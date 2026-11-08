package dev.chill.hillside.mixin;

import dev.chill.hillside.block.SlabRegistry;
import dev.chill.hillside.block.TerrainSlabBlock;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import net.irisshaders.iris.shaderpack.materialmap.BlockRenderType;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Shader packs give blocks their material (snow, sand, foliage...) through block.properties, which names only the
 * packs' known blocks. Each terrain slab gets the ID and render layer its source block has, so any pack treats a
 * snow slab as snow.
 */
@Mixin(value = WorldRenderingSettings.class, remap = false)
abstract class IrisBlockIdsMixin {
    @ModifyVariable(method = "setBlockStateIds", at = @At("HEAD"), argsOnly = true)
    private Object2IntMap<BlockState> hillside$addSlabIds(Object2IntMap<BlockState> ids) {
        if (ids == null) return null;
        Object2IntMap<BlockState> out = new Object2IntOpenHashMap<>(ids);
        out.defaultReturnValue(ids.defaultReturnValue());
        for (TerrainSlabBlock slab : SlabRegistry.all()) {
            for (BlockState state : slab.getStateDefinition().getPossibleStates()) {
                BlockState source = slab.sourceState(state.getValue(TerrainSlabBlock.SNOWY));
                if (ids.containsKey(source)) out.put(state, ids.getInt(source));
            }
        }
        return out;
    }

    @ModifyVariable(method = "setBlockTypeIds", at = @At("HEAD"), argsOnly = true)
    private Map<Block, BlockRenderType> hillside$addSlabTypes(Map<Block, BlockRenderType> types) {
        if (types == null) return null;
        Map<Block, BlockRenderType> out = new HashMap<>(types);
        for (TerrainSlabBlock slab : SlabRegistry.all()) {
            BlockRenderType type = types.get(slab.source());
            if (type != null) out.put(slab, type);
        }
        return out;
    }
}
