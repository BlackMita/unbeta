package net.unbeta.content.metal;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.corruption.CorruptedAnimal;

import java.util.ArrayList;
import java.util.List;

/**
 * A Block of Gold wards off the undead: no zombie of any kind (zombies, revenants, Unmasons,
 * zombie livestock) will walk into the 9 x 9 x 9 space around it. One already inside leaves.
 * A zombie chasing you into a ward stops at its edge.
 */
public final class GoldWard {

    private GoldWard() {}

    public static final int REACH = 4;   // 4 blocks each way: 9 x 9 x 9

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(GoldWard::tick);
    }

    public static boolean undead(Entity e) {
        return e instanceof ZombieEntity || e instanceof CorruptedAnimal;
    }

    static Box zone(long pos) {
        BlockPos p = BlockPos.fromLong(pos);
        return new Box(p).expand(REACH);
    }

    private static void tick(ServerWorld world) {
        BlockMemory mem = BlockMemory.get(world);
        if (mem.gold.isEmpty()) return;
        List<Box> zones = new ArrayList<>();
        for (long p : mem.gold) if (world.isChunkLoaded(BlockPos.fromLong(p))) zones.add(zone(p));
        if (zones.isEmpty()) return;
        java.util.Set<MobEntity> near = new java.util.HashSet<>();
        for (Box z : zones) near.addAll(world.getEntitiesByClass(MobEntity.class, z.expand(16), GoldWard::undead));
        for (MobEntity m : near) {
            Box inside = null;
            for (Box z : zones) if (z.intersects(m.getBoundingBox())) inside = z;
            if (inside != null) {
                // Get out: straight away from the gold, at a run.
                Vec3d c = inside.getCenter();
                Vec3d away = new Vec3d(m.getX() - c.x, 0, m.getZ() - c.z);
                if (away.lengthSquared() < 0.01) away = new Vec3d(1, 0, 0);
                away = away.normalize();
                Vec3d to = c.add(away.multiply(REACH + 4));
                if (world.getTime() % 10 == 0) m.getNavigation().startMovingTo(to.x, m.getY(), to.z, 1.25);
                m.setVelocity(m.getVelocity().add(away.multiply(0.06)));
                continue;
            }
            // Outside: never step in. Drop any path that leads in, and stop at the edge.
            Path path = m.getNavigation().getCurrentPath();
            if (path != null) {
                for (int i = path.getCurrentNodeIndex(); i < Math.min(path.getLength(), path.getCurrentNodeIndex() + 6); i++) {
                    PathNode n = path.getNode(i);
                    Box nb = new Box(n.getBlockPos());
                    boolean in = false;
                    for (Box z : zones) if (z.intersects(nb)) in = true;
                    if (in) {
                        m.getNavigation().stop();
                        break;
                    }
                }
            }
            Vec3d v = m.getVelocity();
            Box next = m.getBoundingBox().offset(v.x * 3, 0, v.z * 3);
            for (Box z : zones) {
                if (z.intersects(next)) {
                    m.setVelocity(0, v.y, 0);
                    break;
                }
            }
        }
    }
}
