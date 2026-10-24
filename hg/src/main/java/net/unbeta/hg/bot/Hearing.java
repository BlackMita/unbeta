package net.unbeta.hg.bot;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.unbeta.hg.DummyPlayer;

import java.util.Random;

/**
 * Bots' ears, and the news they get from deaths. Sounds reach every bot in range, through walls,
 * but vaguely: a direction and a distance, give or take 4 blocks. Hits and hurt sounds carry 16
 * blocks, blocks breaking 12, doors and chests 10.
 */
public final class Hearing {

    private Hearing() {}

    private static final Random RANDOM = new Random();

    /** Something heard: what, roughly where, when. */
    public record Sound(String what, Vec3d at, long when) {}

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.getWorld() instanceof ServerWorld w) noise(w, entity.getPos(), "a fight", 16, entity);
            return true;
        });
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world instanceof ServerWorld w) noise(w, Vec3d.ofCenter(pos), "digging", 12, player);
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerWorld w) {
                BlockState st = w.getBlockState(hit.getBlockPos());
                if (st.isIn(BlockTags.DOORS) || st.isOf(Blocks.CHEST) || st.isOf(Blocks.TRAPPED_CHEST) || st.isOf(Blocks.BARREL)) {
                    noise(w, Vec3d.ofCenter(hit.getBlockPos()), "a door or chest", 10, player);
                }
            }
            return ActionResult.PASS;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register(Hearing::died);
    }

    private static void noise(ServerWorld world, Vec3d at, String what, double range, Entity source) {
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (!(p instanceof DummyPlayer d) || d.body == null || p == source) continue;
            if (p.squaredDistanceTo(at) > range * range) continue;
            Vec3d fuzzy = at.add((RANDOM.nextDouble() - 0.5) * 8, 0, (RANDOM.nextDouble() - 0.5) * 8);
            d.body.brain.hear(new Sound(what, fuzzy, world.getTime()));
        }
    }

    /** A death: the killer bot hears of its kill; bots that fought the dead remember the pile. */
    private static void died(LivingEntity dead, net.minecraft.entity.damage.DamageSource source) {
        if (!(dead.getWorld() instanceof ServerWorld world)) return;
        if (source.getAttacker() instanceof DummyPlayer killer && killer.body != null) killer.body.brain.onKill(dead);
        if (!(dead instanceof PlayerEntity)) return;
        BlockPos at = dead.getBlockPos();
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p instanceof DummyPlayer d && d.body != null && d.body.brain.notebook.fought.containsKey(dead.getUuid())) {
                d.body.brain.notebook.piles.put(at, world.getTime());
            }
        }
    }
}
