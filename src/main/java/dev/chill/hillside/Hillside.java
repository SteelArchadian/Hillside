package dev.chill.hillside;

import com.mojang.logging.LogUtils;
import dev.chill.hillside.block.SlabRegistry;
import dev.chill.hillside.command.BlocksCommand;
import dev.chill.hillside.debug.CensusCommand;
import dev.chill.hillside.net.HillsideNet;
import dev.chill.hillside.worldgen.SurfaceSlabFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Hillside.MOD_ID)
public final class Hillside {
    public static final String MOD_ID = "hillside";
    public static final Logger LOG = LogUtils.getLogger();

    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MOD_ID);
    static {
        FEATURES.register("surface_slabs", () -> new SurfaceSlabFeature(NoneFeatureConfiguration.CODEC));
    }

    public Hillside(IEventBus modBus) {
        FEATURES.register(modBus);
        // Lowest priority: every other mod's blocks are in the registry by the time we pick slab sources.
        modBus.addListener(EventPriority.LOWEST, SlabRegistry::onRegister);
        modBus.addListener(HillsideNet::register);
        NeoForge.EVENT_BUS.addListener(CensusCommand::register);
        NeoForge.EVENT_BUS.addListener(BlocksCommand::register);
    }
}
