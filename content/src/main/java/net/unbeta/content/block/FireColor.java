package net.unbeta.content.block;

import net.minecraft.util.StringIdentifiable;

/**
 * The five obsidian-exclusive fire colors. Blue reuses the vanilla soul-fire look
 * conceptually; the rest are new. Purely cosmetic - behaviour is identical across all.
 */
public enum FireColor implements StringIdentifiable {
    RED("red"),
    YELLOW("yellow"),
    GREEN("green"),
    BLUE("blue"),
    PURPLE("purple");

    public static final FireColor[] VALUES = values();

    private final String name;

    FireColor(String name) {
        this.name = name;
    }

    @Override
    public String asString() {
        return name;
    }
}
