package net.unbeta.content.compat.wthit;

import mcp.mobius.waila.api.IClientRegistrar;
import mcp.mobius.waila.api.IEntityAccessor;
import mcp.mobius.waila.api.IEntityComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;
import mcp.mobius.waila.api.ITooltipComponent;
import mcp.mobius.waila.api.IWailaClientPlugin;
import mcp.mobius.waila.api.IWailaConfig;
import mcp.mobius.waila.api.WailaConstants;
import mcp.mobius.waila.api.component.ItemComponent;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.unbeta.content.mimic.MimicAccess;

/**
 * Client side of the mimic disguise: while disguised, WTHIT shows a mimic as a Chest - chest
 * icon, "Chest", minecraft:chest, "Minecraft". (Health and armour are hidden by
 * WthitMimicAttributesMixin; contents come from MimicWthitCommon.) Awake mimics are untouched.
 */
public final class MimicWthitClient implements IWailaClientPlugin {

    /** WTHIT's own entity icon is 1100 (first icon wins); its name lines are 900 (last write wins). */
    private static final int ICON_FIRST = 500;
    private static final int LINES_LAST = 5000;

    @Override
    public void register(IClientRegistrar registrar) {
        Disguise disguise = new Disguise();
        registrar.icon(disguise, Entity.class, ICON_FIRST);
        registrar.head(disguise, Entity.class, LINES_LAST);
        registrar.tail(disguise, Entity.class, LINES_LAST);
    }

    public static boolean disguised(Entity entity) {
        return entity instanceof MimicAccess mimic && mimic.unbeta_isDisguised();
    }

    private static final class Disguise implements IEntityComponentProvider {

        @Override
        public ITooltipComponent getIcon(IEntityAccessor accessor, IPluginConfig config) {
            return disguised(accessor.getEntity()) ? new ItemComponent(new ItemStack(Items.CHEST)) : null;
        }

        @Override
        public void appendHead(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (!disguised(accessor.getEntity())) return;
            IWailaConfig.Formatter format = IWailaConfig.get().getFormatter();
            tooltip.setLine(WailaConstants.OBJECT_NAME_TAG, format.blockName(Blocks.CHEST.getName().getString()));
            if (config.getBoolean(WailaConstants.CONFIG_SHOW_REGISTRY)) {
                tooltip.setLine(WailaConstants.REGISTRY_NAME_TAG, format.registryName(Registries.BLOCK.getId(Blocks.CHEST)));
            }
        }

        @Override
        public void appendTail(ITooltip tooltip, IEntityAccessor accessor, IPluginConfig config) {
            if (!disguised(accessor.getEntity())) return;
            if (config.getBoolean(WailaConstants.CONFIG_SHOW_MOD_NAME)) {
                tooltip.setLine(WailaConstants.MOD_NAME_TAG, IWailaConfig.get().getFormatter().modName("Minecraft"));
            }
        }
    }
}
