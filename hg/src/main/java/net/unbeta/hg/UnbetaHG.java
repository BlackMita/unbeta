package net.unbeta.hg;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unbeta Hunger Games: a free-for-all server mode played in rounds, each on a fresh world.
 * Install this jar only on a server meant to run the mode. Design: the "Unbeta Hunger Games:
 * Design" doc.
 *
 * <p>Stage 3: keeps (the Keep screen on the K key). Stage 2: hearts, being out, surrender,
 * leaving counts as a death, winning. Stage 1: the round loop (lobby, countdown, grace, 45-minute game, end, restart into a new
 * world), the restricted mode, double-speed days, the world border, and dummy players for
 * testing.
 */
public final class UnbetaHG implements ModInitializer {

    public static final String MOD_ID = "unbeta-hg";
    public static final Logger LOG = LoggerFactory.getLogger("UnbetaHG");
    /**
     * The mode only runs on a dedicated server. The same jar on a player's game just adds the
     * Keep screen, and leaves single player alone.
     */
    public static final boolean ACTIVE = FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;

    @Override
    public void onInitialize() {
        Border.registerBlock();   // on both sides: players' games draw the border switch
        if (!ACTIVE) return;
        Round.register();
        Keeps.register();
        OutPlayers.register();
        // A zombified player's revenant leaves their keeps with them.
        net.unbeta.content.corruption.RevenantEntity.KEPT = (player, stack) -> Keeps.keptThroughDeath(player, stack.getItem());
        // Obsidian Doors open for 3 levels on this server.
        net.unbeta.content.obsidiandoor.ObsidianDoorBlock.XP_LEVELS = 3;
        net.unbeta.content.obsidiandoor.ObsidianDoorBlock.XP_COST = net.unbeta.content.obsidiandoor.XpHelper.pointsForLevel(3);
        Restrictions.register();
        Cage.register();
        Dummies.register();
        HGCommands.register();
        Perf.register();
        net.unbeta.hg.bot.BotConfig.load();
        net.unbeta.hg.bot.BotCommands.register();
        net.unbeta.hg.bot.Hearing.register();
        LOG.info("Unbeta Hunger Games loaded: this server runs rounds.");
    }
}
