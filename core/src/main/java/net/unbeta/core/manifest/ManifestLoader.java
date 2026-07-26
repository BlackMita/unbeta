package net.unbeta.core.manifest;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.util.Identifier;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.ContentKind;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses manifest.json by hand rather than via Gson reflection. Deliberate:
 * hand-parsing gives clear error messages naming the offending entry, which
 * matters a lot when the manifest is being extended by a collaborator.
 */
public final class ManifestLoader {

    private static final String RESOURCE = "/unbeta/manifest.json";

    private ManifestLoader() {}

    public static Manifest loadBundled() {
        try (InputStream in = ManifestLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing bundled resource " + RESOURCE);
            }
            JsonObject root = JsonParser
                    .parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();

            int schema = root.has("schemaVersion") ? root.get("schemaVersion").getAsInt() : 0;
            if (schema != 1) {
                UnbetaCore.LOG.warn("manifest.json schemaVersion is {}, expected 1. Parsing anyway.", schema);
            }

            JsonArray arr = root.getAsJsonArray("entries");
            List<ManifestEntry> out = new ArrayList<>(arr.size());
            for (JsonElement el : arr) {
                out.add(parseEntry(el.getAsJsonObject()));
            }
            return new Manifest(out);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + RESOURCE, e);
        }
    }

    private static ManifestEntry parseEntry(JsonObject o) {
        String rawId = str(o, "id", null);
        if (rawId == null) {
            throw new IllegalArgumentException("Manifest entry is missing 'id': " + o);
        }
        try {
            Identifier id = new Identifier(rawId);
            ContentKind kind = ContentKind.fromId(str(o, "kind", "system"));
            ManifestEntry.Action action = ManifestEntry.Action.fromId(str(o, "action", "keep"));

            List<String> mechanisms = new ArrayList<>();
            if (o.has("mechanisms") && o.get("mechanisms").isJsonArray()) {
                for (JsonElement m : o.getAsJsonArray("mechanisms")) {
                    mechanisms.add(m.getAsString());
                }
            }

            return new ManifestEntry(
                    id,
                    kind,
                    action,
                    str(o, "introducedIn", ""),
                    str(o, "note", ""),
                    o.has("verified") && o.get("verified").getAsBoolean(),
                    List.copyOf(mechanisms));
        } catch (Exception e) {
            throw new IllegalArgumentException("Bad manifest entry for id '" + rawId + "'", e);
        }
    }

    private static String str(JsonObject o, String key, String fallback) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
    }
}
