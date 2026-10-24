package net.unbeta.content.torch;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Hand;

public final class TorchLightingHooks {

    private TorchLightingHooks() {}

    public static void register() {
        // Fire block → light held unlit torch (item swap)
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack held = player.getStackInHand(hand);
            boolean heldUnlitTorch = TorchItems.isUnlitTorch(held);
            boolean heldUnlitJol = net.unbeta.content.jackolantern.JackOLanternItems.isUnlit(held);
            if (!heldUnlitTorch && !heldUnlitJol) return ActionResult.PASS;
            // Check BOTH the clicked block AND the adjacent block (where torch would be placed).
            // Burnt mod renders fire on block faces — clicking that face targets the adjacent pos.
            boolean isFire = false;
            for (net.minecraft.util.math.BlockPos checkPos : new net.minecraft.util.math.BlockPos[]{
                    hit.getBlockPos(), hit.getBlockPos().offset(hit.getSide())}) {
                var checkState = world.getBlockState(checkPos);
                var checkId = net.minecraft.registry.Registries.BLOCK.getId(checkState.getBlock());
                if (checkState.getBlock() instanceof AbstractFireBlock
                        || (checkId.getNamespace().equals("burnt")
                            && (checkId.getPath().startsWith("blazing_")
                                || checkId.getPath().startsWith("smoldering_")
                                || checkId.getPath().startsWith("ember_")
                                || checkId.getPath().contains("fire")
                                || checkId.getPath().contains("flame")))) {
                    isFire = true;
                    break;
                }
            }
            if (isFire) {
                if (!world.isClient) {
                    if (heldUnlitTorch) {
                        TorchItems.lightOneFromStack(player, hand, world.getTime());
                    } else {
                        // Unlit JoL → lit JoL (always count 1, no stack math needed)
                        player.setStackInHand(hand,
                            net.unbeta.content.jackolantern.JackOLanternItems.createLit(held, world.getTime()));
                    }
                }
                return ActionResult.SUCCESS;
            }
            return ActionResult.PASS;
        });

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            long now = world.getTime();
            for (var player : world.getPlayers()) {
                // Burnout check across full inventory
                PlayerInventory inv = player.getInventory();
                for (int i = 0; i < inv.size(); i++) {
                    ItemStack stack = inv.getStack(i);
                    if (!TorchItems.isLitTorch(stack)) continue;
                    long burnoutAt = TorchItems.getBurnoutAt(stack);
                    if (burnoutAt < 0) {
                        // Lit with no deadline (edge case): give it one
                        inv.setStack(i, TorchItems.createLit(stack, now));
                    } else if (now >= burnoutAt) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                                net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH,
                                net.minecraft.sound.SoundCategory.BLOCKS, 0.5F, 2.0F);
                        inv.setStack(i, TorchItems.createUnlit());
                    }
                }

                // Dropped lit torches that have expired → discard and respawn as unlit.
                // Checked once a second: an entity search around every player every tick
                // added up with many players. (Placed torches in water: their own ticker.)
                if (now % 20 != 0) continue;
                // We MUST spawn a new entity rather than swapping the stack, because the
                // dynamic lights datapack caches a light-level score on the entity. Swapping
                // the stack doesn't clear that score, so the old entity keeps emitting
                // level-15 light forever. A fresh entity gets re-parsed from scratch.
                world.getEntitiesByClass(ItemEntity.class,
                        player.getBoundingBox().expand(16),
                        e -> TorchItems.isLitTorch(e.getStack())).forEach(e -> {
                    long burnoutAt = TorchItems.getBurnoutAt(e.getStack());
                    if (burnoutAt >= 0 && now >= burnoutAt) {
                        world.playSound(null, e.getX(), e.getY(), e.getZ(),
                                net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH,
                                net.minecraft.sound.SoundCategory.BLOCKS, 0.5F, 2.0F);
                        net.minecraft.entity.ItemEntity fresh = new net.minecraft.entity.ItemEntity(
                                world, e.getX(), e.getY(), e.getZ(),
                                TorchItems.createUnlit(),
                                e.getVelocity().x, e.getVelocity().y, e.getVelocity().z);
                        world.spawnEntity(fresh);
                        e.discard();
                    }
                });
            }
        });
    }
}
