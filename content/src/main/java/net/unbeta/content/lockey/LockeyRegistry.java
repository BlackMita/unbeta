package net.unbeta.content.lockey;

import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public final class LockeyRegistry {

    public static Item LOCKEY;
    public static SoundEvent CHEST_LOCKED;
    public static SoundEvent CHEST_UNLOCKED;
    public static SoundEvent CHEST_DENY;
    public static net.minecraft.recipe.RecipeSerializer<LockeySalvageRecipe> SALVAGE_SERIALIZER;

    private LockeyRegistry() {}

    private static Identifier id(String path) {
        return new Identifier("unbeta-content", path);
    }

    private static SoundEvent sound(String path) {
        Identifier i = id(path);
        return Registry.register(Registries.SOUND_EVENT, i, SoundEvent.of(i));
    }

    public static void register() {
        LOCKEY = Registry.register(Registries.ITEM, id("lockey"),
                new LockeyItem(new Item.Settings().maxCount(1)));

        CHEST_LOCKED   = sound("chest_locked");
        CHEST_UNLOCKED = sound("chest_unlocked");
        CHEST_DENY     = sound("chest_deny");

        SALVAGE_SERIALIZER = Registry.register(
                Registries.RECIPE_SERIALIZER, id("lockey_salvage"),
                new net.minecraft.recipe.SpecialRecipeSerializer<>(LockeySalvageRecipe::new));
    }
}
