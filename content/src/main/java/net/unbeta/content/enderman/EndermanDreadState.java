package net.unbeta.content.enderman;

/**
 * Client-side flag, reset every tick: true only when EndermanDreadMixin is the one
 * driving nauseaIntensity upward this tick, as opposed to a real portal or a real
 * Nausea potion effect doing so.
 *
 * <p>Read by PortalOverlaySuppressMixin to hide the swirly portal texture during the
 * enderman effect specifically, while leaving a genuine portal's overlay untouched.
 * The view-wobble distortion itself is NOT affected by this flag at all - it lives in
 * GameRenderer's projection code, which reads nauseaIntensity directly and has no idea
 * this flag exists.
 */
public final class EndermanDreadState {
    public static boolean active = false;
    private EndermanDreadState() {}
}
