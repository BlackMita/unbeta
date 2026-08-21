package net.unbeta.content.skeleton;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.unbeta.core.sched.UnbetaScheduler;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles bone pile respawn via the persisted tick scheduler.
 * Two callbacks: one for the warning sound (at 5 seconds remaining),
 * one for the actual respawn.
 */
public final class BonePileRespawn {

    public static final String RESPAWN_HANDLER_ID = "unbeta:bone_pile_respawn";
    public static final String WARNING_HANDLER_ID = "unbeta:bone_pile_warning";
    public static final long RESPAWN_TICKS = 400L;  // 20 seconds
    public static final long WARNING_TICKS = 300L;  // 15 seconds (warning at 5s remaining)

    private BonePileRespawn() {}

    public static void register() {
        UnbetaScheduler.registerPositionHandler(RESPAWN_HANDLER_ID, BonePileRespawn::onRespawn);
        UnbetaScheduler.registerPositionHandler(WARNING_HANDLER_ID, BonePileRespawn::onWarning);
    }

    private static void onWarning(ServerWorld world, BlockPos pos, NbtCompound payload) {
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BonePileBlock)) return;
        // Play bone click sounds
        world.playSound(null, pos, SoundEvents.BLOCK_BONE_BLOCK_BREAK,
                SoundCategory.BLOCKS, 0.4F, 0.8F);
    }

    private static void onRespawn(ServerWorld world, BlockPos pos, NbtCompound payload) {
        var state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BonePileBlock)) return;
        var be = world.getBlockEntity(pos);
        if (!(be instanceof BonePileBlockEntity bonePile)) return;

        // Force-close any player viewing this inventory
        for (var player : world.getPlayers()) {
            if (player.currentScreenHandler instanceof net.minecraft.screen.Generic3x3ContainerScreenHandler) {
                // Check if they're viewing THIS bone pile's inventory
                if (player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0) {
                    player.closeHandledScreen();
                }
            }
        }

        // Collect items from the bone pile THEN clear it before removing the block.
        // If we don't clear first, onStateReplaced scatters items as drops (item dupe).
        DefaultedList<ItemStack> items = bonePile.getItems();
        bonePile.clear(); // prevent onStateReplaced from scattering
        bonePile.markDirty();

        // Remove the bone pile block (onStateReplaced now finds empty inventory)
        world.removeBlock(pos, false);

        // Spawn the skeleton
        SkeletonEntity skeleton = new SkeletonEntity(
                net.minecraft.entity.EntityType.SKELETON, world);
        skeleton.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5,
                world.random.nextFloat() * 360.0F, 0.0F);
        skeleton.setPersistent(); // don't despawn immediately

        // Equip items from bone pile
        equipSkeleton(skeleton, items);

        world.spawnEntity(skeleton);
    }

    /** Equip the skeleton based on bone pile contents. */
    private static void equipSkeleton(SkeletonEntity skeleton, DefaultedList<ItemStack> items) {
        ItemStack weapon = ItemStack.EMPTY;
        ItemStack bow = ItemStack.EMPTY;
        ItemStack helmet = ItemStack.EMPTY;
        ItemStack chest = ItemStack.EMPTY;
        ItemStack legs = ItemStack.EMPTY;
        ItemStack boots = ItemStack.EMPTY;
        List<ItemStack> otherItems = new ArrayList<>();

        for (ItemStack stack : items) {
            if (stack.isEmpty()) continue;
            Item item = stack.getItem();

            if (item instanceof ArmorItem armor) {
                switch (armor.getSlotType()) {
                    case HEAD -> { if (helmet.isEmpty()) helmet = stack; else otherItems.add(stack); }
                    case CHEST -> { if (chest.isEmpty()) chest = stack; else otherItems.add(stack); }
                    case LEGS -> { if (legs.isEmpty()) legs = stack; else otherItems.add(stack); }
                    case FEET -> { if (boots.isEmpty()) boots = stack; else otherItems.add(stack); }
                    default -> otherItems.add(stack);
                }
            } else if (item instanceof BowItem) {
                if (bow.isEmpty()) bow = stack; else otherItems.add(stack);
            } else if (item instanceof SwordItem || item instanceof AxeItem
                    || item instanceof PickaxeItem || item instanceof ShovelItem
                    || item instanceof HoeItem || item instanceof TridentItem) {
                if (weapon.isEmpty()) weapon = stack; else otherItems.add(stack);
            } else {
                otherItems.add(stack);
            }
        }

        // Equip armor
        if (!helmet.isEmpty()) skeleton.equipStack(EquipmentSlot.HEAD, helmet);
        if (!chest.isEmpty()) skeleton.equipStack(EquipmentSlot.CHEST, chest);
        if (!legs.isEmpty()) skeleton.equipStack(EquipmentSlot.LEGS, legs);
        if (!boots.isEmpty()) skeleton.equipStack(EquipmentSlot.FEET, boots);

        // Weapon priority: bow > melee weapon > random non-weapon item
        if (!bow.isEmpty()) {
            skeleton.equipStack(EquipmentSlot.MAINHAND, bow);
            // If there's also a melee weapon, put it in offhand
            if (!weapon.isEmpty()) skeleton.equipStack(EquipmentSlot.OFFHAND, weapon);
        } else if (!weapon.isEmpty()) {
            skeleton.equipStack(EquipmentSlot.MAINHAND, weapon);
        } else if (!otherItems.isEmpty()) {
            // No weapon at all — pick a random non-weapon item to hold harmlessly
            int idx = skeleton.getWorld().random.nextInt(otherItems.size());
            skeleton.equipStack(EquipmentSlot.MAINHAND, otherItems.remove(idx));
        }

        // Make sure skeleton updates its AI based on held item
        ItemStack mainHandItem = skeleton.getEquippedStack(net.minecraft.entity.EquipmentSlot.MAINHAND);
        boolean isWeapon = mainHandItem.isEmpty()
                || mainHandItem.getItem() instanceof net.minecraft.item.BowItem
                || mainHandItem.getItem() instanceof net.minecraft.item.SwordItem
                || mainHandItem.getItem() instanceof net.minecraft.item.AxeItem
                || mainHandItem.getItem() instanceof net.minecraft.item.PickaxeItem
                || mainHandItem.getItem() instanceof net.minecraft.item.ShovelItem
                || mainHandItem.getItem() instanceof net.minecraft.item.HoeItem
                || mainHandItem.getItem() instanceof net.minecraft.item.TridentItem;
        if (isWeapon) {
            skeleton.updateAttackType();
        } else {
            // Holding a harmless item — tag the skeleton so our mixin can cancel attacks.
            BonePileRegistry.HARMLESS_SKELETONS.add(skeleton.getUuid());
        }

        // Drop any remaining items we couldn't equip
        for (ItemStack leftover : otherItems) {
            if (!leftover.isEmpty()) {
                net.minecraft.block.Block.dropStack(skeleton.getWorld(),
                        skeleton.getBlockPos(), leftover);
            }
        }
    }
}
