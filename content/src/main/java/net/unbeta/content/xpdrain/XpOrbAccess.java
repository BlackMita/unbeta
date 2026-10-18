package net.unbeta.content.xpdrain;

/** Added to experience orbs by XpOrbClaimMixin. */
public interface XpOrbAccess {

    /** Claims this orb for a sink: it bursts outward for burstTicks, then homes in at speed. */
    void unbeta$claim(XpDrain.Sink sink, int burstTicks, double speed);

    boolean unbeta$isClaimed();
}
