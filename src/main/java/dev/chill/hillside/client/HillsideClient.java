package dev.chill.hillside.client;

import dev.chill.hillside.Hillside;
import dev.chill.hillside.block.SlabRegistry;
import dev.chill.hillside.block.TerrainSlabBlock;
import java.util.Map;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

@EventBusSubscriber(modid = Hillside.MOD_ID, value = Dist.CLIENT)
public final class HillsideClient {
    private HillsideClient() {}

    /** Every slab state and item gets the cutting model; no blockstate or model files are needed per slab. */
    @SubscribeEvent
    static void onModifyBaking(ModelEvent.ModifyBakingResult event) {
        TerrainSlabModel.clearCaches();
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (TerrainSlabBlock slab : SlabRegistry.all()) {
            TerrainSlabModel model = new TerrainSlabModel(slab);
            for (BlockState state : slab.getStateDefinition().getPossibleStates()) {
                models.put(BlockModelShaper.stateToModelLocation(state), model);
            }
            models.put(ModelResourceLocation.inventory(BuiltInRegistries.BLOCK.getKey(slab)), model);
        }
    }

    @SubscribeEvent
    static void onAddPackFinders(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        SlabResourcePack.onAddPackFinders(event);
    }

    /** Tints (grass, foliage, modded colour handlers) come from the source block. */
    @SubscribeEvent
    static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        var colors = event.getBlockColors();
        TerrainSlabBlock[] slabs = SlabRegistry.all().toArray(TerrainSlabBlock[]::new);
        event.register((state, level, pos, tint) -> colors.getColor(
                ((TerrainSlabBlock) state.getBlock()).sourceState(state.getValue(TerrainSlabBlock.SNOWY)), level, pos, tint), slabs);
    }

    @SubscribeEvent
    static void onItemColors(RegisterColorHandlersEvent.Item event) {
        var colors = event.getItemColors();
        TerrainSlabBlock[] slabs = SlabRegistry.all().toArray(TerrainSlabBlock[]::new);
        event.register((stack, tint) -> colors.getColor(new ItemStack(
                ((TerrainSlabBlock) ((net.minecraft.world.item.BlockItem) stack.getItem()).getBlock()).source()), tint), slabs);
    }

}
