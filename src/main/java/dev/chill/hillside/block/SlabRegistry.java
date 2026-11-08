package dev.chill.hillside.block;

import dev.chill.hillside.Hillside;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ColoredFallingBlock;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.MudBlock;
import net.minecraft.world.level.block.NyliumBlock;
import net.minecraft.world.level.block.RootedDirtBlock;
import net.minecraft.world.level.block.SnowyDirtBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Picks every natural ground block in the game (vanilla and modded) and registers one terrain slab for it.
 * Tags are not loaded yet when blocks register, so the choice uses the block's class, shape and name.
 * Slab ids are {@code hillside:<source namespace>/<source path>}.
 */
public final class SlabRegistry {
    // Words that mark natural ground in vanilla and in most terrain mods.
    private static final Pattern TERRAIN = Pattern.compile(
            "(^|_)(grass_block|dirt|soil|loam|silt|peat|podzol|mycelium|nylium|mud|clay|moss_block|sand|gravel|"
            + "stone|deepslate|tuff|calcite|granite|diorite|andesite|limestone|marble|chalk|slate|shale|basalt|"
            + "blackstone|netherrack|end_stone|terracotta|sandstone|snow_block|permafrost|regolith|rock|dripstone_block|"
            + "ash_block|soul_soil)($|_)");
    // Words that mark building, ore or decorative blocks, which never get slabs.
    private static final Pattern NOT_TERRAIN = Pattern.compile(
            "brick|polished|smooth|cut_|chiseled|tile|pillar|plank|log|wood|stem|hyphae|ore|concrete|glass|wool|"
            + "glazed|lamp|slab|stair|wall|cobble|button|pressure|raw_|crystal|lantern|carved|engraved|paving|"
            + "pavement|reinforced|infested|compressed|storage|crate|redstone|glowstone|lodestone|amethyst|"
            + "gilded|_block_of|^block_of|furnace|cutter|table|fence|door|trapdoor|path|casing|sturdy|layered|"
            + "quartz|purpur|prismarine|hay|kelp|thatch|straw|bale|target|sponge|sack|bag|carpet|alloy|flax|fodder|feed|rigid|gravisand");
    // Sounds of loose natural ground; a full cube that sounds like this is terrain whatever its name.
    private static final Set<SoundType> SOFT_GROUND = Set.of(SoundType.SAND, SoundType.GRAVEL, SoundType.GRASS,
            SoundType.ROOTED_DIRT, SoundType.MUD, SoundType.SOUL_SAND, SoundType.SOUL_SOIL, SoundType.NYLIUM,
            SoundType.NETHERRACK, SoundType.MOSS, SoundType.SNOW, SoundType.SCULK);
    // Sounds of natural rock, which building blocks share; these need the stone-family check too.
    private static final Set<SoundType> STONY = Set.of(SoundType.STONE, SoundType.DEEPSLATE, SoundType.TUFF,
            SoundType.BASALT, SoundType.CALCITE, SoundType.DRIPSTONE_BLOCK);

    private static final Map<Block, TerrainSlabBlock> BY_SOURCE = new IdentityHashMap<>();
    private static final Map<ResourceLocation, Block> PENDING = new LinkedHashMap<>();
    // Sources that got a slab by detection (not by the admin's extra list).
    private static final Set<ResourceLocation> DETECTED = new HashSet<>();

    private SlabRegistry() {}

    /** The terrain slab made from {@code source}, or null if it has none. */
    public static TerrainSlabBlock slabFor(Block source) {
        return BY_SOURCE.get(source);
    }

    /** The slab worldgen should place on {@code source}: none when the admin turned that block off. */
    public static TerrainSlabBlock generatedFor(Block source) {
        TerrainSlabBlock slab = BY_SOURCE.get(source);
        return slab == null || BlockChoices.isOff(BuiltInRegistries.BLOCK.getKey(source)) ? null : slab;
    }

    public static boolean detected(ResourceLocation source) {
        return DETECTED.contains(source);
    }

