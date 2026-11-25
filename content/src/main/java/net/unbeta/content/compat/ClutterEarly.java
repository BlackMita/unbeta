package net.unbeta.content.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.unbeta.content.UnbetaContent;

/**
 * Has Clutter No More add its vertical slabs and steps early enough. Clutter waits for a moment
 * that newer Fabric API puts off until a world starts: too late on a server (the block list has
 * closed: crash) and too late in your game (its pictures and names are made before the title
 * screen, from blocks it hasn't added yet: missing textures). So Unbeta has it add them at the
 * end of Unbeta's own start-up, the same moment on server and game so the two always match.
 * (Two later moments remain as fallbacks: the block list about to close, and the game first
 * loading its resources.) Once only; Clutter's own late go is skipped (ClutterOnceMixin).
 */
public final class ClutterEarly {

    private ClutterEarly() {}

    private static boolean done;

    public static synchronized void run(String when) {
        if (done) return;
        done = true;
        if (!FabricLoader.getInstance().isModLoaded("clutternomore")) return;
        try {
            Class.forName("dev.tazer.clutternomore.registry.ShapeBlocks").getMethod("registerVariants").invoke(null);
            UnbetaContent.LOG.info("Clutter No More shapes added early ({}).", when);
        } catch (Throwable t) {
            UnbetaContent.LOG.error("Couldn't have Clutter No More add its shapes early", t);
        }
    }
}
