package net.unbeta.content.client.lockey;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.unbeta.content.lockey.LockeyDoors;
import net.unbeta.content.lockey.LockeyItem;

import java.util.HashMap;
import java.util.Map;

/**
 * The client's copy of which doors are locked or damaged, so it can leave the door alone when
 * the server is going to: a locked door doesn't budge (not even for a frame), and locking,
 * unlocking or mending doesn't flick the door open first.
 */
public final class LockeyDoorsClient {

    /** Lower-half pos -> damage (low byte) | 0x100 if locked. */
    private static volatile Map<Long, Integer> doors = new HashMap<>();

    private LockeyDoorsClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(LockeyDoors.SYNC, (client, handler, buf, sender) -> {
            int n = buf.readVarInt();
            Map<Long, Integer> next = new HashMap<>();
            for (int i = 0; i < n; i++) next.put(buf.readLong(), buf.readVarInt());
            client.execute(() -> doors = next);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> doors = new HashMap<>());

        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!world.isClient) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock)) return ActionResult.PASS;
            long lower = LockeyDoors.lowerHalf(pos, state).asLong();
            int v = doors.getOrDefault(lower, 0);
            ItemStack held = player.getStackInHand(hand);
            boolean locked = (v & 0x100) != 0;
            boolean mending = (v & 0xFF) > 0 && LockeyDoors.healsDoor(state.getBlock(), held);
            // SUCCESS: swing and ask the server, but don't swing the door here.
            if (locked || mending || LockeyItem.isLockey(held)) return ActionResult.SUCCESS;
            return ActionResult.PASS;
        });
    }
}
