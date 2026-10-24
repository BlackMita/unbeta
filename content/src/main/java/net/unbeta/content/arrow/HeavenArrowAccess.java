package net.unbeta.content.arrow;

/** Implemented on every ArrowEntity by HeavenArrowMixin: is this a Heaven's Arrow? */
public interface HeavenArrowAccess {
    boolean unbeta$isHeaven();

    void unbeta$setHeaven(boolean heaven);
}
