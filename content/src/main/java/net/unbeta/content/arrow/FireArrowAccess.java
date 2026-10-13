package net.unbeta.content.arrow;

/** Implemented on every shot arrow (PersistentProjectileEntity) by FireArrowMixin. */
public interface FireArrowAccess {
    /** Burned out after landing alight: black, cold, and can't be picked up. */
    boolean unbeta$isCharred();
}
