package net.unbeta.content.client.ladder;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.render.model.json.JsonUnbakedModel;
import net.minecraft.util.Identifier;

/**
 * The Burnt mod's burnt ladder is drawn with Smart Ladders' 3D ladder shape, in Burnt's own
 * dark charred ladder texture - so it matches the ladders around it instead of being flat.
 * Done at model-load time, so it wins whatever order the mods' resources load in.
 */
public final class BurntLadderModel {

    private static final Identifier BURNT_LADDER = new Identifier("burnt", "block/burnt_ladder");
    private static final String MODEL = "{\"parent\":\"minecraft:block/ladder\",\"textures\":"
            + "{\"0\":\"burnt:block/burnt_ladder\",\"particle\":\"burnt:block/burnt_ladder\"}}";

    private BurntLadderModel() {}

    public static void register() {
        FabricLoader loader = FabricLoader.getInstance();
        if (!loader.isModLoaded("burnt") || !loader.isModLoaded("smart_ladders")) return;
        ModelLoadingPlugin.register(plugin -> plugin.modifyModelOnLoad().register((original, context) ->
                BURNT_LADDER.equals(context.id()) ? JsonUnbakedModel.deserialize(MODEL) : original));
    }
}
