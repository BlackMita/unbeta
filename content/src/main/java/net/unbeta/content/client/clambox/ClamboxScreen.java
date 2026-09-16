package net.unbeta.content.client.clambox;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.gui.DrawContext;
import net.unbeta.content.clambox.ClamboxScreenHandler;

public class ClamboxScreen extends HandledScreen<ClamboxScreenHandler> {

    private static final Identifier TEXTURE =
            new Identifier("unbeta-content", "textures/gui/clambox.png");

    public ClamboxScreen(ClamboxScreenHandler handler, PlayerInventory inv, Text title) {
        super(handler, inv, title);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        // Base GUI texture
        context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);

        // Progress arrow: same position as furnace (79,34), sprite at texture (176,14) 24x16
        int progress = this.handler.getProgress();
        int maxProgress = this.handler.getMaxProgress();
        if (maxProgress > 0 && progress > 0) {
            int arrowWidth = progress * 24 / maxProgress;
            context.drawTexture(TEXTURE, x + 79, y + 34, 176, 14, arrowWidth, 16);
        }

        // Spinning circle: a small marker orbiting the circle I drew where the flame was.
        // Runs as long as pearling is active (progress > 0), purely time-based.
        if (progress > 0) {
            double angle = (System.currentTimeMillis() % 1200) / 1200.0 * 2 * Math.PI;
            int cx = x + 62; // centre of circle (where flame used to be)
            int cy = y + 43;
            int r = 6;
            int dotX = (int) Math.round(cx + r * Math.cos(angle));
            int dotY = (int) Math.round(cy + r * Math.sin(angle));
            context.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, 0xFFFFFFFF); // white dot
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
