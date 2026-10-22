package net.unbeta.hg.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.unbeta.hg.Keeps;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Keep screen: every item in the game with a search box, like Creative's. Click one to keep
 * its kind (paying levels); your keeps are listed at the top, with the price of the next one.
 * In the lobby, click one of your keeps to give it back.
 */
public final class KeepScreen extends Screen {

    private static final int SLOT = 18;
    private static final int GRID_TOP = 92;

    private TextFieldWidget search;
    private final List<ItemStack> all = new ArrayList<>();
    private final List<ItemStack> shown = new ArrayList<>();
    private int scroll;          // in rows
    private int cols, left, panelWidth;

    public KeepScreen() {
        super(Text.literal("Keeps"));
    }

    @Override
    protected void init() {
        cols = MathHelper.clamp((this.width - 40) / SLOT, 9, 16);
        panelWidth = cols * SLOT;
        left = (this.width - panelWidth) / 2;
        if (all.isEmpty()) collectItems();
        String old = search == null ? "" : search.getText();
        search = new TextFieldWidget(this.textRenderer, left, GRID_TOP - 20, panelWidth, 14, Text.literal("Search"));
        search.setMaxLength(50);
        search.setText(old);
        search.setChangedListener(s -> refilter());
        this.addDrawableChild(search);
        this.setInitialFocus(search);
        refilter();
    }

