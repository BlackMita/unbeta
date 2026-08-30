package net.unbeta.content.unlikelike;

import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class UnlikeLikeSounds {
    public static SoundEvent CRAWL;
    public static SoundEvent GROWL;
    public static SoundEvent CHEW;
    public static SoundEvent HURT;
    public static SoundEvent SCREAM;
    public static SoundEvent SPEW;

    private UnlikeLikeSounds() {}

    private static SoundEvent reg(String name) {
        Identifier id = new Identifier("unbeta-content", "mob.unlike_like." + name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void register() {
        CRAWL  = reg("crawl");
        GROWL  = reg("growl");
        CHEW   = reg("chew");
        HURT   = reg("hurt");
        SCREAM = reg("scream");
        SPEW   = reg("spew");
    }
}
