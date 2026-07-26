package net.unbeta.core.gates;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.minecraft.loot.LootTable;
import net.minecraft.util.Identifier;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;

/**
 * Blanks the loot table of gated blocks and entities.
 *
 * <p>fabric-loot-api-v2 (1.20.1) Replace signature has FIVE params:
 * (ResourceManager, LootManager, Identifier id, LootTable original, LootTableSource source)
 * returning a LootTable. Returning null means "no replacement".
 *
 * <p>Only block and entity tables are touched. Chest/fishing/gameplay tables are left
 * alone, because a gated item can still legitimately appear in a b1.8 chest.
 */
public final class LootGate {

    private static final String BLOCKS_PREFIX = "blocks/";
    private static final String ENTITIES_PREFIX = "entities/";

    private LootGate() {}

    public static void register() {
        LootTableEvents.REPLACE.register((resourceManager, lootManager, id, original, source) -> {
            if (!UnbetaApi.isReady() || id == null) return null;
            if (!UnbetaApi.rules().isGatedNamespace(id.getNamespace())) return null;

            String path = id.getPath();

            if (path.startsWith(BLOCKS_PREFIX)) {
                Identifier blockId = new Identifier(id.getNamespace(), path.substring(BLOCKS_PREFIX.length()));
                if (UnbetaApi.rules().isRemoved(ContentKind.BLOCK, blockId)) {
                    UnbetaCore.LOG.debug("Loot gate emptied block table {}", id);
                    return LootTable.EMPTY;
                }
            } else if (path.startsWith(ENTITIES_PREFIX)) {
                Identifier entityId = new Identifier(id.getNamespace(), path.substring(ENTITIES_PREFIX.length()));
                if (UnbetaApi.rules().isRemoved(ContentKind.ENTITY, entityId)) {
                    UnbetaCore.LOG.debug("Loot gate emptied entity table {}", id);
                    return LootTable.EMPTY;
                }
            }
            return null;
        });
    }
}