    /**
     * Blocks an admin may give a slab: full solid cubes without block entities, from any mod. The block list
     * screen shows these; detected ones first.
     */
    public static boolean canHaveSlab(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if (id.getNamespace().equals(Hillside.MOD_ID) || block instanceof EntityBlock) return false;
        try {
            BlockState s = block.defaultBlockState();
            return s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    && !s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    public static List<TerrainSlabBlock> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_SOURCE.values()));
    }

    public static void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.BLOCK)) {
            BlockChoices.load();
            Set<ResourceLocation> ids = new HashSet<>(BuiltInRegistries.BLOCK.keySet());
            Set<ResourceLocation> tagged = TerrainTags.read(TerrainTags.GROUND);
            Set<ResourceLocation> plants = TerrainTags.read(TerrainTags.PLANT);
            for (Map.Entry<net.minecraft.resources.ResourceKey<Block>, Block> e : List.copyOf(BuiltInRegistries.BLOCK.entrySet())) {
                ResourceLocation id = e.getKey().location();
                if (id.getNamespace().equals(Hillside.MOD_ID)) continue;
                boolean detected = isTerrain(id, e.getValue(), ids, tagged, plants);
                if (detected) DETECTED.add(id);
                if (detected || (BlockChoices.extraAtStart(id) && canHaveSlab(e.getValue()))) {
                    PENDING.put(ResourceLocation.fromNamespaceAndPath(Hillside.MOD_ID, id.getNamespace() + "/" + id.getPath()), e.getValue());
                }
            }
            PENDING.forEach((slabId, source) -> event.register(Registries.BLOCK, slabId, () -> {
                TerrainSlabBlock slab = new TerrainSlabBlock(source, propertiesFrom(source));
                BY_SOURCE.put(source, slab);
                return slab;
            }));
            Hillside.LOG.info("Hillside: {} terrain slabs", PENDING.size());
            Hillside.LOG.info("Hillside: modded sources {}", PENDING.keySet().stream().map(ResourceLocation::getPath)
                    .filter(p -> !p.startsWith("minecraft/")).toList());
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            PENDING.forEach((slabId, source) -> event.register(Registries.ITEM, slabId, () -> {
                TerrainSlabBlock slab = BY_SOURCE.get(source);
                return new BlockItem(slab, new Item.Properties()) {
                    @Override
                    public Component getName(ItemStack stack) {
                        return slab.getName();
                    }
                };
            }));
        }
    }

    /**
     * The source's settings, with every per-state function replaced by its answer for the source's default state.
     * A copied function may read the source's own block state properties (Biomes O' Plenty's light level reads
     * "distance"), which the slab doesn't have.
     */
    private static BlockBehaviour.Properties propertiesFrom(Block source) {
        BlockState s = source.defaultBlockState();
        int light = s.getLightEmission();
        MapColor color = s.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        return BlockBehaviour.Properties.ofFullCopy(source).dropsLike(source)
                .lightLevel(state -> light)
                .mapColor(color)
                .offsetType(BlockBehaviour.OffsetType.NONE)
                .isRedstoneConductor((state, level, pos) -> s.isRedstoneConductor(level, pos))
                .isSuffocating((state, level, pos) -> s.isSuffocating(level, pos))
                .isViewBlocking((state, level, pos) -> s.isViewBlocking(level, pos))
                .hasPostProcess((state, level, pos) -> s.hasPostProcess(level, pos))
                .emissiveRendering((state, level, pos) -> s.emissiveRendering(level, pos));
    }

    /**
     * Natural ground, found four ways: a vanilla terrain class, a terrain word in the name, a mod listing it in
     * vanilla's natural-ground tags ({@link TerrainTags}), or (for modded blocks) the block's sound. Soft ground sounds (sand, gravel, grass, mud, soul sand...) count on their own; stone-like
     * sounds count only for the base block of a stone family, i.e. one whose mod also has a polished, cut, brick,
     * tile, chiseled or cobbled variant of it (Quark's jasper, Create's ochrum).
     */
    static boolean isTerrain(ResourceLocation id, Block block, Set<ResourceLocation> ids, Set<ResourceLocation> tagged,
                             Set<ResourceLocation> plants) {
        if (block instanceof EntityBlock || block instanceof ConcretePowderBlock) return false;
        String path = id.getPath();
        if (NOT_TERRAIN.matcher(path).find()) return false;
        try {
            BlockState s = block.defaultBlockState();
            // A full, solid-looking cube; the collision may be a little lower (soul sand, mud).
            if (!s.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                    || s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) return false;
            if (block instanceof SnowyDirtBlock || block instanceof NyliumBlock || block instanceof RootedDirtBlock
                    || block instanceof MudBlock || block instanceof ColoredFallingBlock || TERRAIN.matcher(path).find()) {
                return true;
            }
            // Vanilla's nether carver tag holds wart blocks, so plant matter is ruled out before the tag check.
            if (plants.contains(id)) return false;
            if (tagged.contains(id)) return true;
            SoundType sound = s.getSoundType();
            if (SOFT_GROUND.contains(sound)) return true;
            return STONY.contains(sound) && !path.endsWith("_block") && hasStoneFamily(id, ids);
        } catch (RuntimeException ex) {
            return false; // a block that can't answer outside a world is not plain terrain
        }
    }

    private static boolean hasStoneFamily(ResourceLocation id, Set<ResourceLocation> ids) {
        String p = id.getPath();
        for (String variant : new String[] {"polished_" + p, "cut_" + p, "chiseled_" + p, "cobbled_" + p,
                p + "_bricks", p + "_brick", p + "_tiles"}) {
            if (ids.contains(id.withPath(variant))) return true;
        }
        return false;
    }
}
