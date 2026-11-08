package dev.chill.hillside.block;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.chill.hillside.Hillside;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModFileInfo;

/**
 * The blocks every installed mod declares as natural ground, read from the mods' own block tag files. Tags aren't
 * loaded yet when blocks register, so the json files are read straight from the mod jars. A terrain mod adds its
 * ground blocks to vanilla's carver and ground tags so caves cut through them; that is the signal used here, so any
 * terrain mod is picked up without knowing its block names.
 */
final class TerrainTags {
    // Vanilla tags that only hold natural ground (and ores, which the name filter drops).
    static final List<String> GROUND = List.of("overworld_carver_replaceables", "nether_carver_replaceables",
            "dirt", "sand", "terracotta", "base_stone_overworld", "base_stone_nether", "nylium", "moss_replaceable",
            "azalea_root_replaceable", "dripstone_replaceable_blocks", "sculk_replaceable", "snow_layer_can_survive_on");
    // Plant matter: mushroom caps, wart blocks, leaves and logs. Skipped even when a ground tag lists them (vanilla's
    // nether carver tag includes wart blocks). Not mineable/hoe: moss is dug with a hoe too.
    static final List<String> PLANT = List.of("mineable/axe", "wart_blocks", "leaves", "logs");

    private TerrainTags() {}

    static Set<ResourceLocation> read(List<String> roots) {
        Map<String, Set<String>> tags = new HashMap<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            for (String root : roots) {
                for (String dir : new String[] {"tags/block/", "tags/blocks/"}) {
                    collect(info, "data/minecraft/" + dir, root, tags);
                }
            }
        }
        // Resolve nested tags (#minecraft:dirt inside the carver tag, a mod's own #biomesoplenty:... tags).
        Set<ResourceLocation> blocks = new HashSet<>();
        Deque<String> todo = new ArrayDeque<>();
        Set<String> seen = new HashSet<>();
        for (String root : roots) todo.add("minecraft:" + root);
        while (!todo.isEmpty()) {
            String tag = todo.poll();
            if (!seen.add(tag)) continue;
            Set<String> values = tags.get(tag);
            if (values == null) values = readModTag(tag, tags);
            for (String v : values) {
                if (v.startsWith("#")) todo.add(v.substring(1));
                else {
                    ResourceLocation id = ResourceLocation.tryParse(v);
                    if (id != null) blocks.add(id);
                }
            }
        }
        Hillside.LOG.info("Hillside: {} blocks in tags {}", blocks.size(), roots.get(0) + "...");
        return blocks;
    }

    private static void collect(IModFileInfo info, String dir, String name, Map<String, Set<String>> tags) {
        try {
            Path p = info.getFile().findResource(dir + name + ".json");
            if (p == null || !Files.isRegularFile(p)) return;
            try (Reader r = Files.newBufferedReader(p)) {
                JsonObject json = JsonParser.parseReader(r).getAsJsonObject();
                Set<String> out = tags.computeIfAbsent("minecraft:" + name, k -> new HashSet<>());
                for (JsonElement e : json.getAsJsonArray("values")) {
                    out.add(e.isJsonObject() ? e.getAsJsonObject().get("id").getAsString() : e.getAsString());
                }
            }
        } catch (Exception ex) {
            Hillside.LOG.debug("Hillside: skipped tag {} in {}", name, info.moduleName(), ex);
        }
    }

    /** A non-vanilla tag referenced from a vanilla one: read it from whichever mod has it. */
    private static Set<String> readModTag(String tag, Map<String, Set<String>> tags) {
        ResourceLocation id = ResourceLocation.tryParse(tag);
        if (id == null) return Set.of();
        Set<String> out = new HashSet<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            for (String dir : new String[] {"/tags/block/", "/tags/blocks/"}) {
                Map<String, Set<String>> one = new HashMap<>();
                collect(info, "data/" + id.getNamespace() + dir, id.getPath(), one);
                one.values().forEach(out::addAll);
            }
        }
        tags.put(tag, out);
        return out;
    }
}
