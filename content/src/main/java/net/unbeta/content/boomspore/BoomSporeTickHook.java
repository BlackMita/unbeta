package net.unbeta.content.boomspore;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Server tick hook: scans player inventories + nearby dropped entities for live
 * Boom Spores whose fuse has expired, and triggers creeper-strength explosions.
 *
 * <p>Container scanning (chests etc.) is done within a radius of each player,
 * same bounded pattern as the torch burnout scanner.
 */
public final class BoomSporeTickHook {

    private static final int SCAN_RADIUS = 16;

    private BoomSporeTickHook() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            long now = world.getTime();
            for (var player : world.getPlayers()) {
                // 1. Scan player inventory
                var inv = player.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack stack = inv.getStack(i);
                    if (BoomSporeItem.isLive(stack) && now >= BoomSporeItem.getDetonationAt(stack)) {
                        inv.setStack(i, ItemStack.EMPTY);
                        world.createExplosion(null, player.getX(), player.getY(), player.getZ(),
                                3.0F, World.ExplosionSourceType.MOB);
                        break; // one explosion per player per tick max
                    }
                }

                // 2. Dropped boom spores: explode on landing (when on ground).
                // They are NEVER collectible — only obtainable from a creeper.
                world.getEntitiesByClass(ItemEntity.class,
                        player.getBoundingBox().expand(SCAN_RADIUS),
                        e -> e.getStack().isOf(BoomSporeRegistry.BOOM_SPORE_ITEM)).forEach(e -> {
                    e.setPickupDelayInfinite(); // never collectible
                    if (e.isOnGround()) {
                        world.createExplosion(null, e.getX(), e.getY(), e.getZ(),
                                3.0F, World.ExplosionSourceType.MOB);
                        e.discard();
                    }
                });

                // 3. Scan nearby containers (chests, barrels, etc.)
                for (var pos : BlockPos.iterateOutwards(player.getBlockPos(),
                        SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
                    BlockEntity be = world.getBlockEntity(pos);
                    if (!(be instanceof Inventory container)) continue;
                    for (int i = 0; i < container.size(); i++) {
                        ItemStack stack = container.getStack(i);
                        if (BoomSporeItem.isLive(stack) && now >= BoomSporeItem.getDetonationAt(stack)) {
                            container.setStack(i, ItemStack.EMPTY);
                            // Explode below the container (destroys it)
                            world.createExplosion(null,
                                    pos.getX() + 0.5, pos.getY() - 0.5, pos.getZ() + 0.5,
                                    3.0F, World.ExplosionSourceType.MOB);
                            break;
                        }
                    }
                }
            }
        });
    }
}
