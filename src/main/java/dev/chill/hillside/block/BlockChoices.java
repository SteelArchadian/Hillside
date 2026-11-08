package dev.chill.hillside.block;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chill.hillside.Hillside;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

/**
 * The admin's say over which blocks get slabs, kept in {@code config/hillside/blocks.json}:
 * <ul>
 * <li>{@code off}: detected blocks whose slabs should not generate. Takes effect at once; the slab block stays
 * registered, so turning it back on needs no restart and old worlds keep loading.</li>
 * <li>{@code extra}: blocks the detection missed that should get a slab anyway. Slabs are registered at startup, so
 * these take effect after a restart (on servers, players need the same file).</li>
 * <li>{@code fallingSlabs}: slabs of sand, gravel and other falling blocks fall like their block.</li>
 * </ul>
 * Read once while blocks register (before configs load), so this is plain json rather than a config spec.
 */
public final class BlockChoices {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<ResourceLocation> OFF = ConcurrentHashMap.newKeySet();
    private static final Set<ResourceLocation> EXTRA = ConcurrentHashMap.newKeySet();
    // What EXTRA held at startup: the slabs that actually exist this session.
    private static final Set<ResourceLocation> EXTRA_AT_START = ConcurrentHashMap.newKeySet();
    private static volatile boolean fallingSlabs = true;
    private static boolean loaded;

    private BlockChoices() {}

    static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("hillside").resolve("blocks.json");
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.isRegularFile(f)) {
            save();
            return;
        }
        try (Reader r = Files.newBufferedReader(f)) {
            JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
            read(json, "off", OFF);
            read(json, "extra", EXTRA);
            if (json.has("fallingSlabs")) fallingSlabs = json.get("fallingSlabs").getAsBoolean();
        } catch (Exception ex) {
            // A hand-edit gone wrong must not stop the game: keep the defaults and leave the file for the admin.
            Hillside.LOG.warn("Hillside: could not read {}, using defaults", f, ex);
        }
        EXTRA_AT_START.addAll(EXTRA);
    }

    private static void read(JsonObject json, String key, Set<ResourceLocation> into) {
        if (!json.has(key) || !json.get(key).isJsonArray()) return;
        json.getAsJsonArray(key).forEach(e -> {
            ResourceLocation id = e.isJsonPrimitive() ? ResourceLocation.tryParse(e.getAsString()) : null;
            if (id != null) into.add(id);
            else Hillside.LOG.warn("Hillside: ignoring bad block id {} in {}", e, key);
        });
    }

    public static synchronized void save() {
        JsonObject json = new JsonObject();
        json.addProperty("_help", "off: detected blocks that should not get slabs (live). extra: undetected blocks "
                + "that should (after a restart; players need the same file). Or use /hillside blocks in game.");
        json.add("off", array(OFF));
        json.add("extra", array(EXTRA));
        json.addProperty("fallingSlabs", fallingSlabs);
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling("blocks.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                GSON.toJson(json, w);
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception ex) {
            Hillside.LOG.warn("Hillside: could not save {}", f, ex);
        }
    }

    private static JsonArray array(Set<ResourceLocation> ids) {
        JsonArray a = new JsonArray();
        new TreeSet<>(ids).forEach(id -> a.add(id.toString()));
        return a;
    }

    public static boolean isOff(ResourceLocation source) {
        return OFF.contains(source);
    }

    public static boolean isExtra(ResourceLocation source) {
        return EXTRA.contains(source);
    }

    static boolean extraAtStart(ResourceLocation source) {
        return EXTRA_AT_START.contains(source);
    }

    public static boolean fallingSlabs() {
        return fallingSlabs;
    }

    /**
     * Turns slabs for {@code source} on or off. {@code detected}: the block got a slab by detection.
     * Returns true when the change only takes effect after a restart.
     */
    public static synchronized boolean set(ResourceLocation source, boolean on, boolean detected) {
        if (on) {
            OFF.remove(source);
            if (!detected) EXTRA.add(source);
        } else {
            EXTRA.remove(source);
            if (detected || EXTRA_AT_START.contains(source)) OFF.add(source);
        }
        save();
        return !detected && on != EXTRA_AT_START.contains(source);
    }

    public static synchronized void setFallingSlabs(boolean on) {
        fallingSlabs = on;
        save();
    }
}
