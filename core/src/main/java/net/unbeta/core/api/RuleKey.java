package net.unbeta.core.api;

import net.minecraft.util.Identifier;

/**
 * A stable, human-readable identifier for a single gate.
 *
 * <p>Format: {@code <kind>/<namespace>.<path>} - for example
 * {@code entity/minecraft.villager} or {@code system/unbeta.enchanting}.
 *
 * <p><b>Semantics, memorise this:</b> a rule's boolean value is
 * {@code true} when Unbeta has <i>removed or disabled</i> the thing.
 * {@code false} means vanilla 1.20.1 behaviour is left alone.
 * So {@code dimension/minecraft.the_nether == false} in Phase 1, and Phase 2
 * flips it to {@code true}.
 */
public record RuleKey(String value) implements Comparable<RuleKey> {

    public RuleKey {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Rule key must not be blank");
        }
    }

    public static RuleKey of(String value) {
        return new RuleKey(value);
    }

    public static RuleKey of(ContentKind kind, Identifier id) {
        return new RuleKey(kind.id() + "/" + id.getNamespace() + "." + id.getPath());
    }

    public static RuleKey of(ContentKind kind, String namespace, String path) {
        return new RuleKey(kind.id() + "/" + namespace + "." + path);
    }

    /** Shorthand for the {@code system/unbeta.<name>} keys. */
    public static RuleKey system(String name) {
        return new RuleKey(ContentKind.SYSTEM.id() + "/unbeta." + name);
    }

    public ContentKind kind() {
        return ContentKind.fromId(value.substring(0, value.indexOf('/')));
    }

    @Override
    public int compareTo(RuleKey o) {
        return value.compareTo(o.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
