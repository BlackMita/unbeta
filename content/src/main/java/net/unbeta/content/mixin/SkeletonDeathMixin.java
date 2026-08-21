package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.skeleton.BonePileBlockEntity;
import net.unbeta.content.skeleton.BonePileRegistry;
import net.unbeta.content.skeleton.BonePileRespawn;
import net.unbeta.core.sched.UnbetaScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class SkeletonDeathMixin {

    @Inject(method = "dropEquipment", at = @At("HEAD"), cancellable = true)
    private void unbeta_cancelSkeletonDrops(DamageSource source, int lootingMultiplier,
                                            boolean allowDrops, CallbackInfo ci) {
        if (!((Object)this instanceof SkeletonEntity)) return;
        ci.cancel();
    }

    @Inject(method = "dropLoot", at = @At("HEAD"), cancellable = true)
    private void unbeta_cancelSkeletonLoot(DamageSource source, boolean causedByPlayer,
                                           CallbackInfo ci) {
        if (!((Object)this instanceof SkeletonEntity)) return;
        ci.cancel();
    }

    @Inject(method = "onDeath", at = @At("TAIL"))
    private void unbeta_skeletonBonePile(DamageSource source, CallbackInfo ci) {
        if (!((Object)this instanceof SkeletonEntity skeleton)) return;
        World world = skeleton.getWorld();
        if (world.isClient) return;
        ServerWorld sw = (ServerWorld) world;

        BlockPos landingPos = findLandingPos(world, skeleton.getBlockPos());
        if (landingPos == null) return;

        BlockState landingState = world.getBlockState(landingPos);
        if (!landingState.isAir() && !landingState.isOf(Blocks.LAVA)
                && !landingState.isOf(BonePileRegistry.BONE_PILE_BLOCK)) {
            scatterGear(skeleton, world, landingPos);
            return;
        }

        world.setBlockState(landingPos,
                BonePileRegistry.BONE_PILE_BLOCK.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_ALL);

        var be = world.getBlockEntity(landingPos);
        if (be instanceof BonePileBlockEntity bonePile) {
            populateBonePile(skeleton, bonePile);
            long now = world.getTime();
            bonePile.setRespawnAt(now + BonePileRespawn.RESPAWN_TICKS);
            UnbetaScheduler.schedule(sw, landingPos,
                    BonePileRespawn.WARNING_TICKS, BonePileRespawn.WARNING_HANDLER_ID);
            UnbetaScheduler.schedule(sw, landingPos,
                    BonePileRespawn.RESPAWN_TICKS, BonePileRespawn.RESPAWN_HANDLER_ID);
        }
    }

    private static BlockPos findLandingPos(World world, BlockPos from) {
        BlockPos.Mutable pos = from.mutableCopy();
        while (pos.getY() > world.getBottomY()) {
            BlockState below = world.getBlockState(pos.down());
            if (below.isSolidBlock(world, pos.down()) || below.isOf(Blocks.LAVA)) {
                return pos.toImmutable();
            }
            pos.move(0, -1, 0);
        }
        return null;
    }

    private static void populateBonePile(SkeletonEntity skeleton, BonePileBlockEntity bonePile) {
        int slot = 0;
        ItemStack mainHand = skeleton.getEquippedStack(EquipmentSlot.MAINHAND);
        if (!mainHand.isEmpty()) {
            ItemStack copy = mainHand.copy();
            if (copy.isDamageable()) copy.setDamage((int)(copy.getMaxDamage() * 0.85));
            bonePile.setStack(slot++, copy);
        }
        // Off hand too
        ItemStack offHand = skeleton.getEquippedStack(EquipmentSlot.OFFHAND);
        if (!offHand.isEmpty() && slot < 9) {
            ItemStack copy = offHand.copy();
            if (copy.isDamageable()) copy.setDamage((int)(copy.getMaxDamage() * 0.85));
            bonePile.setStack(slot++, copy);
        }
        for (EquipmentSlot es : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armor = skeleton.getEquippedStack(es);
            if (!armor.isEmpty() && slot < 9) {
                ItemStack copy = armor.copy();
                if (copy.isDamageable()) copy.setDamage((int)(copy.getMaxDamage() * 0.85));
                bonePile.setStack(slot++, copy);
            }
        }
    }

    private static void scatterGear(SkeletonEntity skeleton, World world, BlockPos pos) {
        for (EquipmentSlot es : EquipmentSlot.values()) {
            ItemStack stack = skeleton.getEquippedStack(es);
            if (!stack.isEmpty()) {
                ItemStack copy = stack.copy();
                if (copy.isDamageable()) copy.setDamage((int)(copy.getMaxDamage() * 0.85));
                net.minecraft.block.Block.dropStack(world, pos, copy);
            }
        }
    }
}
