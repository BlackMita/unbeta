package net.unbeta.core.api;

/**
 * The category a manifest entry belongs to. Used as the first segment of a {@link RuleKey}.
 */
public enum ContentKind {
    ENTITY,
    ITEM,
    BLOCK,
    STRUCTURE,
    DIMENSION,
    SYSTEM;

    private final String id = name().toLowerCase(java.util.Locale.ROOT);

    public String id() {
        return id;
    }

    public static ContentKind fromId(String s) {
        for (ContentKind k : values()) {
            if (k.id.equals(s)) return k;
        }
        throw new IllegalArgumentException("Unknown content kind: " + s);
    }
}
