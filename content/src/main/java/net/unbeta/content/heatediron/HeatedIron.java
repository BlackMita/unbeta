package net.unbeta.content.heatediron;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.FluidDrainable;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * What a sponge is to water, an iron block is to lava. A plain Block of Iron soaks up the lava
 * around it (within 5 blocks, up to 65 blocks of it) the moment it's placed or lava reaches it,
 * and becomes a Heated Iron Block: glowing orange, light 15, hot to stand on, and cooling back
 * to plain iron over 3 minutes (4 times as fast in or beside water). A heated block never soaks
 * up more lava; lava touching it only heats it back up, never past 3 minutes' worth.
 */
public final class HeatedIron {

    private HeatedIron() {}

    /** Three minutes of heat, at most. */
    public static final long MAX_HEAT = 3 * 60 * 20;
    public static final String KEY = "HeatUntil";

    public static Block BLOCK;
    public static HeatedIronItem ITEM;
    public static BlockEntityType<HeatedIronBlockEntity> BLOCK_ENTITY;

    public static void register() {
        Identifier id = new Identifier("unbeta-content", "heated_iron_block");
        BLOCK = Registry.register(Registries.BLOCK, id, new HeatedIronBlock(AbstractBlock.Settings.copy(Blocks.IRON_BLOCK).luminance(s -> 15)));
        ITEM = Registry.register(Registries.ITEM, id, new HeatedIronItem(BLOCK, new net.minecraft.item.Item.Settings()));
        BLOCK_ENTITY = Registry.register(Registries.BLOCK_ENTITY_TYPE, id, FabricBlockEntityTypeBuilder.create(HeatedIronBlockEntity::new, BLOCK).build());
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS).register(entries -> entries.add(ITEM));
    }

    // ------------------------------------------------------------------ soaking up lava

    /** A plain iron block was placed, or something changed next to it: soak up any lava in reach. */
    public static void tryAbsorb(World world, BlockPos pos) {
        if (world.isClient) return;
        boolean touching = false;
        for (Direction d : Direction.values()) if (world.getFluidState(pos.offset(d)).isIn(FluidTags.LAVA)) touching = true;
        if (!touching) return;
        int soaked = BlockPos.iterateRecursively(pos, 5, 65, (cur, queue) -> {
            for (Direction d : Direction.values()) queue.accept(cur.offset(d));
        }, cur -> {
            if (cur.equals(pos)) return true;
            if (!world.getFluidState(cur).isIn(FluidTags.LAVA)) return false;
            BlockState st = world.getBlockState(cur);
            if (st.getBlock() instanceof FluidDrainable drain && !drain.tryDrainFluid(world, cur, st).isEmpty()) return true;
            if (st.getBlock() instanceof FluidBlock) {
                world.setBlockState(cur, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                return true;
            }
            return false;
        });
        if (soaked <= 1) return;
        world.setBlockState(pos, BLOCK.getDefaultState(), Block.NOTIFY_ALL);
        if (world.getBlockEntity(pos) instanceof HeatedIronBlockEntity be) be.setHeatUntil(world.getTime() + MAX_HEAT);
        world.playSound(null, pos, SoundEvents.BLOCK_LAVA_EXTINGUISH, SoundCategory.BLOCKS, 1.0F, 0.6F);
    }

    // ------------------------------------------------------------------ the heat an item carries

    /** When this heated item will be cold, in world time (a fresh one is given full heat). */
    public static long heatUntil(ItemStack stack, World world) {
        NbtCompound be = stack.getOrCreateSubNbt("BlockEntityTag");
        if (!be.contains(KEY)) be.putLong(KEY, world.getTime() + MAX_HEAT);
        return be.getLong(KEY);
    }

    public static boolean cold(ItemStack stack, World world) {
        return heatUntil(stack, world) <= world.getTime();
    }

    /** The same number of plain iron blocks. */
    public static ItemStack cooled(ItemStack stack) {
        return new ItemStack(Items.IRON_BLOCK, stack.getCount());
    }
}
