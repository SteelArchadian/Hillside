package dev.chill.hillside.client;

import dev.chill.hillside.net.HillsideNet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * {@code /hillside blocks}: every block that could have a slab, searchable, each with an on/off switch. Blocks
 * Hillside picked by itself are listed first. Turning a detected block off is live; giving an undetected block a
 * slab takes a restart, since slabs are registered at startup.
 */
public final class BlockListScreen extends Screen {
    private List<HillsideNet.Entry> entries;
    private boolean falling;
    private EditBox search;
    private Rows rows;
    private Button fallingButton;
    private double scroll;

    private BlockListScreen(HillsideNet.BlockList list) {
        super(Component.translatable("hillside.blocks.title"));
        take(list);
    }

    /** From the network handler: open the screen, or refresh it when it is already open. */
    public static void show(HillsideNet.BlockList list) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof BlockListScreen open) {
            open.take(list);
            open.refill();
        } else {
            mc.setScreen(new BlockListScreen(list));
        }
    }

    private void take(HillsideNet.BlockList list) {
        entries = new ArrayList<>(list.entries());
        entries.sort(Comparator.comparing((HillsideNet.Entry e) -> e.state() == HillsideNet.NO_SLAB)
                .thenComparing(e -> e.id().toString()));
        falling = list.falling();
        if (fallingButton != null) fallingButton.setMessage(fallingLabel());
    }

    @Override
    protected void init() {
        search = new EditBox(font, width / 2 - 150, 22, 196, 18, Component.translatable("hillside.blocks.search"));
        search.setHint(Component.translatable("hillside.blocks.search"));
        search.setResponder(s -> refill());
        addRenderableWidget(search);
        fallingButton = addRenderableWidget(Button.builder(fallingLabel(), b -> send(HillsideNet.FALLING, !falling))
                .bounds(width / 2 + 50, 21, 100, 20)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("hillside.blocks.falling_tip")))
                .build());
        rows = new Rows(minecraft, width, height - 76, 46, 22);
        addRenderableWidget(rows);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, height - 26, 200, 20).build());
        refill();
        setInitialFocus(search);
    }

    private Component fallingLabel() {
        return Component.translatable(falling ? "hillside.blocks.falling_on_short" : "hillside.blocks.falling_off_short");
    }

    private void refill() {
        if (rows == null) return;
        scroll = rows.getScrollAmount();
        String q = search.getValue().toLowerCase(Locale.ROOT).trim();
        rows.reset();
        for (HillsideNet.Entry e : entries) {
            Block block = BuiltInRegistries.BLOCK.get(e.id());
            String name = block.getName().getString();
            if (q.isEmpty() || e.id().toString().contains(q) || name.toLowerCase(Locale.ROOT).contains(q)) {
                rows.add(new Row(e, block, name));
            }
        }
        rows.setScrollAmount(scroll);
    }

    private void send(String id, boolean on) {
        PacketDistributor.sendToServer(new HillsideNet.SetChoice(id, on));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 8, 0xFFFFFF);
        g.drawCenteredString(font, Component.translatable("hillside.blocks.hint"), width / 2, height - 40, 0xA0A0A0);
    }

    private final class Rows extends ContainerObjectSelectionList<Row> {
        Rows(Minecraft mc, int width, int height, int top, int itemHeight) {
            super(mc, width, height, top, itemHeight);
        }

        void reset() {
            clearEntries();
        }

        void add(Row row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return 300;
        }
    }

    private final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final ItemStack icon;
        private final String name;
        private final String id;
        private final Button toggle;

        Row(HillsideNet.Entry e, Block block, String name) {
            this.icon = new ItemStack(block);
            this.name = name;
            this.id = e.id().toString();
            boolean on = e.state() == HillsideNet.ON || e.state() == HillsideNet.ON_AFTER_RESTART;
            String label = switch (e.state()) {
                case HillsideNet.ON -> "hillside.blocks.state_on";
                case HillsideNet.ON_AFTER_RESTART -> "hillside.blocks.state_restart";
                default -> "hillside.blocks.state_off";
            };
            toggle = Button.builder(Component.translatable(label), b -> send(id, !on)).size(80, 20).build();
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY,
                           boolean hovered, float partialTick) {
            g.renderItem(icon, left + 2, top + 1);
            g.drawString(font, name, left + 24, top + 1, 0xFFFFFF);
            g.drawString(font, id, left + 24, top + 11, 0x808080);
            toggle.setPosition(left + width - 82, top);
            toggle.render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(toggle);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(toggle);
        }
    }
}
