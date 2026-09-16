package net.unbeta.content.client.clambox;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
        context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
