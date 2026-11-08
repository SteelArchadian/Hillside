package dev.chill.hillside.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import dev.chill.hillside.net.HillsideNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /hillside blocks} (op): opens the block list screen. Text forms for consoles and scripts:
 * {@code /hillside blocks <block> <on|off>} and {@code /hillside blocks falling <on|off>}.
 */
public final class BlocksCommand {
    private BlocksCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hillside").requires(s -> s.hasPermission(2))
                .then(Commands.literal("blocks")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            HillsideNet.sendList(player);
                            return 1;
                        })
                        .then(Commands.literal("falling")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> say(ctx.getSource(), HillsideNet.change(HillsideNet.FALLING,
                                                BoolArgumentType.getBool(ctx, "on"))))))
                        .then(Commands.argument("block", ResourceLocationArgument.id())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggestResource(
                                        BuiltInRegistries.BLOCK.keySet(), b))
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(ctx -> say(ctx.getSource(), HillsideNet.change(
                                                ResourceLocationArgument.getId(ctx, "block").toString(),
                                                BoolArgumentType.getBool(ctx, "on"))))))));
    }

    private static int say(CommandSourceStack src, Component msg) {
        src.sendSuccess(() -> msg, true);
        return 1;
    }
}
