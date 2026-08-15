package net.unbeta.content.boomspore;

import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.CreeperEntity;

/**
 * Adds a synced boolean to CreeperEntity: "has this creeper planted a boom spore?"
 * Used by the renderer (client) to swap to the grinning texture, and by the
 * CreeperStalkGoal (server) to track flee state.
 *
 * <p>Registered via a mixin that calls {@link #register(CreeperEntity)} from the
 * creeper's constructor.
 */
public final class CreeperDataTracker {

    public static final TrackedData<Boolean> HAS_PLANTED =
            DataTracker.registerData(CreeperEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private CreeperDataTracker() {}

    /** Called from the creeper constructor mixin to set the default value. */
    public static void init(DataTracker tracker) {
        tracker.startTracking(HAS_PLANTED, false);
    }
}
