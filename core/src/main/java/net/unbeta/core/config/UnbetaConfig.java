package net.unbeta.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.unbeta.core.UnbetaCore;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Two files, both under config/unbeta/:
 *
 * <ul>
 *   <li><b>core.json</b> - normal settings plus a rules map, at CORE_CONFIG priority.</li>
 *   <li><b>overrides.json</b> - a bare rules map at USER_OVERRIDE priority, which beats
 *       everything including other mods. Empty by default. This is the escape hatch.</li>
 * </ul>
 *
 * Plain JSON on purpose - no Cloth Config dependency, so core stays loadable even
 * when the rest of the pack is broken.
 */
public final class UnbetaConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public Set<String> gatedNamespaces = new LinkedHashSet<>(Set.of("minecraft"));

    /** If true, gated items are also blocked from being used when obtained by other means. */
    public boolean strictMode = false;

    /** Hide gated content from the creative menu and recipe viewers. */
    public boolean hideFromCreative = true;

    /** Log a line for every gate decision. Extremely noisy; for debugging only. */
    public boolean verboseGateLogging = false;

    /** Rule overrides at CORE_CONFIG priority. Key -> removed. */
    public Map<String, Boolean> rules = new LinkedHashMap<>();

    public static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("unbeta");
    }

    public static UnbetaConfig loadOrCreate() {
        Path file = dir().resolve("core.json");
        UnbetaConfig cfg = new UnbetaConfig();
        try {
            Files.createDirectories(dir());
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    UnbetaConfig read = GSON.fromJson(r, UnbetaConfig.class);
                    if (read != null) cfg = read;
                }
            } else {
                cfg.save();
            }
            if (cfg.gatedNamespaces == null || cfg.gatedNamespaces.isEmpty()) {
                cfg.gatedNamespaces = new LinkedHashSet<>(Set.of("minecraft"));
            }
            if (cfg.rules == null) cfg.rules = new LinkedHashMap<>();
        } catch (Exception e) {
            UnbetaCore.LOG.error("Could not read config/unbeta/core.json, using defaults", e);
        }
        return cfg;
    }

    public void save() {
        Path file = dir().resolve("core.json");
        try {
            Files.createDirectories(dir());
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(this, w);
            }
        } catch (IOException e) {
            UnbetaCore.LOG.error("Could not write config/unbeta/core.json", e);
        }
    }

    /** Reads config/unbeta/overrides.json, creating an empty stub if absent. */
    public static Map<String, Boolean> loadUserOverrides() {
        Path file = dir().resolve("overrides.json");
        Map<String, Boolean> out = new LinkedHashMap<>();
        try {
            Files.createDirectories(dir());
            if (!Files.exists(file)) {
                Files.writeString(file,
                        "{\n"
                      + "  \"_comment\": \"Highest-priority rule overrides. true = Unbeta removes it. Example below.\",\n"
                      + "  \"rules\": {\n"
                      + "    \"dimension/minecraft.the_nether\": false\n"
                      + "  }\n"
                      + "}\n", StandardCharsets.UTF_8);
                return out;
            }
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                if (root.has("rules") && root.get("rules").isJsonObject()) {
                    JsonObject rules = root.getAsJsonObject("rules");
                    for (String k : rules.keySet()) {
                        out.put(k, rules.get(k).getAsBoolean());
                    }
                }
            }
        } catch (Exception e) {
            UnbetaCore.LOG.error("Could not read config/unbeta/overrides.json", e);
        }
        return out;
    }
}
