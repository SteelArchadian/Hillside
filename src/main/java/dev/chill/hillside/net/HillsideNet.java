package dev.chill.hillside.net;

import dev.chill.hillside.Hillside;
import dev.chill.hillside.block.BlockChoices;
import dev.chill.hillside.block.SlabRegistry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** The block list screen's two messages: the server sends the list, the admin's client sends each switch flip. */
public final class HillsideNet {
    /** Per-block state shown in the list. */
    public static final byte NO_SLAB = 0, ON = 1, OFF = 2, ON_AFTER_RESTART = 3;
    /** Pseudo id for the falling-slabs switch in {@link SetChoice}. */
    public static final String FALLING = "#falling";

    private HillsideNet() {}

    public record Entry(ResourceLocation id, byte state) {}

    public record BlockList(List<Entry> entries, boolean falling) implements CustomPacketPayload {
        public static final Type<BlockList> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hillside.MOD_ID, "block_list"));
        public static final StreamCodec<FriendlyByteBuf, BlockList> CODEC = StreamCodec.of((buf, p) -> {
            buf.writeVarInt(p.entries.size());
            for (Entry e : p.entries) {
                buf.writeResourceLocation(e.id);
                buf.writeByte(e.state);
            }
            buf.writeBoolean(p.falling);
        }, buf -> {
            int n = Math.min(buf.readVarInt(), 65536);
            List<Entry> entries = new ArrayList<>(n);
            for (int i = 0; i < n; i++) entries.add(new Entry(buf.readResourceLocation(), buf.readByte()));
            return new BlockList(entries, buf.readBoolean());
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SetChoice(String id, boolean on) implements CustomPacketPayload {
        public static final Type<SetChoice> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Hillside.MOD_ID, "set_choice"));
        public static final StreamCodec<FriendlyByteBuf, SetChoice> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeUtf(p.id, 256).writeBoolean(p.on),
                buf -> new SetChoice(buf.readUtf(256), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(BlockList.TYPE, BlockList.CODEC, (p, ctx) -> dev.chill.hillside.client.BlockListScreen.show(p));
        r.playToServer(SetChoice.TYPE, SetChoice.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) apply(player, p);
        });
    }

    public static byte state(ResourceLocation id, Block block) {
        if (SlabRegistry.slabFor(block) != null) return BlockChoices.isOff(id) ? OFF : ON;
        return BlockChoices.isExtra(id) ? ON_AFTER_RESTART : NO_SLAB;
    }

    public static BlockList list() {
        List<Entry> entries = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!SlabRegistry.canHaveSlab(block) && SlabRegistry.slabFor(block) == null) continue;
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            entries.add(new Entry(id, state(id, block)));
        }
        return new BlockList(entries, BlockChoices.fallingSlabs());
    }

    public static void sendList(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, list());
    }

    /** One switch flip from the screen (or the text command). Admins only; returns the feedback line. */
    public static Component change(String key, boolean on) {
        if (FALLING.equals(key)) {
            BlockChoices.setFallingSlabs(on);
            return Component.translatable(on ? "hillside.blocks.falling_on" : "hillside.blocks.falling_off");
        }
        ResourceLocation id = ResourceLocation.tryParse(key);
        Block block = id == null ? null : BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        if (block == null || (!SlabRegistry.canHaveSlab(block) && SlabRegistry.slabFor(block) == null)) {
            return Component.translatable("hillside.blocks.unknown", key);
        }
        boolean restart = BlockChoices.set(id, on, SlabRegistry.detected(id));
        String what = on ? "hillside.blocks.on" : "hillside.blocks.off";
        return Component.translatable(restart ? what + "_restart" : what, block.getName());
    }

    private static void apply(ServerPlayer player, SetChoice p) {
        if (!player.hasPermissions(2)) return;
        player.sendSystemMessage(change(p.id, p.on));
        sendList(player);
    }
}
