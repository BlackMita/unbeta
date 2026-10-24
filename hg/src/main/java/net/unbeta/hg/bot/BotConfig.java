package net.unbeta.hg.bot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.unbeta.hg.UnbetaHG;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The bots' tuning file, config/unbeta-hg-bots.json: every number that shapes how bots behave,
 * so they can be retuned without code (change a number, restart the server). Missing numbers
 * take their defaults; the file is rewritten with all of them on every start.
 */
public final class BotConfig {

    /** The skill dial: 1 = normal. Higher turns faster and (later) aims and reacts better; lower is clumsier. */
    public double skill = 1.0;
    /** Fastest head turn, in degrees per tick, at skill 1. */
    public double turnDegreesPerTick = 24.0;
    /** How far ahead a bot plans a walk at once, in blocks (longer walks go in legs). */
    public int pathLegBlocks = 32;
    /** How far a bot can reach to break or place, in blocks. */
    public double reach = 4.5;
    /** Ticks to staged-craft one recipe (20 ticks = 1 second). */
    public int craftTicks = 20;
    /** A walk with no progress for this many ticks is replanned; after 5 replans the bot gives up. */
    public int stuckTicks = 60;
    /** Sprint when the walk has more than this many blocks to go. */
    public double sprintOver = 8.0;

    // ---- Brains (Phase 2) ----
    /** A bot looks and thinks every this many ticks (5 = four times a second), staggered between bots. */
    public int thinkTicks = 5;
    /** How far it sees living things, in blocks; in the dark (light 4 or less, not burning) only this far. */
    public double sightRange = 32.0;
    public double darkSightRange = 12.0;
    /** Its view cone, in degrees; narrower while its eyes are busy (mining, crafting, fighting). */
    public double sightConeDegrees = 140.0;
    public double busyConeDegrees = 90.0;
    /** Glance rays for noticing blocks: how many a second, and how far. */
    public int glanceRaysPerSecond = 60;
    public double glanceRange = 24.0;
    /** At most this many new things taken in per look. */
    public int newThingsPerLook = 3;
    /** Win Methods: Reaction length multipliers (for set-length Reactions). */
    public double lowerLength = 1.75;
    public double middlingLength = 0.5;
    public double higherLength = 1.0;
    /** Middling: how many Reactions it queues. */
    public int middlingQueue = 2;
    /** Middling contagion: how often it checks the people it sees (ticks), and the chance to copy each. */
    public int contagionTicks = 40;
    public double contagionChance = 0.35;
    /** Lower rage: interrupted 3 times within this many ticks, it locks on for this many. */
    public int rageWindowTicks = 400;
    public int rageTicks = 1200;
    /** Higher wears off: the same Reaction 3 times running, and it's ignored this many ticks. */
    public int wearsOffTicks = 1200;
    /** Health (half-hearts) at which each Win Method retreats. */
    public double lowerRetreatHealth = 6.0;
    public double middlingRetreatHealth = 10.0;
    public double higherRetreatHealth = 8.0;
    /** Chance to water-drop a long fall, by Win Method. */
    public double lowerWaterDrop = 0.1;
    public double middlingWaterDrop = 0.4;
    public double higherWaterDrop = 0.8;
    /** After a Reaction gives up, it isn't tried again for this many ticks; after one succeeds, this many. */
    public int failCooldownTicks = 300;
    public int doneCooldownTicks = 40;

    // ---- Fighting, Player Methods, flavour (steps 4 to 6) ----
    /** Chance a swing misses, by Win Method (divided by skill). */
    public double lowerMiss = 0.25;
    public double middlingMiss = 0.15;
    public double higherMiss = 0.08;
    /** Middling buys a keep once this many of its 36 slots are full. */
    public int middlingBuyAtSlots = 24;
    /** Hog: longest it guards a pile (ticks). */
    public int hogGuardTicks = 1200;
    /** Tortoise turns Terminator with this many ticks left on the round clock (15 minutes). */
    public int terminatorTicksLeft = 18000;
    /** N16: chance a bot hides for a night, by Win Method (doubled with no weapon; always after a night death to monsters). */
    public double lowerHideChance = 0.05;
    public double middlingHideChance = 0.12;
    public double higherHideChance = 0.2;
    /** Chance a chat-worthy moment (iron, a kill, a death) gets a line in chat. */
    public double chatChance = 0.25;

    private static BotConfig current = new BotConfig();

    public static BotConfig get() {
        return current;
    }

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("unbeta-hg-bots.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (Files.exists(file)) {
                BotConfig read = gson.fromJson(Files.readString(file), BotConfig.class);
                if (read != null) current = read;
            }
            Files.writeString(file, gson.toJson(current));
        } catch (Exception e) {
            UnbetaHG.LOG.error("Couldn't read {} - using the defaults", file, e);
        }
    }

    public float turn() {
        return (float) (turnDegreesPerTick * skill);
    }
}
