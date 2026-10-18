package net.unbeta.content.skyhold;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.util.math.BlockPos;

/**
 * No Skyhold within 300 blocks of spawn. Skyholds are laid out while chunks generate, where the
 * world's spawn point can't be looked up, so this keeps them away from two places: the world's
 * centre (0, 0) - where a new world's spawn is searched for - and the actual spawn point, known
 * once the server has started. Measured from the Skyhold's centre (its main island).
 */
public final class SkyholdSpawnGuard {

    public static final int MIN_DISTANCE = 300;

    private static volatile int spawnX = 0, spawnZ = 0;

    private SkyholdSpawnGuard() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> { spawnX = 0; spawnZ = 0; });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BlockPos spawn = server.getOverworld().getSpawnPos();
            spawnX = spawn.getX();
            spawnZ = spawn.getZ();
        });
    }

    public static boolean tooClose(int x, int z) {
        long min = (long) MIN_DISTANCE * MIN_DISTANCE;
        return sq(x, z) < min || sq(x - spawnX, z - spawnZ) < min;
    }

    private static long sq(long dx, long dz) {
        return dx * dx + dz * dz;
    }
}
