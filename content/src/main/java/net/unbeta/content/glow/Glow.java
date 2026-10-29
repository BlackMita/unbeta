package net.unbeta.content.glow;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import net.unbeta.content.gunpowder.Gunpowder;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glowstone dust rubbed onto a block makes that block give off light 11 (enough that the space
 * beside it keeps Endermen away). The block keeps its glow when a piston pushes it or it falls,
 * and loses it once it's gone (mined, blown up, burnt, washed away).
 */
public final class Glow {

    private Glow() {}

    public static final int LIGHT = 11;
    public static final Identifier SYNC = new Identifier("unbeta-content", "glow");
    public static final DustParticleEffect SPARKLE = new DustParticleEffect(new Vector3f(1.0F, 0.85F, 0.35F), 0.7F);

    /** Glowing positions per server world (read by the lighting thread, so concurrent). */
    private static final Map<RegistryKey<World>, Set<Long>> SERVER = new ConcurrentHashMap<>();
    /** Glowing positions in the world this game is showing. */
    public static final Set<Long> CLIENT = ConcurrentHashMap.newKeySet();

    /** Asked by the lighting engine, on either side. */
    public static boolean glows(World world, long pos) {
        if (world.isClient) return !CLIENT.isEmpty() && CLIENT.contains(pos);
        Set<Long> set = SERVER.get(world.getRegistryKey());
        return set != null && !set.isEmpty() && set.contains(pos);
    }

    public static boolean anyServer(World world) {
        Set<Long> set = SERVER.get(world.getRegistryKey());
        return set != null && !set.isEmpty();
    }

    // ------------------------------------------------------------------ saving

    private static final class Saved extends PersistentState {
        final Set<Long> positions = ConcurrentHashMap.newKeySet();

        static Saved read(NbtCompound nbt) {
            Saved s = new Saved();
            for (long p : nbt.getLongArray("Glowing")) s.positions.add(p);
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            LongOpenHashSet all = new LongOpenHashSet();
            for (Long p : positions) all.add(p.longValue());
            nbt.putLongArray("Glowing", all.toLongArray());
            return nbt;
        }
    }

    private static Saved saved(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(Saved::read, Saved::new, "unbeta_glow");
    }

    // ------------------------------------------------------------------ changing

    public static boolean add(ServerWorld world, BlockPos pos) {
        Saved s = saved(world);
        if (!s.positions.add(pos.asLong())) return false;
        s.markDirty();
        SERVER.put(world.getRegistryKey(), s.positions);
        world.getChunkManager().getLightingProvider().checkBlock(pos);
        send(world, (byte) 1, new long[]{pos.asLong()});
        return true;
    }

    public static void remove(ServerWorld world, BlockPos pos) {
        Saved s = saved(world);
        if (!s.positions.remove(pos.asLong())) return;
        s.markDirty();
        world.getChunkManager().getLightingProvider().checkBlock(pos);
        send(world, (byte) 2, new long[]{pos.asLong()});
    }

    /** A piston or a fall carried a glowing block from one place to another. */
    public static void moved(ServerWorld world, BlockPos from, BlockPos to) {
        remove(world, from);
        add(world, to);
    }

    /** Whether a block becoming this means it's gone (so its glow goes with it). */
    public static boolean gone(BlockState newState) {
        return newState.isAir() || newState.getBlock() instanceof FluidBlock;
    }

    // ------------------------------------------------------------------ telling the game

    private static PacketByteBuf packet(byte op, long[] positions) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeByte(op);              // 0 = everything in this world, 1 = add, 2 = remove
        buf.writeLongArray(positions);
        return buf;
    }

    private static void send(ServerWorld world, byte op, long[] positions) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (ServerPlayNetworking.canSend(p, SYNC)) ServerPlayNetworking.send(p, SYNC, packet(op, positions));
        }
    }

    private static void sendAll(ServerPlayerEntity p) {
        if (!ServerPlayNetworking.canSend(p, SYNC)) return;
        Saved s = saved(p.getServerWorld());
        long[] all = new long[s.positions.size()];
        int i = 0;
        for (Long l : s.positions) if (i < all.length) all[i++] = l;
        ServerPlayNetworking.send(p, SYNC, packet((byte) 0, i == all.length ? all : java.util.Arrays.copyOf(all, i)));
    }

    // ------------------------------------------------------------------ setting up

    public static void register() {
        ServerWorldEvents.LOAD.register((server, world) -> {
            Saved s = saved(world);
            SERVER.put(world.getRegistryKey(), s.positions);
        });
        ServerWorldEvents.UNLOAD.register((server, world) -> SERVER.remove(world.getRegistryKey()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendAll(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, dest) -> sendAll(player));
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> sendAll(newPlayer));

        // Right-click any block with glowstone dust to make it glow (sneak for blocks that
        // do something when clicked, like chests and doors).
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.GLOWSTONE_DUST) || player.isSpectator()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof FluidBlock || state.isOf(Gunpowder.TRAIL)) return ActionResult.PASS;
            if (!player.isSneaking()) {
                ActionResult r = state.onUse(world, player, hand, hit);
                if (r.isAccepted()) return r;
            }
            if (world.isClient) return CLIENT.contains(pos.asLong()) ? ActionResult.PASS : ActionResult.SUCCESS;
            ServerWorld sw = (ServerWorld) world;
            if (!add(sw, pos)) return ActionResult.PASS;     // already glowing: the dust isn't used
            if (!player.getAbilities().creativeMode) stack.decrement(1);
            sw.playSound(null, pos, SoundEvents.ITEM_GLOW_INK_SAC_USE, SoundCategory.BLOCKS, 1.0F, 1.2F);
            sw.spawnParticles(SPARKLE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 14, 0.35, 0.35, 0.35, 0.0);
            return ActionResult.SUCCESS;
        });
    }
}
