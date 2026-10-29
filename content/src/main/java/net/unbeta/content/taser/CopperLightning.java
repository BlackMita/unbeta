package net.unbeta.content.taser;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lightning striking copper that's joined to more copper (a lightning rod on a copper block, or a
 * copper block touching others) runs through all of it. Everything touching that copper takes a
 * lightning strike's damage, and is thrown away from it twice as hard as a zombie cow's charge.
 * The taser's zap sounds once.
 */
public final class CopperLightning {

    private CopperLightning() {}

    private static final int MAX_COPPER = 512;
    private static final float DAMAGE = 5.0F;        // what a lightning strike does
    private static final double THROW = 5.0;         // a zombie cow's charge is 2.5

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof LightningEntity bolt) strike(world, bolt);
        });
    }

    private static void strike(ServerWorld world, LightningEntity bolt) {
        BlockPos hit = BlockPos.ofFloored(bolt.getPos().subtract(0, 1.0E-6, 0));
        if (!world.getBlockState(hit).isIn(Taser.CONDUCTS)) return;
        Set<BlockPos> copper = joined(world, hit);
        if (copper.size() < 2) return;      // a lone copper block or a rod on nothing: just lightning

        Set<LivingEntity> shocked = new HashSet<>();
        List<BlockPos> touchedBy = new ArrayList<>();
        for (BlockPos p : copper) {
            for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(p).expand(0.05),
                    e -> e.isAlive() && !e.isSpectator() && !(e instanceof PlayerEntity pl && pl.isCreative()))) {
                if (shocked.add(e)) touchedBy.add(p);
            }
        }
        world.playSound(null, hit, Taser.BUZZ, SoundCategory.WEATHER, 3.0F, 0.8F);
        int i = 0;
        for (LivingEntity e : shocked) {
            BlockPos from = touchedBy.get(i++);
            e.damage(world.getDamageSources().lightningBolt(), DAMAGE);
            Vec3d c = Vec3d.ofCenter(from);
            double dx = c.x - e.getX(), dz = c.z - e.getZ();
            if (dx * dx + dz * dz < 1.0E-4) {
                double a = world.random.nextDouble() * Math.PI * 2;
                dx = Math.cos(a);
                dz = Math.sin(a);
            }
            e.takeKnockback(THROW, dx, dz);
            e.velocityModified = true;
        }
    }

    /** Every copper block (and lightning rod) joined face to face with the one struck. */
    private static Set<BlockPos> joined(ServerWorld world, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        seen.add(start);
        q.add(start);
        while (!q.isEmpty() && seen.size() < MAX_COPPER) {
            BlockPos p = q.poll();
            for (Direction d : Direction.values()) {
                BlockPos n = p.offset(d);
                if (!seen.contains(n) && world.getBlockState(n).isIn(Taser.CONDUCTS)) {
                    seen.add(n);
                    q.add(n);
                }
            }
        }
        return seen;
    }
}
