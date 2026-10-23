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
