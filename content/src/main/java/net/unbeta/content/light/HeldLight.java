package net.unbeta.content.light;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.jackolantern.JackOLanternItems;
import net.unbeta.content.torch.TorchItems;

/**
 * The one set of right-click rules for every placed light that can be lit or unlit.
 * Placed torches and Jack o'Lanterns both delegate here (furnaces and Burnt blocks will
 * too), so they can't disagree about what counts as a light source.
 *
 * <ol>
 *   <li>Unlit light item + lit placed light: lights the item in hand.</li>
 *   <li>Lit light item (or flint and steel) + unlit placed light: lights the placed light.</li>
 *   <li>Lit + lit: nothing, ever - a lit thing never snuffs a lit thing.</li>
 *   <li>Anything else + lit placed light: snuffs it.</li>
 * </ol>
 */
public final class HeldLight {

    private HeldLight() {}

    /** An unlit item that can be lit: unlit torch or unlit Jack o'Lantern. */
    public static boolean isUnlit(ItemStack stack) {
        return TorchItems.isUnlitTorch(stack) || JackOLanternItems.isUnlit(stack);
    }

    /** A burning item: lit torch or lit Jack o'Lantern. */
    public static boolean isLit(ItemStack stack) {
        return TorchItems.isLitTorch(stack) || JackOLanternItems.isLit(stack);
    }

    /** Anything that can light something else. */
    public static boolean isIgniter(ItemStack stack) {
        return isLit(stack) || stack.isOf(Items.FLINT_AND_STEEL);
    }

    /** Light whatever unlit light item is in this hand. */
    public static void lightHeld(PlayerEntity player, Hand hand, long now) {
        ItemStack held = player.getStackInHand(hand);
        if (TorchItems.isUnlitTorch(held)) {
            TorchItems.lightOneFromStack(player, hand, now);
        } else if (JackOLanternItems.isUnlit(held)) {
            player.setStackInHand(hand, JackOLanternItems.createLit(held, now));
        }
    }

    /** The rules, for a placed light whose lit state is placedLit. */
    public static ActionResult onUse(boolean placedLit, World world, BlockPos pos,
                                     PlayerEntity player, Hand hand,
                                     Runnable lightPlaced, Runnable extinguishPlaced) {
        ItemStack held = player.getStackInHand(hand);

        // 1. Unlit light item: lights from a lit placed light; otherwise let it be placed.
        if (isUnlit(held)) {
            if (!placedLit) return ActionResult.PASS;
            if (!world.isClient) lightHeld(player, hand, world.getTime());
            world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 0.6F, 1.3F);
            return ActionResult.SUCCESS;
        }

        // 2 and 3. Igniter: lights an unlit placed light; never snuffs a lit one.
        if (isIgniter(held)) {
            if (placedLit) return ActionResult.PASS;
            if (!world.isClient) {
                lightPlaced.run();
                if (held.isOf(Items.FLINT_AND_STEEL) && !player.getAbilities().creativeMode) {
                    held.damage(1, player, p -> p.sendToolBreakStatus(hand));
                }
            }
            return ActionResult.SUCCESS;
        }

        // 4. Anything else snuffs a lit placed light.
        if (placedLit) {
            if (!world.isClient) extinguishPlaced.run();
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }
}
