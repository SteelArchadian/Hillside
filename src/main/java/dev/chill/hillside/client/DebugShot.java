package dev.chill.hillside.client;

import dev.chill.hillside.Hillside;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * TEST ONLY. With -Dhillside.debugshot=<name>, takes one HUD-less screenshot hillside-<name>.png 12 s after the
 * player is in a world (the test harness holds the camera angle). Does nothing without the property.
 */
@EventBusSubscriber(modid = Hillside.MOD_ID, value = Dist.CLIENT)
public final class DebugShot {
    private static final String NAME = System.getProperty("hillside.debugshot");
    private static final int AT_TICK = 240;
    private static int ticks;

    private DebugShot() {}

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (NAME == null || mc.player == null || ticks > AT_TICK) return;
        if (++ticks == AT_TICK - 40) mc.options.hideGui = true;
        if (ticks == AT_TICK) {
            String file = "hillside-" + NAME + ".png";
            Screenshot.grab(mc.gameDirectory, file, mc.getMainRenderTarget(), msg -> Hillside.LOG.info("[Hillside] shot {}", file));
        }
    }
}
