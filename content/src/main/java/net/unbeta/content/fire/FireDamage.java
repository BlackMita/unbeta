package net.unbeta.content.fire;

/** Everything vanilla counts as fire (burning, fire, lava, magma, fireballs) hits twice as hard. */
public final class FireDamage {

    public static final float MULTIPLIER = 2.0F;
    /** Set while the doubled hit is being dealt, so it isn't doubled again. */
    public static final ThreadLocal<Boolean> DOUBLING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private FireDamage() {}
}
