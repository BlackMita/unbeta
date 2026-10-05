package net.unbeta.content.torch;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * A player whose head is fully underwater has every lit Unbeta torch they carry put out -
 * hands, hotbar and inventory alike - with a single hiss.
 */
public final class TorchDousing {

    private TorchDousing() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(TorchDousing::tick);
    }

    private static void tick(ServerWorld world) {
        if (world.getTime() % 5 != 0) return;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (!player.isSubmergedInWater()) continue;
            PlayerInventory inv = player.getInventory();
            boolean doused = false;
            for (int i = 0; i < inv.size(); i++) {
                ItemStack stack = inv.getStack(i);
                if (!TorchItems.isLitTorch(stack)) continue;
                ItemStack unlit = TorchItems.createUnlit();
                unlit.setCount(stack.getCount());
                inv.setStack(i, unlit);
                doused = true;
            }
            if (doused) {
                world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH,
                        SoundCategory.PLAYERS, 0.6F, 1.4F);
            }
        }
    }
}
