package net.unbeta.core.manifest;

import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.RuleKey;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * One row of manifest.json.
 *
 * @param action one of keep / remove / defer. See Manifest.Action.
 */
public record ManifestEntry(
        Identifier id,
        ContentKind kind,
        Action action,
        String introducedIn,
        String note,
        boolean verified,
        List<String> mechanisms
) {

    public enum Action {
        /** Present in Beta 1.8. Do nothing. */
        KEEP(false),
        /** Gate it. */
        REMOVE(true),
        /** Should be gone, but another mod owns it. Ship the key, ship no implementation. */
        DEFER(false);

        private final boolean removedByDefault;

        Action(boolean removedByDefault) {
            this.removedByDefault = removedByDefault;
        }

        public boolean removedByDefault() {
            return removedByDefault;
        }

        public static Action fromId(String s) {
            return switch (s.toLowerCase(java.util.Locale.ROOT)) {
                case "keep" -> KEEP;
                case "remove" -> REMOVE;
                case "defer" -> DEFER;
                default -> throw new IllegalArgumentException("Unknown manifest action: " + s);
            };
        }
    }

    public RuleKey ruleKey() {
        return RuleKey.of(kind, id);
    }
}
