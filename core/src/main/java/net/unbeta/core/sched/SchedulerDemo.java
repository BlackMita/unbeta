package net.unbeta.core.sched;

import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.unbeta.core.UnbetaCore;

/**
 * Throwaway demo handlers used by {@code /unbeta sched} to prove the scheduler works
 * end-to-end (including across a save/quit/reload) before real features depend on it.
 *
 * <p>Safe to delete once torches and creeper bombs are implemented.
 */
public final class SchedulerDemo {

    public static final String POS_HANDLER = "unbeta:demo_position";
    public static final String ITEM_HANDLER = "unbeta:demo_item";

    private SchedulerDemo() {}

    public static void register() {
        UnbetaScheduler.registerPositionHandler(POS_HANDLER, (world, pos, payload) -> {
            world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(),
                    SoundCategory.BLOCKS, 1.0F, 1.0F);
            world.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    30, 0.5, 0.5, 0.5, 0.0);
            UnbetaCore.LOG.info("[sched demo] position task fired at {}", pos);
        });

        UnbetaScheduler.registerItemHandler(ITEM_HANDLER, (world, stack, holder, pos) -> {
            world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE,
                    SoundCategory.BLOCKS, 0.7F, 1.4F);
            world.spawnParticles(ParticleTypes.FLAME,
                    pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5,
                    40, 0.4, 0.4, 0.4, 0.02);
            String where = (holder != null) ? ("held by " + holder.getName().getString())
                                                        : ("container at " + pos);
            UnbetaCore.LOG.info("[sched demo] item countdown expired ({}), stack={}", where, stack);
            return false; // don't consume the stack, so we can watch it repeatedly
        });
    }
}
