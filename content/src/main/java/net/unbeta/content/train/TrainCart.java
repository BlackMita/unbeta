package net.unbeta.content.train;

import java.util.UUID;

/**
 * What every minecart carries for trains and jumping (added to minecarts by TrainCartMixin).
 * Each cart has two chain ends (slots 0 and 1); a slot holds the linked cart and the chain type.
 */
public interface TrainCart {

    int NONE = 0, IRON = 1, COPPER = 2;

    UUID unbeta_link(int slot);

    int unbeta_linkType(int slot);

    void unbeta_setLink(int slot, UUID other, int type);

    /** For the client: the linked cart's entity id * 4 + chain type, or 0. */
    int unbeta_linkData(int slot);

    void unbeta_setLinkData(int slot, int data);

    /** For the client: the player holding this cart by a chain (their entity id * 4 + chain type), or 0. */
    int unbeta_heldData();

    void unbeta_setHeldData(int data);

    int unbeta_missing(int slot);

    void unbeta_setMissing(int slot, int ticks);

    boolean unbeta_onRail();

    /** In the air after a jump: rails are ignored until it lands. */
    boolean unbeta_airborne();

    void unbeta_setAirborne(boolean airborne);

    /** Landed badly on a rail: stays off the track until it leaves the rail or stops. */
    boolean unbeta_derailed();

    void unbeta_setDerailed(boolean derailed);

    /** Ticks left of being dragged along by a copper chain (lets a furnace cart exceed its own top speed). */
    int unbeta_dragged();

    void unbeta_setDragged(int ticks);

    default boolean unbeta_hasLinks() {
        return unbeta_link(0) != null || unbeta_link(1) != null;
    }

    default int unbeta_slotOf(UUID other) {
        if (other == null) return -1;
        if (other.equals(unbeta_link(0))) return 0;
        if (other.equals(unbeta_link(1))) return 1;
        return -1;
    }

    default int unbeta_freeSlot() {
        return unbeta_link(0) == null ? 0 : unbeta_link(1) == null ? 1 : -1;
    }
}
