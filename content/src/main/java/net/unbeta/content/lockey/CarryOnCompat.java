package net.unbeta.content.lockey;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.function.BiFunction;

/**
 * Lockey x Carry On. Everything here is reached by reflection, so Unbeta neither needs
 * Carry On to build nor to run - if it's absent (or its internals change), locked chests
 * simply go back to being uncarryable.
 *
 * <p>Carry On keeps a carried chest as saved data under "tile" and rebuilds the chest
 * from it on placement. CarryOnPickupMixin tucks the lock's key id into that data
 * (LOCK_KEY); ChestBlockEntityLockCarryMixin + BlockEntityCarriedLockMixin re-lock the
 * rebuilt chest wherever it's set down.
 */
public final class CarryOnCompat {

    public static final String LOCK_KEY = "UnbetaLockey";

    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("carryon");
    private static Method getCarryData, getNbt, isKeyPressed, tryPickUpBlock;
    private static boolean broken;

        /** True while Carry On is deciding whether a pickup is allowed (it asks via the block-break event). */
    private static final ThreadLocal<Boolean> PICKING_UP = ThreadLocal.withInitial(() -> false);
    /** When each player last set something down with Carry On (server time). */
    private static final java.util.Map<UUID, Long> PLACED_AT = new java.util.concurrent.ConcurrentHashMap<>();
    /** Ticks after a placement during which clicking a container won't open it. */
    private static final long PLACE_GRACE = 10;

    private CarryOnCompat() {}

    public static void setPickingUp(boolean value) { PICKING_UP.set(value); }

    public static boolean isPickingUp() { return PICKING_UP.get(); }

    public static void notePlaced(ServerPlayerEntity player) {
        PLACED_AT.put(player.getUuid(), player.getWorld().getTime());
    }

    /**
     * Carry On shuts any screen that opens while the client still thinks it's carrying - but
     * only on the client. A held right-click repeats onto the chest just placed, the server
     * opens it, the client silently closes it, and the lid is stuck open. So for half a
     * second after a placement, clicking a container doesn't open it.
     */
    public static void register() {
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClient) return net.minecraft.util.ActionResult.PASS;
            Long at = PLACED_AT.get(player.getUuid());
            if (at == null || world.getTime() - at > PLACE_GRACE) return net.minecraft.util.ActionResult.PASS;
            if (world.getBlockState(hit.getBlockPos()).createScreenHandlerFactory(world, hit.getBlockPos()) == null)
                return net.minecraft.util.ActionResult.PASS;
            return net.minecraft.util.ActionResult.FAIL;
        });
    }

    private static synchronized boolean ready() {
        if (!LOADED || broken) return false;
        if (tryPickUpBlock != null) return true;
        try {
            Class<?> manager = Class.forName("tschipp.carryon.common.carry.CarryOnDataManager");
            Class<?> data = Class.forName("tschipp.carryon.common.carry.CarryOnData");
            Class<?> pickup = Class.forName("tschipp.carryon.common.carry.PickupHandler");
            getCarryData = manager.getMethod("getCarryData", PlayerEntity.class);
            getNbt = data.getMethod("getNbt");
            isKeyPressed = data.getMethod("isKeyPressed");
            tryPickUpBlock = pickup.getMethod("tryPickUpBlock",
                    ServerPlayerEntity.class, BlockPos.class, World.class, BiFunction.class);
            return true;
        } catch (Throwable t) {
            broken = true;
            net.unbeta.content.UnbetaContent.LOG.warn("Carry On is installed but its internals changed - locked chests can't be carried.", t);
            return false;
        }
    }

    /**
     * The player is making Carry On's pick-up gesture on a locked chest: hand the click to
     * Carry On directly, so it can't matter whose click handler happens to run first.
     * True if Carry On picked it up.
     */
    public static boolean tryCarry(PlayerEntity player, ServerWorld world, BlockPos pos) {
        if (!(player instanceof ServerPlayerEntity sp) || !ready()) return false;
        if (!sp.getMainHandStack().isEmpty() || !sp.getOffHandStack().isEmpty()) return false;
        try {
            Object data = getCarryData.invoke(null, sp);
            if (!(Boolean) isKeyPressed.invoke(data)) return false;
            BiFunction<BlockPos, BlockState, Boolean> allow = (p, s) -> Boolean.TRUE;
            return (Boolean) tryPickUpBlock.invoke(null, sp, pos, world, allow);
        } catch (Throwable t) {
            return false;
        }
    }

    /** The online player currently carrying the chest locked by this key, or null. */
    public static PlayerEntity carrierOf(MinecraftServer server, UUID lockId) {
        if (server == null || lockId == null || !ready()) return null;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            try {
                NbtCompound nbt = (NbtCompound) getNbt.invoke(getCarryData.invoke(null, p));
                NbtCompound tile = nbt.getCompound("tile");
                if (tile.containsUuid(LOCK_KEY) && lockId.equals(tile.getUuid(LOCK_KEY))) return p;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }
}
