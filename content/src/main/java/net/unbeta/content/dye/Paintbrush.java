package net.unbeta.content.dye;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Paintbrush. Crafted plain (feather or string / any nugget / stick, top to bottom); a
 * plain brush plus a dye makes a loaded brush of that colour, good for 32 strokes. Right-click
 * any wood piece or Stone - plain or dyed - to paint it. An empty brush goes back to a plain
 * brush, ready to load again.
 *
 * <p>Also here: flint and steel on a dyed block burns the dye off (back to plain) instead of
 * setting it alight - one layer of protection.
 */
public final class Paintbrush {

    public static final int STROKES = 32;

    public static Item PLAIN;
    public static final Map<DyeColor, Item> LOADED = new EnumMap<>(DyeColor.class);

    private Paintbrush() {}

    /** A brush loaded with one colour. */
    public static final class LoadedItem extends Item {
        private final DyeColor color;

        public LoadedItem(DyeColor color, Settings settings) {
            super(settings);
            this.color = color;
        }

        public DyeColor color() {
            return color;
        }

        @Override
        public ActionResult useOnBlock(ItemUsageContext context) {
            World world = context.getWorld();
            BlockPos pos = context.getBlockPos();
            if (!DyedBlocks.paint(world, pos, color)) return ActionResult.PASS;
            if (!world.isClient) {
                world.playSound(null, pos, SoundEvents.ITEM_DYE_USE, SoundCategory.BLOCKS, 1.0F, 1.0F);
                PlayerEntity player = context.getPlayer();
                ItemStack stack = context.getStack();
                if (player == null || !player.getAbilities().creativeMode) {
                    if (stack.getDamage() + 1 >= stack.getMaxDamage()) {
                        if (player != null) player.setStackInHand(context.getHand(), new ItemStack(PLAIN));
                        world.playSound(null, pos, SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 0.5F, 1.4F);
                    } else {
                        stack.setDamage(stack.getDamage() + 1);
                    }
                }
            }
            return ActionResult.success(world.isClient);
        }
    }

    public static void register() {
        PLAIN = Registry.register(Registries.ITEM, new Identifier("unbeta-content", "paintbrush"),
                new Item(new Item.Settings().maxCount(1)));
        for (DyeColor c : DyeColor.values()) {
            LOADED.put(c, Registry.register(Registries.ITEM, new Identifier("unbeta-content", c.getName() + "_paintbrush"),
                    new LoadedItem(c, new Item.Settings().maxDamage(STROKES))));
        }
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(entries -> {
            entries.add(PLAIN);
            for (DyeColor c : DyeColor.values()) entries.add(LOADED.get(c));
        });

        // Flint and steel strips the dye from a dyed block instead of lighting it.
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack held = player.getStackInHand(hand);
            if (!held.isOf(Items.FLINT_AND_STEEL) || player.isSpectator()) return ActionResult.PASS;
            BlockPos pos = hit.getBlockPos();
            DyedBlocks.Entry e = DyedBlocks.entry(world.getBlockState(pos).getBlock());
            if (e == null || e.color() == null) return ActionResult.PASS;
            if (!world.isClient && DyedBlocks.paint(world, pos, null)) {
                world.playSound(null, pos, SoundEvents.ITEM_FLINTANDSTEEL_USE, SoundCategory.BLOCKS, 1.0F, 1.2F);
                world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 0.4F, 1.6F);
                held.damage(1, player, p -> p.sendToolBreakStatus(hand));
            }
            return ActionResult.success(world.isClient);
        });
    }
}
