package net.unbeta.content.lockey;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.unbeta.content.jackolantern.UnbetaJackOLanternBlock;
import net.unbeta.content.jackolantern.UnbetaLitJackOLanternBlock;
import net.unbeta.content.obsidiandoor.ObsidianDoorBlock;
import net.unbeta.core.state.UnbetaWorldState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lockey on doors.
 *
 * <ul>
 *   <li>Right-click a CLOSED door with an unbound Lockey to lock it (both halves, and the
 *       other door of a double door). Right-click with its own key to unlock it. An open door
 *       can't be locked.</li>
 *   <li>A locked door won't open for anyone - hands, redstone, mobs or other mods.</li>
 *   <li>Forcing it: each time a locked door is mined it gives up one piece of itself and stands -
 *       a plank (wood), an iron nugget (iron) or an obsidian block (obsidian); the 6th destroys it.</li>
 *   <li>That damage is the door's for good: it survives unlocking and relocking, goes with the door
 *       when it is picked up (shown as a bar; damaged doors don't stack) and comes back when it is
 *       placed. Right-click a placed damaged door with its material (planks, an iron nugget or
 *       ingot, obsidian) to mend one sixth.</li>
 *   <li>While locked, the door makes the 5 x 6 x 5 block space around it (2 blocks to each side,
 *       2 below to 2 above) indestructible: nothing there can be broken, blown up, burned, moved
 *       by pistons or taken by endermen. New blocks can still be placed into empty space.
 *       Creative mode is exempt.</li>
 * </ul>
 *
 * <p>The client is sent every locked or damaged door, so it never swings a door the server
 * will keep shut (see LockeyDoorsClient).
 *
 * <p>Locks live in two places: "lockey" (both halves, alongside chests, so the key's
 * where-is-my-lock lookup finds doors too) and "lockey_doors" (lower half only, the index the
 * protected zones are built from).
 */
public final class LockeyDoors {

    private static final String DOORS = "lockey_doors";
    private static final String HITS = "lockey_door_hits";      // lower-half pos -> pieces given up (0-5)
    public static final String ITEM_DAMAGE = "UnbetaDoorDamage";   // the same count, on a door item
    public static final Identifier SYNC = new Identifier("unbeta-content", "door_locks");
    public static final int RELEASES = 6;
    private static final int SIDE = 2, BELOW = 2, ABOVE = 3;   // zone around the LOWER half: 5 x 6 x 5

    /** Locked doors' lower halves per dimension, rebuilt whenever a lock changes. */
    private static final Map<RegistryKey<World>, long[]> CACHE = new ConcurrentHashMap<>();
    /** Set while a creative player breaks a block, or while we destroy a door ourselves. */
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private LockeyDoors() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> CACHE.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CACHE.clear());
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
                (handler, sender, server) -> send(handler.player));
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> send(player));
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
                (oldPlayer, newPlayer, alive) -> send(newPlayer));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient || !(world instanceof ServerWorld sw)) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof DoorBlock)) return ActionResult.PASS;
            return onUse(sw, player, player.getStackInHand(hand), lowerHalf(pos, state));
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (!(world instanceof ServerWorld sw)) return true;
            if (player.isCreative()) {
                BYPASS.set(true);
                return true;
            }
            if (state.getBlock() instanceof DoorBlock) {
                BlockPos lower = lowerHalf(pos, state);
                if (lockedBy(sw, lower) != null) return forceDoor(sw, player, lower, sw.getBlockState(lower));
            }
            if (isProtected(sw, pos)) {
                world.playSound(null, pos, LockeyRegistry.CHEST_DENY, SoundCategory.BLOCKS, 0.4F, 1.4F);
                return false;
            }
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            BYPASS.set(false);
            // A locked door broken anyway (creative): release its lock, retire its key.
            if (world instanceof ServerWorld sw && state.getBlock() instanceof DoorBlock) {
                BlockPos lower = lowerHalf(pos, state);
                UUID owner = lockedBy(sw, lower);
                if (owner != null) {
                    unlockDoor(sw, lower);
                    retireIfUnused(sw, owner);
                }
                if (player.isCreative()) setDamage(sw, lower, 0);
            }
        });
        PlayerBlockBreakEvents.CANCELED.register((world, player, pos, state, be) -> BYPASS.set(false));
    }

    // ------------------------------------------------------------------ using doors

    private static ActionResult onUse(ServerWorld world, PlayerEntity player, ItemStack held, BlockPos lower) {
        BlockState state = world.getBlockState(lower);
        if (!(state.getBlock() instanceof DoorBlock)) return ActionResult.PASS;
        UUID owner = lockedBy(world, lower);

        // Mending a damaged door with its own material - locked or not.
        int damage = damage(world, lower);
        if (damage > 0 && healsDoor(state.getBlock(), held)) {
            setDamage(world, lower, damage - 1);
            if (!player.getAbilities().creativeMode) held.decrement(1);
            world.playSound(null, lower, state.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0F, 0.9F);
            resync(player, world, lower);
            return ActionResult.SUCCESS;
        }

        if (LockeyItem.isLockey(held)) {
            UUID mine = LockeyItem.getOrCreateId(held);
            if (owner == null) {
                if (LockeyItem.isBound(held)) return ActionResult.PASS;   // a key already in use: just a door
                if (state.get(DoorBlock.OPEN)) {
                    world.playSound(null, lower, LockeyRegistry.CHEST_DENY, SoundCategory.BLOCKS, 0.8F, 1.0F);
                    player.sendMessage(Text.literal("Close the door first.").formatted(Formatting.GOLD), true);
                    return ActionResult.SUCCESS;
                }
                lockDoor(world, lower, mine);
                BlockPos partner = partner(world, lower, state);
                if (partner != null && !world.getBlockState(partner).get(DoorBlock.OPEN) && lockedBy(world, partner) == null) {
                    lockDoor(world, partner, mine);
                }
                LockeyItem.bind(held, lower);
                world.playSound(null, lower, LockeyRegistry.CHEST_LOCKED, SoundCategory.BLOCKS, 0.8F, 1.0F);
                resync(player, world, lower);
                return ActionResult.SUCCESS;
            }
            if (owner.equals(mine)) {
                unlockDoor(world, lower);
                BlockPos partner = partner(world, lower, state);
                if (partner != null && mine.equals(lockedBy(world, partner))) unlockDoor(world, partner);
                LockeyItem.unbind(held);
                world.playSound(null, lower, LockeyRegistry.CHEST_UNLOCKED, SoundCategory.BLOCKS, 0.8F, 1.0F);
                resync(player, world, lower);
                return ActionResult.SUCCESS;
            }
        }
        if (owner == null) return ActionResult.PASS;

        // Locked, and not by this key: stays shut.
        world.playSound(null, lower, LockeyRegistry.CHEST_DENY, SoundCategory.BLOCKS, 0.8F, 1.0F);
        player.sendMessage(whereIsKey(world, owner), false);
        resync(player, world, lower);
        return ActionResult.SUCCESS;
    }

    /** The client may already have swung the door open; show it the real (shut) door. */
    private static void resync(PlayerEntity player, ServerWorld world, BlockPos lower) {
        if (!(player instanceof ServerPlayerEntity sp)) return;
        sp.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, lower));
        sp.networkHandler.sendPacket(new BlockUpdateS2CPacket(world, lower.up()));
    }

    private static Text whereIsKey(ServerWorld world, UUID owner) {
        BlockPos keyPos = LockeyLocator.find(world, owner);
        if (keyPos != null) {
            return Text.literal("Key is at " + keyPos.getX() + ", " + keyPos.getY() + ", " + keyPos.getZ())
                    .formatted(Formatting.GOLD);
        }
        if (LockeyState.isRevoked(world, owner)) return Text.literal("Will never unlock.").formatted(Formatting.RED);
        BlockPos seen = LockeyState.lastSeen(world, owner);
        if (seen != null) {
            return Text.literal("Key last seen at " + seen.getX() + ", " + seen.getY() + ", " + seen.getZ())
                    .formatted(Formatting.GOLD);
        }
        return Text.literal("Key location unknown.").formatted(Formatting.GOLD);
    }

    // ------------------------------------------------------------------ forcing doors

    /** One "break" of a locked door. Always cancels the real break; returns false. */
    private static boolean forceDoor(ServerWorld world, PlayerEntity player, BlockPos lower, BlockState state) {
        Block block = state.getBlock();
        int released = damage(world, lower) + 1;
        ItemScatterer.spawn(world, lower.getX() + 0.5, lower.getY() + 1.0, lower.getZ() + 0.5, new ItemStack(releasedItem(block)));
        world.playSound(null, lower, net.minecraft.sound.SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.5F, 1.4F);

        if (released < RELEASES) {
            setDamage(world, lower, released);
            return false;
        }
        UUID owner = lockedBy(world, lower);
        unlockDoor(world, lower);
        setDamage(world, lower, 0);
        if (owner != null) retireIfUnused(world, owner);
        BYPASS.set(true);
        try {
            world.breakBlock(lower, false, player);
        } finally {
            BYPASS.set(false);
        }
        return false;
    }

    /** What a locked door gives up per break: its planks, or iron nuggets for iron. */
    static Item releasedItem(Block door) {
        if (door == Blocks.IRON_DOOR) return Items.IRON_NUGGET;
        if (door instanceof ObsidianDoorBlock) return Items.OBSIDIAN;
        Identifier id = Registries.BLOCK.getId(door);
        String path = id.getPath();
        List<Identifier> tries = new ArrayList<>();
        if (path.endsWith("_door")) tries.add(new Identifier(id.getNamespace(), path.substring(0, path.length() - 5) + "_planks"));
        int cut = path.indexOf('_');
        if (cut > 0) tries.add(new Identifier(id.getNamespace(), path.substring(0, cut) + "_planks"));   // burnt_oak_door -> burnt_planks
        for (Identifier t : tries) {
            if (Registries.ITEM.containsId(t)) return Registries.ITEM.get(t);
        }
        return Items.OAK_PLANKS;
    }

    /** The material that mends this door by one sixth. */
    public static boolean healsDoor(Block door, ItemStack held) {
        if (held.isEmpty()) return false;
        if (door == Blocks.IRON_DOOR) return held.isOf(Items.IRON_NUGGET) || held.isOf(Items.IRON_INGOT);
        return held.isOf(releasedItem(door));
    }

    // ------------------------------------------------------------------ door damage

    public static int damage(ServerWorld world, BlockPos lower) {
        return UnbetaWorldState.read(world, HITS).getInt(Long.toString(lower.asLong()));
    }

    private static void setDamage(ServerWorld world, BlockPos lower, int damage) {
        String k = Long.toString(lower.asLong());
        if (damage <= 0 && !UnbetaWorldState.read(world, HITS).contains(k)) return;
        UnbetaWorldState.edit(world, HITS, s -> {
            if (damage <= 0) s.remove(k);
            else s.putInt(k, Math.min(damage, RELEASES - 1));
        });
        sync(world);
    }

    public static int itemDamage(ItemStack stack) {
        NbtCompound n = stack.getNbt();
        return n == null ? 0 : n.getInt(ITEM_DAMAGE);
    }

    /** A door was just placed from an item: wipe anything left by an old door here, take on the item's damage. */
    public static void onDoorPlaced(ServerWorld world, BlockPos lower, ItemStack stack) {
        UUID stale = lockedBy(world, lower);
        if (stale != null) {
            unlockDoor(world, lower);
            retireIfUnused(world, stale);
        }
        UnbetaWorldState.edit(world, HITS, s -> s.remove(Long.toString(lower.asLong())));
        setDamage(world, lower, Math.min(itemDamage(stack), RELEASES - 1));
        sync(world);
    }

    /** Stamps a door's damage onto the door item it drops (called while its drops are worked out). */
    public static void stampDrops(ServerWorld world, BlockPos lower, Block door, List<ItemStack> drops) {
        int damage = damage(world, lower);
        if (damage <= 0) return;
        for (ItemStack drop : drops) {
            if (drop.isOf(door.asItem())) drop.getOrCreateNbt().putInt(ITEM_DAMAGE, damage);
        }
    }

    /** A door block was replaced by something else: once the dust settles, forget its lock and damage. */
    public static void onDoorGone(ServerWorld world, BlockPos lower) {
        later(world, () -> {
            if (world.getBlockState(lower).getBlock() instanceof DoorBlock) return;   // a door is back
            UUID owner = lockedBy(world, lower);
            if (owner != null) {
                unlockDoor(world, lower);
                retireIfUnused(world, owner);
            }
            setDamage(world, lower, 0);
        });
    }

    /** Runs after the current action has fully finished (its drops included) - next task slot. */
    private static void later(ServerWorld world, Runnable task) {
        net.minecraft.server.MinecraftServer server = world.getServer();
        server.send(new net.minecraft.server.ServerTask(server.getTicks(), task));
    }

    // ------------------------------------------------------------------ client sync

    private static net.minecraft.network.PacketByteBuf packet(ServerWorld world) {
        java.util.Map<Long, Integer> doors = new java.util.HashMap<>();
        NbtCompound hits = UnbetaWorldState.read(world, HITS);
        for (String k : hits.getKeys()) {
            try { doors.merge(Long.parseLong(k), hits.getInt(k), Integer::sum); } catch (NumberFormatException ignored) { }
        }
        for (long l : doors(world)) doors.merge(l, 0x100, Integer::sum);
        net.minecraft.network.PacketByteBuf buf = net.fabricmc.fabric.api.networking.v1.PacketByteBufs.create();
        buf.writeVarInt(doors.size());
        doors.forEach((pos, v) -> {
            buf.writeLong(pos);
            buf.writeVarInt(v);
        });
        return buf;
    }

    private static void send(ServerPlayerEntity player) {
        if (player.getWorld() instanceof ServerWorld sw) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, SYNC, packet(sw));
        }
    }

    private static void sync(ServerWorld world) {
        for (ServerPlayerEntity p : world.getPlayers()) send(p);
    }

    // ------------------------------------------------------------------ lock table

    public static UUID lockedBy(ServerWorld world, BlockPos lower) {
        NbtCompound s = UnbetaWorldState.read(world, DOORS);
        String k = Long.toString(lower.asLong());
        return s.containsUuid(k) ? s.getUuid(k) : null;
    }

    private static void lockDoor(ServerWorld world, BlockPos lower, UUID id) {
        UnbetaWorldState.edit(world, DOORS, s -> s.putUuid(Long.toString(lower.asLong()), id));
        LockeyState.lock(world, lower, id);
        LockeyState.lock(world, lower.up(), id);
        CACHE.remove(world.getRegistryKey());
        sync(world);
    }

    private static void unlockDoor(ServerWorld world, BlockPos lower) {
        UnbetaWorldState.edit(world, DOORS, s -> s.remove(Long.toString(lower.asLong())));
        LockeyState.unlock(world, lower);
        LockeyState.unlock(world, lower.up());
        CACHE.remove(world.getRegistryKey());
        sync(world);
    }

    /** A key whose every door is gone reports "Will never unlock." */
    private static void retireIfUnused(ServerWorld world, UUID owner) {
        NbtCompound locks = UnbetaWorldState.read(world, "lockey");
        for (String k : locks.getKeys()) {
            if (locks.containsUuid(k) && owner.equals(locks.getUuid(k))) return;
        }
        LockeyState.revoke(world, owner);
    }

    public static BlockPos lowerHalf(BlockPos pos, BlockState state) {
        return state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
    }

    /** The other door of a double door: beside it, same facing, opposite hinge (as vanilla pairs them). */
    static BlockPos partner(World world, BlockPos lower, BlockState state) {
        Direction facing = state.get(DoorBlock.FACING);
        DoorHinge hinge = state.get(DoorBlock.HINGE);
        Direction side = hinge == DoorHinge.LEFT ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
        BlockPos at = lower.offset(side);
        BlockState other = world.getBlockState(at);
        if (other.getBlock() instanceof DoorBlock && other.get(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && other.get(DoorBlock.FACING) == facing && other.get(DoorBlock.HINGE) != hinge) return at;
        return null;
    }

    // ------------------------------------------------------------------ the protected zone

    private static long[] doors(ServerWorld world) {
        return CACHE.computeIfAbsent(world.getRegistryKey(), k -> {
            NbtCompound s = UnbetaWorldState.read(world, DOORS);
            List<Long> out = new ArrayList<>();
            for (String key : s.getKeys()) {
                try {
                    out.add(Long.parseLong(key));
                } catch (NumberFormatException ignored) {
                }
            }
            long[] arr = new long[out.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = out.get(i);
            return arr;
        });
    }

    /** True if pos lies in a locked door's zone (and nothing is exempting this change). */
    public static boolean isProtected(ServerWorld world, BlockPos pos) {
        return isNear(world, pos, 0);
    }

    /** As isProtected, with the zones grown by margin blocks on every side. */
    public static boolean isNear(ServerWorld world, BlockPos pos, int margin) {
        if (BYPASS.get() || world.getServer() == null || !world.getServer().isOnThread()) return false;
        long[] doors = doors(world);
        if (doors.length == 0) return false;
        int x = pos.getX(), y = pos.getY(), z = pos.getZ();
        for (long l : doors) {
            int lx = BlockPos.unpackLongX(l), ly = BlockPos.unpackLongY(l), lz = BlockPos.unpackLongZ(l);
            if (Math.abs(x - lx) > SIDE + margin || Math.abs(z - lz) > SIDE + margin) continue;
            if (y < ly - BELOW - margin || y > ly + ABOVE + margin) continue;
            if (doorStillThere(world, l)) return true;
        }
        return false;
    }

    /** A lock whose door vanished some other way (commands, other mods) is cleared. */
    private static boolean doorStillThere(ServerWorld world, long lower) {
        BlockPos pos = BlockPos.fromLong(lower);
        WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return true;   // not loaded: trust the record
        if (chunk.getBlockState(pos).getBlock() instanceof DoorBlock) return true;
        later(world, () -> {
            UUID owner = lockedBy(world, pos);
            if (owner == null || world.getBlockState(pos).getBlock() instanceof DoorBlock) return;
            unlockDoor(world, pos);
            retireIfUnused(world, owner);
            setDamage(world, pos, 0);
        });
        return false;
    }

    /**
     * World.setBlockState guard. Blocks: destroying or replacing a block in a zone (anything
     * but empty space, water and the like), and opening or closing a locked door by any means.
     * Allowed: placing into empty space, and a block changing its own state (crops growing,
     * torches lighting, doors elsewhere in the zone opening).
     */
    public static boolean blocksChange(ServerWorld world, BlockPos pos, BlockState next) {
        if (!isProtected(world, pos)) return false;
        BlockState old = world.getBlockState(pos);
        if (old.getBlock() instanceof DoorBlock && next.getBlock() == old.getBlock()) {
            if (old.get(DoorBlock.OPEN) == next.get(DoorBlock.OPEN)) return false;
            return lockedBy(world, lowerHalf(pos, old)) != null;
        }
        if (old.isAir() || old.isReplaceable()) return false;
        if (old.getBlock() == next.getBlock()) return false;
        if (isJackOLantern(old) && isJackOLantern(next)) return false;   // lit <-> unlit swap
        return true;
    }

    private static boolean isJackOLantern(BlockState s) {
        return s.getBlock() instanceof UnbetaJackOLanternBlock || s.getBlock() instanceof UnbetaLitJackOLanternBlock;
    }
}
