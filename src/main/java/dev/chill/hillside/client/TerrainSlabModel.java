package dev.chill.hillside.client;

import dev.chill.hillside.block.TerrainSlabBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.RenderTypeHelper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a terrain slab from whatever model its source block has right now (resource packs such as Better Grass,
 * modded textures, tints), cut to the slab's half. The source model is looked up at draw time, so wrappers other
 * mods add during baking are included. A bottom slab shows the upper half of the source's side texture, so a grass
 * slab keeps its grass edge. A snowy bottom slab gets a 2-pixel snow cap inside its own cell.
 */
final class TerrainSlabModel implements BakedModel {
    static final QuadSlicer BOTTOM = new QuadSlicer(-0.5F, 0F, 0.5F);
    static final QuadSlicer TOP = new QuadSlicer(0F, 0.5F, 1F);
    // A top slab's underside: the source's bottom face moved up to the slab's middle.
    static final QuadSlicer TOP_INNER = new QuadSlicer(0.5F, 0.5F, 1F);
    static final QuadSlicer CAP = new QuadSlicer(-0.375F, 0.5F, 0.625F);
    private static final ChunkRenderTypeSet SOLID = ChunkRenderTypeSet.of(RenderType.solid());

    private final TerrainSlabBlock block;

    TerrainSlabModel(TerrainSlabBlock block) {
        this.block = block;
    }

    static void clearCaches() {
        BOTTOM.clear();
        TOP.clear();
        TOP_INNER.clear();
        CAP.clear();
    }

    private static BakedModel modelOf(BlockState state) {
        return Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        SlabType type = state == null ? SlabType.BOTTOM : state.getValue(SlabBlock.TYPE);
        boolean snowy = state != null && state.getValue(TerrainSlabBlock.SNOWY);
        BlockState src = block.sourceState(snowy && type == SlabType.BOTTOM);
        BakedModel model = modelOf(src);
        if (type == SlabType.DOUBLE) return model.getQuads(src, side, rand, ModelData.EMPTY, renderType);

        // The inner face (top of a bottom slab, bottom of a top slab) no longer touches the cell edge: never cull it.
        Direction inner = type == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
        QuadSlicer slicer = type == SlabType.BOTTOM ? BOTTOM : TOP;
        boolean cap = snowy && type == SlabType.BOTTOM;
        boolean capPass = cap && (renderType == null || renderType == RenderType.solid());
        if (side == inner) return List.of();

        List<BakedQuad> out = new ArrayList<>();
        slicer.sliceInto(model.getQuads(src, side, rand, ModelData.EMPTY, renderType), out);
        if (side == null) {
            if (!cap) (type == SlabType.BOTTOM ? BOTTOM : TOP_INNER).sliceInto(model.getQuads(src, inner, rand, ModelData.EMPTY, renderType), out);
            if (capPass) {
                CAP.sliceInto(snowQuads(Direction.UP, rand), out);
                // The cap's edges sit above the slab's half-height side, so a neighbour that hides the slab's side
                // (another bottom slab) doesn't hide them. Culled with the side, they left a see-through strip.
                for (Direction d : Direction.Plane.HORIZONTAL) CAP.sliceInto(snowQuads(d, rand), out);
            }
        }
        return out;
    }

    private static List<BakedQuad> snowQuads(Direction side, RandomSource rand) {
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        return modelOf(snow).getQuads(snow, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        boolean snowy = state.getValue(TerrainSlabBlock.SNOWY) && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
        BlockState src = block.sourceState(snowy);
        ChunkRenderTypeSet types = modelOf(src).getRenderTypes(src, rand, ModelData.EMPTY);
        return snowy ? ChunkRenderTypeSet.union(types, SOLID) : types;
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        BlockState src = block.sourceState(false);
        for (RenderType t : modelOf(src).getRenderTypes(src, RandomSource.create(42L), ModelData.EMPTY)) {
            return List.of(RenderTypeHelper.getEntityRenderType(t, true));
        }
        return List.of(RenderType.solid());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return modelOf(block.sourceState(false)).useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return modelOf(block.sourceState(false)).getParticleIcon();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return getParticleIcon();
    }

    @Override
    public ItemTransforms getTransforms() {
        return modelOf(block.sourceState(false)).getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
