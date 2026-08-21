package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PumpkinBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.unbeta.content.jackolantern.JackOLanternRegistry;
import net.unbeta.content.jackolantern.JackOLanternLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Redirect shears-on-pumpkin to produce an unlit Unbeta Jack o'Lantern
 * instead of a carved pumpkin. The carved pumpkin is soft-locked in Unbeta.
 */
@Mixin(PumpkinBlock.class)
public class PumpkinShearMixin {

    @Inject(method = "onUse", at = @At("HEAD"), cancellable = true)
    private void unbeta_shearToJoL(BlockState state, World world, BlockPos pos,
                                    PlayerEntity player, Hand hand,
                                    BlockHitResult hit, CallbackInfoReturnable<ActionResult> cir) {
        ItemStack held = player.getStackInHand(hand);
        if (!held.isOf(Items.SHEARS)) return;
        if (!world.isClient) {
            Direction direction = Direction.fromRotation(player.getYaw());
            Direction facing = direction.getOpposite();

            // Place unlit JoL instead of carved pumpkin
            world.setBlockState(pos,
                    JackOLanternRegistry.UNLIT_BLOCK.getDefaultState()
                            .with(net.minecraft.state.property.Properties.HORIZONTAL_FACING, facing)
                            .with(JackOLanternLogic.LIT, false),
                    net.minecraft.block.Block.NOTIFY_ALL);

            if (!player.getAbilities().creativeMode) {
                held.damage(1, player, p -> p.sendToolBreakStatus(hand));
            }
            world.playSound(null, pos,
                    net.minecraft.sound.SoundEvents.BLOCK_PUMPKIN_CARVE,
                    net.minecraft.sound.SoundCategory.BLOCKS, 1.0F, 1.0F);
        }
        cir.setReturnValue(ActionResult.SUCCESS);
    }
}