    /** Everything Creative's search tab shows (one of each item), or the whole item list as a fallback. */
    private void collectItems() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Set<Item> seen = new HashSet<>();
        try {
            if (mc.player != null && mc.world != null) {
                ItemGroups.updateDisplayContext(mc.player.networkHandler.getEnabledFeatures(), false, mc.world.getRegistryManager());
                for (ItemStack s : ItemGroups.getSearchGroup().getDisplayStacks()) {
                    if (!s.isEmpty() && seen.add(s.getItem())) all.add(new ItemStack(s.getItem()));
                }
            }
        } catch (Throwable t) {
            all.clear();
            seen.clear();
        }
        if (all.isEmpty()) {
            for (Item i : Registries.ITEM) if (i != Items.AIR && seen.add(i)) all.add(new ItemStack(i));
        }
    }

    private void refilter() {
        String q = search == null ? "" : search.getText().trim().toLowerCase(Locale.ROOT);
        shown.clear();
        for (ItemStack s : all) {
            if (q.isEmpty() || s.getName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || Registries.ITEM.getId(s.getItem()).getPath().contains(q.replace(' ', '_'))) {
                shown.add(s);
            }
        }
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
    }

    private int visibleRows() {
        return Math.max(1, (this.height - GRID_TOP - 10) / SLOT);
    }

    private int maxScroll() {
        int rows = (shown.size() + cols - 1) / cols;
        return Math.max(0, rows - visibleRows());
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        ctx.fill(left - 6, 6, left + panelWidth + 6, this.height - 4, 0xC0101010);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Keeps").formatted(Formatting.GREEN, Formatting.BOLD), this.width / 2, 10, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(KeepsClient.status).formatted(Formatting.GRAY), this.width / 2, 22, 0xFFFFFF);

        int level = this.client != null && this.client.player != null ? this.client.player.experienceLevel : 0;
        int cost = KeepsClient.nextCost;
        Formatting priceColour = !KeepsClient.canBuy ? Formatting.DARK_GRAY : level >= cost ? Formatting.GREEN : Formatting.RED;
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("Next keep: " + cost + " levels  (you have " + level + ")").formatted(priceColour), this.width / 2, 34, 0xFFFFFF);

        // Your keeps.
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("Your keeps:").formatted(Formatting.WHITE), left, 52, 0xFFFFFF);
        int kx = left + this.textRenderer.getWidth("Your keeps:") + 6;
        ItemStack hoverStack = null;
        List<Text> hoverLines = null;
        if (KeepsClient.entries.isEmpty()) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("none yet").formatted(Formatting.DARK_GRAY), kx, 52, 0xFFFFFF);
        }
        for (KeepsClient.Entry e : KeepsClient.entries) {
            int y = 47;
            ctx.fill(kx - 1, y - 1, kx + 17, y + 17, e.locked() ? 0xFF2E7D32 : 0xFFB8A000);
            ctx.fill(kx, y, kx + 16, y + 16, 0xFF202020);
            ItemStack s = new ItemStack(e.head());
            ctx.drawItem(s, kx, y);
            if (mouseX >= kx && mouseX < kx + 16 && mouseY >= y && mouseY < y + 16) {
                hoverLines = new ArrayList<>();
                hoverLines.add(s.getName());
                if (e.members().size() > 1) hoverLines.add(Text.literal("Whole family: " + e.members().size() + " kinds").formatted(Formatting.GRAY));
                hoverLines.add(e.locked() ? Text.literal("Locked in for the round").formatted(Formatting.GREEN)
                        : Text.literal("Click to give it back (levels returned)").formatted(Formatting.YELLOW));
            }
            kx += 20;
        }

        // The item grid.
        int rows = visibleRows();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int i = (scroll + r) * cols + c;
                if (i >= shown.size()) break;
                ItemStack s = shown.get(i);
                int x = left + c * SLOT, y = GRID_TOP + r * SLOT;
                boolean kept = KeepsClient.isKept(s.getItem());
                ctx.fill(x, y, x + 17, y + 17, kept ? 0x6040FF60 : 0x30FFFFFF);
                ctx.drawItem(s, x + 1, y + 1);
                if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
                    ctx.fill(x, y, x + 17, y + 17, 0x50FFFFFF);
                    hoverStack = s;
                }
            }
        }
        if (shown.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal("No items match.").formatted(Formatting.DARK_GRAY), this.width / 2, GRID_TOP + 6, 0xFFFFFF);
        }
        int max = maxScroll();
        if (max > 0) {
            int barTop = GRID_TOP, barH = rows * SLOT;
            int knob = Math.max(10, barH * rows / (rows + max));
            int ky = barTop + (barH - knob) * scroll / max;
            ctx.fill(left + panelWidth + 2, barTop, left + panelWidth + 5, barTop + barH, 0x40FFFFFF);
            ctx.fill(left + panelWidth + 2, ky, left + panelWidth + 5, ky + knob, 0xC0FFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);
        if (search.getText().isEmpty() && !search.isFocused()) {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("Search items...").formatted(Formatting.DARK_GRAY), left + 4, GRID_TOP - 17, 0xFFFFFF);
        }

        if (hoverLines != null) {
            ctx.drawTooltip(this.textRenderer, hoverLines, mouseX, mouseY);
        } else if (hoverStack != null) {
            List<Text> lines = new ArrayList<>(Screen.getTooltipFromItem(MinecraftClient.getInstance(), hoverStack));
            KeepsClient.Entry e = KeepsClient.entryFor(hoverStack.getItem());
            if (e != null) {
                lines.add(Text.literal(e.locked() ? "Kept (locked in)" : "Kept - click it in Your keeps to give it back").formatted(Formatting.GREEN));
            } else if (!KeepsClient.canBuy) {
                lines.add(Text.literal(KeepsClient.status).formatted(Formatting.DARK_GRAY));
            } else {
                lines.add(Text.literal("Click to keep - " + cost + " levels").formatted(level >= cost || KeepsClient.lobby ? Formatting.GREEN : Formatting.RED));
            }
            ctx.drawTooltip(this.textRenderer, lines, mouseX, mouseY);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button != 0) return false;
        // One of your keeps (lobby: give it back).
        int kx = left + this.textRenderer.getWidth("Your keeps:") + 6;
        for (KeepsClient.Entry e : KeepsClient.entries) {
            if (mouseX >= kx && mouseX < kx + 16 && mouseY >= 47 && mouseY < 63) {
                if (!e.locked()) send(Keeps.UNPICK, e.head());
                return true;
            }
            kx += 20;
        }
        // The grid.
        if (mouseY >= GRID_TOP && mouseX >= left && mouseX < left + cols * SLOT) {
            int r = (int) ((mouseY - GRID_TOP) / SLOT), c = (int) ((mouseX - left) / SLOT);
            if (r < visibleRows()) {
                int i = (scroll + r) * cols + c;
                if (i < shown.size()) {
                    send(Keeps.PICK, shown.get(i).getItem());
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = MathHelper.clamp(scroll - (int) Math.signum(amount), 0, maxScroll());
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static void send(Identifier channel, Item item) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeIdentifier(Registries.ITEM.getId(item));
        ClientPlayNetworking.send(channel, buf);
    }
}
