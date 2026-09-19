package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * XP gate for the Obsidian Door, injected at the single chokepoint every open/close
 * path funnels through - DoorBlock.setOpen.
 *
 * <p>Opening: requires the nearest player to have >= COST points. On success, consume
 * COST from them (the door now "holds" it, represented purely by the open state).
 * On failure, cancel the open, play the deny sound, and message the player.
 *
 * <p>Closing: release COST back as XP orbs at the door.
 */
@Mixin(DoorBlock.class)
public abstract class ObsidianDoorGateMixin {

    private static final int COST = 550;

    @Inject(method = "setOpen", at = @At("HEAD"), cancellable = true)
    private void unbeta_xpGate(Entity entity, World world, BlockState state, BlockPos pos,
                               boolean open, CallbackInfo ci) {
        // Only our door
        if (!((Object)this instanceof net.unbeta.content.obsidiandoor.ObsidianDoorBlock)) return;
        if (world.isClient) return;

        boolean currentlyOpen = state.get(DoorBlock.OPEN);
        if (open == currentlyOpen) return; // no change

        net.minecraft.server.world.ServerWorld sw = (net.minecraft.server.world.ServerWorld) world;

        if (open) {
            // OPENING: find nearest player, check + charge
            PlayerEntity nearest = world.getClosestPlayer(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 32.0, false);
            if (nearest == null) { ci.cancel(); return; }

            if (!net.unbeta.content.obsidiandoor.XpHelper.canAfford(nearest, COST)) {
                world.playSound(null, pos,
                        net.unbeta.content.lockey.LockeyRegistry.CHEST_DENY,
                        net.minecraft.sound.SoundCategory.BLOCKS, 0.8F, 1.0F);
                nearest.sendMessage(net.minecraft.text.Text.literal(
                        "This door needs more experience to open.")
                        .formatted(net.minecraft.util.Formatting.GREEN), false);
                ci.cancel();
                return;
            }
            nearest.addExperience(-COST);
        } else {
            // CLOSING: release the stored XP as orbs at the door
            net.minecraft.entity.ExperienceOrbEntity.spawn(sw,
                    new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5), COST);
        }
    }
}
