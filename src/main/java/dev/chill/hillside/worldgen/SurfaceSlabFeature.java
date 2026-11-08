package dev.chill.hillside.worldgen;

import com.mojang.serialization.Codec;
import dev.chill.hillside.block.SlabRegistry;
import dev.chill.hillside.block.TerrainSlabBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Softens one-block terrain steps everywhere in the column, on the surface and in caves. An open cell gets
 * <ul>
 * <li>a bottom slab of the floor's material when the floor below is natural terrain, the cell above is open, and a
 * side neighbour rises exactly one block of natural terrain (a step up);</li>
 * <li>otherwise a top slab of the ceiling's material when the ceiling above is natural terrain, the cell below is
 * open, and a side neighbour hangs exactly one block lower (a step down in the ceiling).</li>
 * </ul>
 * Cells with ground both above and below are left alone, so one-block-high passages stay walkable. Runs in the
 * top-layer step after vanilla's snow pass, so a surface snow layer turns into a snowy slab.
 */
public class SurfaceSlabFeature extends Feature<NoneFeatureConfiguration> {
    public SurfaceSlabFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx) {
        WorldGenLevel level = ctx.level();
        int x0 = ctx.origin().getX() & ~15, z0 = ctx.origin().getZ() & ~15;
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cell = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        boolean placed = false;
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
                // Rolling window down the column: each block is read once. The heightmap is the first air
                // above the ground, which is the first cell that can take a slab.
                BlockState above = level.getBlockState(cell.set(x, top + 1, z));
                BlockState here = level.getBlockState(cell.set(x, top, z));
                for (int y = top; y > minY; y--) {
                    BlockState below = level.getBlockState(cell.set(x, y - 1, z));
                    if (isClearable(here)) {
                        cell.set(x, y, z);
                        BlockState slab = slabFor(level, cell, probe, here, above, below);
                        if (slab != null) {
                            if (here.getBlock() instanceof DoublePlantBlock && above.is(here.getBlock())) {
                                level.setBlock(probe.set(x, y + 1, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                            }
                            level.setBlock(cell, slab, Block.UPDATE_CLIENTS);
                            here = slab;
                            placed = true;
                        }
                    }
                    above = here;
                    here = below;
                }
            }
        }
        return placed;
    }

    private static BlockState slabFor(WorldGenLevel level, BlockPos cell, BlockPos.MutableBlockPos probe,
                                      BlockState here, BlockState above, BlockState below) {
        TerrainSlabBlock floor = SlabRegistry.generatedFor(below.getBlock());
        if (floor != null && isOpen(above) && hasStep(level, cell, probe, Direction.UP)) {
            return floor.defaultBlockState()
                    .setValue(SlabBlock.TYPE, SlabType.BOTTOM)
                    .setValue(TerrainSlabBlock.SNOWY, here.getBlock() instanceof SnowLayerBlock);
        }
        TerrainSlabBlock ceiling = SlabRegistry.generatedFor(above.getBlock());
        if (ceiling != null && isOpen(below) && hasStep(level, cell, probe, Direction.DOWN)) {
            return ceiling.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
        }
        return null;
    }

    /** Air, or a plant / snow layer that worldgen may replace. */
    private static boolean isClearable(BlockState s) {
        return s.isAir() || (!s.blocksMotion() && s.getFluidState().isEmpty() && !s.hasBlockEntity()
                && !s.is(BlockTags.LEAVES));
    }

    private static boolean isOpen(BlockState s) {
        return !s.blocksMotion() && s.getFluidState().isEmpty();
    }

    /**
     * True when a side neighbour of {@code cell} is natural terrain that is exactly one block thick toward
     * {@code away}: for a floor step, open above it; for a ceiling step, open below it.
     */
    private static boolean hasStep(WorldGenLevel level, BlockPos cell, BlockPos.MutableBlockPos probe, Direction away) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            probe.setWithOffset(cell, d);
            if (SlabRegistry.slabFor(level.getBlockState(probe).getBlock()) == null) continue;
            probe.move(away);
            if (isOpen(level.getBlockState(probe))) return true;
        }
        return false;
    }
}
