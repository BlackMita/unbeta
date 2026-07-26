package net.unbeta.core.manifest;

import net.unbeta.core.api.RuleKey;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The parsed manifest. Immutable after load. */
public final class Manifest {

    private final List<ManifestEntry> entries;
    private final Map<RuleKey, ManifestEntry> byKey;

    public Manifest(List<ManifestEntry> entries) {
        this.entries = List.copyOf(entries);
        Map<RuleKey, ManifestEntry> m = new LinkedHashMap<>();
        for (ManifestEntry e : this.entries) {
            m.put(e.ruleKey(), e);
        }
        this.byKey = Collections.unmodifiableMap(m);
    }

    public List<ManifestEntry> entries() {
        return entries;
    }

    public Map<RuleKey, ManifestEntry> byKey() {
        return byKey;
    }

    public long countUnverified() {
        return entries.stream().filter(e -> !e.verified()).count();
    }
}
