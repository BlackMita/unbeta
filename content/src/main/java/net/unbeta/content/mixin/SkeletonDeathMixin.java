package net.unbeta.content.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.unbeta.content.skeleton.BonePileBlockEntity;
import net.unbeta.content.skeleton.BonePileDeaths;
import net.unbeta.content.skeleton.BonePileRegistry;
import net.unbeta.content.skeleton.BonePileRespawn;
import net.unbeta.core.sched.UnbetaScheduler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Skeleton death -> bone pile.
 *
 * <p>The pile is placed where the BODY comes to rest, not where HP hit zero. A dead mob
 * is "immobile" (isImmobile() == isDead()) but physics still runs, so knockback carries
 * the corpse through an arc for the ~20-tick death animation before vanilla removes it
 * in updatePostDeath. We therefore:
 *   1. onDeath:          snapshot the gear immediately, place nothing.
 *   2. updatePostDeath:  once the body is removed (deathTime >= 20), place the pile at
 *                        the body's final position and fill it from the snapshot.
 *
 * <p>Placement must happen at the final position rather than placing early and moving
 * the pile later, because the respawn scheduler is keyed to the pile's BlockPos.
 */
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

    /** Step 1: at the instant of death, snapshot the gear. Nothing is placed yet. */
    @Inject(method = "onDeath", at = @At("TAIL"))
    private void unbeta_snapshotSkeletonGear(DamageSource source, CallbackInfo ci) {
        if (!((Object)this instanceof SkeletonEntity skeleton)) return;
        if (skeleton.getWorld().isClient) return;
        BonePileDeaths.PENDING.put(skeleton.getUuid(), buildGear(skeleton));
    }

    /** Step 2: when the body is actually removed, place the pile where it came to rest. */
    @Inject(method = "updatePostDeath", at = @At("TAIL"))
    private void unbeta_placeBonePileWhereBodyLanded(CallbackInfo ci) {
        if (!((Object)this instanceof SkeletonEntity skeleton)) return;
        World world = skeleton.getWorld();
        if (world.isClient) return;
        if (!skeleton.isRemoved() || skeleton.deathTime < 20) return; // body not gone yet

        List<ItemStack> gear = BonePileDeaths.PENDING.remove(skeleton.getUuid());
        if (gear == null) gear = buildGear(skeleton); // snapshot lost (e.g. restart): drops were cancelled, so gear is still on the body

        placeBonePile((ServerWorld) world, skeleton.getBlockPos(), gear);
    }

    private static void placeBonePile(ServerWorld sw, BlockPos from, List<ItemStack> gear) {
        BlockPos landingPos = findLandingPos(sw, from);
        if (landingPos == null) return;

        BlockState landingState = sw.getBlockState(landingPos);
        // Blocks the bone pile can REPLACE (land on top of/displace)
        boolean canReplace = landingState.isAir()
                || landingState.isOf(BonePileRegistry.BONE_PILE_BLOCK)
                || landingState.isOf(Blocks.SNOW)
                || landingState.isOf(Blocks.GRASS)
                || landingState.isOf(Blocks.TALL_GRASS)
                || landingState.isIn(net.minecraft.registry.tag.BlockTags.FLOWERS)
                || (landingState.isOf(Blocks.WATER) && !landingState.get(net.minecraft.state.property.Properties.LEVEL_15.equals(net.minecraft.state.property.Properties.LEVEL_15) ? net.minecraft.state.property.Properties.LEVEL_15 : net.minecraft.state.property.Properties.LEVEL_15).equals(0));
        // Flowing water/lava: use fluid state level check
        net.minecraft.fluid.FluidState fluidState = sw.getFluidState(landingPos);
        boolean isFlowing = !fluidState.isEmpty() && !fluidState.isStill();
        boolean isSource = !fluidState.isEmpty() && fluidState.isStill();
        if (isSource) {
            // Source water/lava destroys bone pile before landing
            scatterGear(sw, landingPos, gear);
            return;
        }
        if (!canReplace && !isFlowing) {
            scatterGear(sw, landingPos, gear);
            return;
        }

        sw.setBlockState(landingPos, BonePileRegistry.BONE_PILE_BLOCK.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_ALL);

        if (sw.getBlockEntity(landingPos) instanceof BonePileBlockEntity bonePile) {
            for (int i = 0; i < gear.size() && i < 9; i++) {
                bonePile.setStack(i, gear.get(i));
            }
            long now = sw.getTime();
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

    /** Same order and 85%-worn treatment as before: main hand, off hand, then armour. */
    private static List<ItemStack> buildGear(SkeletonEntity skeleton) {
        List<ItemStack> out = new ArrayList<>();
        for (EquipmentSlot es : new EquipmentSlot[]{
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = skeleton.getEquippedStack(es);
            if (stack.isEmpty() || out.size() >= 9) continue;
            ItemStack copy = stack.copy();
            if (copy.isDamageable()) copy.setDamage((int)(copy.getMaxDamage() * 0.85));
            out.add(copy);
        }
        return out;
    }

    private static void scatterGear(World world, BlockPos pos, List<ItemStack> gear) {
        for (ItemStack stack : gear) {
            net.minecraft.block.Block.dropStack(world, pos, stack);
        }
        // The pile "formed and broke" rather than silently not existing: drop its own
        // loot table (1-2 bones), exactly as if a player had broken it by hand.
        net.minecraft.block.Block.dropStacks(
                BonePileRegistry.BONE_PILE_BLOCK.getDefaultState(), world, pos);
    }
}
