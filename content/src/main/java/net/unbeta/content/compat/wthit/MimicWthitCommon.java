package net.unbeta.content.compat.wthit;

import mcp.mobius.waila.api.ICommonRegistrar;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.IWailaCommonPlugin;
import mcp.mobius.waila.api.data.ItemData;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.unbeta.content.mimic.MimicAccess;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the mimic disguise: a disguised mimic's contents are sent as WTHIT item data -
 * the same data a chest sends - so WTHIT lists them exactly as it lists a chest's. Runs before
 * WTHIT's own item data (priority 1550); an awake mimic is left entirely to WTHIT.
 */
public final class MimicWthitCommon implements IWailaCommonPlugin {

    @Override
    public void register(ICommonRegistrar registrar) {
        registrar.entityData(new Contents(), Entity.class, 1000);
    }

    private static final class Contents implements IDataProvider<Entity> {
        @Override
        public void appendData(IDataWriter data, IServerAccessor<Entity> accessor, IPluginConfig config) {
            if (!(accessor.getTarget() instanceof MimicAccess mimic) || !mimic.unbeta_isDisguised()) return;
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack stack : mimic.unbeta_contents()) {
                if (!stack.isEmpty()) items.add(stack.copy());
            }
            data.add(ItemData.class, result -> result.add(ItemData.of(config).add(items)));
        }
    }
}
