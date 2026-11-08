package dev.chill.hillside.debug;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.chill.hillside.Hillside;
import dev.chill.hillside.block.SlabRegistry;
import dev.chill.hillside.block.TerrainSlabBlock;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /hillside census [radius]} (op): walks the loaded chunks around the player and counts one-block terrain
 * steps, per biome: how many got a slab, and which floor blocks had a step but no slab of their own. The second
 * list is how a terrain mod's missing ground blocks show up. Full results go to the log.
 */
public final class CensusCommand {
    private CensusCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hillside").requires(s -> s.hasPermission(2))
                .then(Commands.literal("census")
                        .executes(ctx -> run(ctx, 8))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(0, 32))
                                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static final class Tally {
        int steps, slabbed;
        final Map<String, Integer> missing = new HashMap<>();
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int radius) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        long start = System.nanoTime();
        BlockPos at = BlockPos.containing(src.getPosition());
        Map<String, Tally> byBiome = new TreeMap<>();
        Map<String, Integer> missing = new HashMap<>();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        int chunks = 0, minY = level.getMinBuildHeight();
        for (int cx = (at.getX() >> 4) - radius; cx <= (at.getX() >> 4) + radius; cx++) {
            for (int cz = (at.getZ() >> 4) - radius; cz <= (at.getZ() >> 4) + radius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                chunks++;
                for (int x = cx << 4; x < (cx << 4) + 16; x++) {
                    for (int z = cz << 4; z < (cz << 4) + 16; z++) {
                        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
                        for (int y = top; y > minY + 1; y--) {
                            BlockState here = chunk.getBlockState(p.set(x, y, z));
                            boolean slab = here.getBlock() instanceof TerrainSlabBlock;
                            if (!slab && !open(here)) continue;
                            BlockState below = chunk.getBlockState(p.set(x, y - 1, z));
                            if (!ground(below) || !open(chunk.getBlockState(p.set(x, y + 1, z)))) continue;
                            if (!slab && !step(level, p.set(x, y, z), q)) continue;
                            String biome = level.getBiome(p.set(x, y, z)).unwrapKey()
                                    .map(k -> k.location().toString()).orElse("?");
                            Tally t = byBiome.computeIfAbsent(biome, b -> new Tally());
                            t.steps++;
                            if (slab) {
                                t.slabbed++;
                            } else if (SlabRegistry.slabFor(below.getBlock()) == null) {
                                String id = BuiltInRegistries.BLOCK.getKey(below.getBlock()).toString();
                                t.missing.merge(id, 1, Integer::sum);
                                missing.merge(id, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        Hillside.LOG.info("[Hillside census] {} chunks around {} in {} ms", chunks, at.toShortString(),
                (System.nanoTime() - start) / 1_000_000);
        byBiome.forEach((b, t) -> Hillside.LOG.info("[Hillside census] {}: {} steps, {} slabbed, no slab for {}",
                b, t.steps, t.slabbed, top(t.missing, 8)));
        Hillside.LOG.info("[Hillside census] no slab overall: {}", top(missing, 30));
        int steps = byBiome.values().stream().mapToInt(t -> t.steps).sum();
        int slabbed = byBiome.values().stream().mapToInt(t -> t.slabbed).sum();
        String line = "Hillside census: " + chunks + " chunks, " + steps + " steps, " + slabbed + " slabbed; no slab for "
                + top(missing, 5);
        src.sendSuccess(() -> Component.literal(line), false);
        return slabbed;
    }

    private static String top(Map<String, Integer> m, int n) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(n).map(e -> e.getKey() + " x" + e.getValue()).toList().toString();
    }

    private static boolean open(BlockState s) {
        return !s.blocksMotion() && s.getFluidState().isEmpty();
    }

    /** A solid full cube: what a step's floor and riser are made of. */
    private static boolean ground(BlockState s) {
        return s.getBlock() instanceof TerrainSlabBlock ? false
                : s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** A side neighbour is ground with open space above it: the cell sits at the foot of a one-block rise. */
    private static boolean step(ServerLevel level, BlockPos cell, BlockPos.MutableBlockPos q) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (ground(level.getBlockState(q.setWithOffset(cell, d))) && open(level.getBlockState(q.move(Direction.UP)))) {
                return true;
            }
        }
        return false;
    }
}
