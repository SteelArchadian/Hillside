package dev.chill.hillside.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A slab that looks like (and drops like) a natural terrain block. SNOWY draws a thin snow cap inside the slab's
 * own cell instead of keeping a snow layer in the cell above, so nothing is rendered offset from its block.
 */
public class TerrainSlabBlock extends SlabBlock {
    public static final BooleanProperty SNOWY = BlockStateProperties.SNOWY;

    private final Block source;

    public TerrainSlabBlock(Block source, Properties properties) {
        super(properties);
        this.source = source;
        registerDefaultState(defaultBlockState().setValue(SNOWY, false));
    }

    public Block source() {
        return source;
    }

    /** The source block's state to draw this slab with (snowy grass sides when the slab is snowy). */
    public BlockState sourceState(boolean snowy) {
        BlockState s = source.defaultBlockState();
        return snowy && s.hasProperty(SNOWY) ? s.setValue(SNOWY, true) : s;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SNOWY);
    }

    /** Slabs of sand, gravel and other falling blocks fall like their block (admin setting, on by default). */
    private boolean falls() {
        return source instanceof FallingBlock && BlockChoices.fallingSlabs();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        super.onPlace(state, level, pos, old, moved);
        if (falls()) level.scheduleTick(pos, this, 2);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (falls()) level.scheduleTick(pos, this, 2);
        return super.updateShape(state, dir, neighbor, level, pos, neighborPos);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        // A top slab hangs from its own cell's ceiling half, so like a bottom slab it falls when nothing is under it.
        if (falls() && FallingBlock.isFree(level.getBlockState(pos.below())) && pos.getY() >= level.getMinBuildHeight()) {
            SlabType type = state.getValue(TYPE) == SlabType.DOUBLE ? SlabType.DOUBLE : SlabType.BOTTOM;
            FallingBlockEntity.fall(level, pos, state.setValue(TYPE, type).setValue(WATERLOGGED, false));
        }
    }

    @Override
    public MutableComponent getName() {
        return Component.translatable("block.hillside.terrain_slab", source.getName());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockState s = super.getStateForPlacement(ctx);
        return s == null ? null : s.setValue(SNOWY, false);
    }
}
