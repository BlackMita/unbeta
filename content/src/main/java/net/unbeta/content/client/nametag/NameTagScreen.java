package net.unbeta.content.client.nametag;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.unbeta.content.nametag.NameTagNaming;
import org.lwjgl.glfw.GLFW;

/** A single text box for naming the held name tag. Enter or Done confirms; Esc cancels. */
public class NameTagScreen extends Screen {

    private static final int WIDTH = 200;
    private final Hand hand;
    private TextFieldWidget field;

    public NameTagScreen(Hand hand) {
        super(Text.literal("Name this tag"));
        this.hand = hand;
    }

    @Override
    protected void init() {
        int x = (this.width - WIDTH) / 2;
        int y = this.height / 2 - 10;
        this.field = new TextFieldWidget(this.textRenderer, x, y, WIDTH, 20, Text.literal("Name"));
        this.field.setMaxLength(NameTagNaming.MAX_LENGTH);
        this.addDrawableChild(this.field);
        this.setInitialFocus(this.field);
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> this.confirm())
                .dimensions(x, y + 26, WIDTH, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            this.confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        String name = this.field.getText().trim();
        if (!name.isEmpty()) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeEnumConstant(this.hand);
            buf.writeString(name, NameTagNaming.MAX_LENGTH);
            ClientPlayNetworking.send(NameTagNaming.CHANNEL, buf);
        }
        this.close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, this.height / 2 - 26, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
