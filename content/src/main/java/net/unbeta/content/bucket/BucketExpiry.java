package net.unbeta.content.bucket;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.unbeta.content.lockey.LoadedBlockEntities;

/**
 * Timed buckets sitting in containers (chests, barrels, hoppers, chest minecarts - anything
 * that isn't a player or a mob): once a second, an expired lava bucket vanishes with a snuff,
 * and a finished Ice Bucket turns into its ice, cobblestone or ice cream. The Clambox is left alone.
 */
public final class BucketExpiry {

    private BucketExpiry() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 == 0) sweep(world);
        });
    }

    private static void sweep(ServerWorld world) {
        for (BlockEntity be : LoadedBlockEntities.iterate(world)) {
            if (!(be instanceof Inventory inv) || be.getClass().getName().contains(".clambox.")) continue;
            if (expire(world, inv, be.getPos())) inv.markDirty();
        }
        for (Entity e : world.iterateEntities()) {
            if (e instanceof LivingEntity || !(e instanceof Inventory inv)) continue;
            if (expire(world, inv, e.getBlockPos())) inv.markDirty();
        }
    }

    private static boolean expire(ServerWorld world, Inventory inv, BlockPos at) {
        boolean changed = false;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (BucketBurn.due(s, world)) {
                inv.setStack(i, ItemStack.EMPTY);
                world.playSound(null, at, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.7f, 1.0f);
                changed = true;
            } else if (IceFreeze.isFilled(s) && IceFreeze.due(s, world)) {
                inv.setStack(i, IceFreeze.result(s));
                IceFreeze.sound(world, at, s);
                changed = true;
            }
        }
        return changed;
    }
}
