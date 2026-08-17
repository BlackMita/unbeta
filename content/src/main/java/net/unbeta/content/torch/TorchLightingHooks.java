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
            if (!TorchItems.isUnlitTorch(held)) return ActionResult.PASS;
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
                    player.setStackInHand(hand, TorchItems.createLit(held, world.getTime()));
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
                        inv.setStack(i, TorchItems.createUnlit());
                    }
                }

                // Dropped lit torches that have expired → discard and respawn as unlit.
                // We MUST spawn a new entity rather than swapping the stack, because the
                // dynamic lights datapack caches a light-level score on the entity. Swapping
                // the stack doesn't clear that score, so the old entity keeps emitting
                // level-15 light forever. A fresh entity gets re-parsed from scratch.
                world.getEntitiesByClass(ItemEntity.class,
                        player.getBoundingBox().expand(16),
                        e -> TorchItems.isLitTorch(e.getStack())).forEach(e -> {
                    long burnoutAt = TorchItems.getBurnoutAt(e.getStack());
                    if (burnoutAt >= 0 && now >= burnoutAt) {
                        net.minecraft.entity.ItemEntity fresh = new net.minecraft.entity.ItemEntity(
                                world, e.getX(), e.getY(), e.getZ(),
                                TorchItems.createUnlit(),
                                e.getVelocity().x, e.getVelocity().y, e.getVelocity().z);
                        world.spawnEntity(fresh);
                        e.discard();
                    }
                });

                // Placed lit torches in fluid → extinguish.
                // Vanilla water flow will pop the block off anyway, but we extinguish first
                // so the dropped item is UNLIT (not a lit torch dropped into water).
                var bb = player.getBoundingBox().expand(16);
                for (var pos : net.minecraft.util.math.BlockPos.iterateOutwards(
                        player.getBlockPos(), 16, 16, 16)) {
                    if (!bb.contains(pos.getX(), pos.getY(), pos.getZ())) continue;
                    var state = world.getBlockState(pos);
                    boolean isLitTorch = (state.getBlock() instanceof UnbetaTorchBlock
                            || state.getBlock() instanceof UnbetaWallTorchBlock)
                            && state.get(TorchLogic.LIT);
                    if (!isLitTorch) continue;
                    if (!world.getFluidState(pos).isEmpty()) {
                        TorchLogic.extinguishPlaced(world, pos, state);
                    }
                }

                                // Dual-wield: unlit in one hand + lit in the other → light the unlit
                ItemStack main = player.getMainHandStack();
                ItemStack off = player.getOffHandStack();
                if (TorchItems.isUnlitTorch(main) && TorchItems.isLitTorch(off)) {
                    player.setStackInHand(Hand.MAIN_HAND, TorchItems.createLit(main, now));
                } else if (TorchItems.isUnlitTorch(off) && TorchItems.isLitTorch(main)) {
                    player.setStackInHand(Hand.OFF_HAND, TorchItems.createLit(off, now));
                }
            }
        });
    }
}
