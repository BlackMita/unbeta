package net.unbeta.content.nametag;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.SharedConstants;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/**
 * Naming a name tag without an anvil (anvils are removed in Unbeta). Right-clicking with an
 * unnamed name tag opens a prompt on the client (NameTagScreen); the typed name arrives
 * here. The server re-checks that the player still holds an unnamed name tag in that hand
 * before naming it, so the name can only ever land on the intended tag. Anvil rules: at
 * most 50 characters, invalid characters stripped, the whole stack is named. Free.
 */
public final class NameTagNaming {

    public static final Identifier CHANNEL = new Identifier("unbeta-content", "name_tag");
    public static final int MAX_LENGTH = 50; // the anvil's limit

    private NameTagNaming() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CHANNEL, (server, player, handler, buf, responseSender) -> {
            Hand hand = buf.readEnumConstant(Hand.class);
            String name = SharedConstants.stripInvalidChars(buf.readString(MAX_LENGTH)).trim();
            server.execute(() -> {
                ItemStack stack = player.getStackInHand(hand);
                if (name.isEmpty() || !stack.isOf(Items.NAME_TAG) || stack.hasCustomName()) return;
                stack.setCustomName(Text.literal(name));
                player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.ITEM_BOOK_PAGE_TURN,
                        SoundCategory.PLAYERS, 1.0F, 1.0F);
            });
        });
    }
}
