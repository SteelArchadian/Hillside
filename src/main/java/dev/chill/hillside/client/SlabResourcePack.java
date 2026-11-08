package dev.chill.hillside.client;

import dev.chill.hillside.Hillside;
import dev.chill.hillside.block.SlabRegistry;
import dev.chill.hillside.block.TerrainSlabBlock;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.Nullable;

/**
 * A built-in client pack holding one placeholder blockstate and item model per terrain slab, so the model loader
 * finds a definition for every slab. The real models replace the placeholders in ModifyBakingResult.
 */
final class SlabResourcePack implements PackResources {
    private static final byte[] BLOCKSTATE = "{\"variants\":{\"\":{\"model\":\"minecraft:block/stone\"}}}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ITEM = "{\"parent\":\"minecraft:block/stone\"}".getBytes(StandardCharsets.UTF_8);

    private final PackLocationInfo location;
    private Map<ResourceLocation, byte[]> files;

    private SlabResourcePack(PackLocationInfo location) {
        this.location = location;
    }

    static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) return;
        PackLocationInfo info = new PackLocationInfo(Hillside.MOD_ID + "_slabs", Component.literal("Hillside slabs"),
                PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new SlabResourcePack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new SlabResourcePack(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, false));
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    private Map<ResourceLocation, byte[]> files() {
        if (files == null) {
            Map<ResourceLocation, byte[]> map = new HashMap<>();
            for (TerrainSlabBlock slab : SlabRegistry.all()) {
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(slab);
                map.put(id.withPath(p -> "blockstates/" + p + ".json"), BLOCKSTATE);
                map.put(id.withPath(p -> "models/item/" + p + ".json"), ITEM);
            }
            files = map;
        }
        return files;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return null;
    }

    @Override
    public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        byte[] data = type == PackType.CLIENT_RESOURCES ? files().get(id) : null;
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.CLIENT_RESOURCES || !namespace.equals(Hillside.MOD_ID)) return;
        files().forEach((id, data) -> {
            if (id.getPath().startsWith(path + "/")) output.accept(id, () -> new ByteArrayInputStream(data));
        });
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? Set.of(Hillside.MOD_ID) : Set.of();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        if (serializer == PackMetadataSection.TYPE) {
            return (T) new PackMetadataSection(Component.literal("Hillside slab models"),
                    net.minecraft.SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES), Optional.empty());
        }
        return null;
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {}
}
