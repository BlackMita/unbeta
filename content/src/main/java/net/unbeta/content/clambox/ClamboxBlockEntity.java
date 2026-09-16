package net.unbeta.content.clambox;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

/**
 * Two slots: 0 = input (the thing being pearled), 1 = output (the finished pearl).
 *
 * <p>Structure mirrors BonePileBlockEntity, which is the established pattern for a
 * custom container in this mod. The screen handler arrives in phase 2; for now this is
 * storage only.
 */
public class ClamboxBlockEntity extends BlockEntity
        implements Inventory, net.minecraft.screen.NamedScreenHandlerFactory {

    public static final int SLOT_INPUT = 0;
    public static final int SLOT_OUTPUT = 1;

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(2, ItemStack.EMPTY);

    /** Ticks of pearling done on the current input. Resets if conditions break. */
    private int progress = 0;
    /** Ticks required to finish one pearl. */
    public static final int PEARL_TIME = 200;

    public ClamboxBlockEntity(BlockPos pos, BlockState state) {
        super(ClamboxRegistry.CLAMBOX_BLOCK_ENTITY, pos, state);
    }

    @Override public int size() { return items.size(); }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStack(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack r = Inventories.splitStack(items, slot, amount);
        if (!r.isEmpty()) markDirty();
        return r;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack()) stack.setCount(getMaxCountPerStack());
        markDirty();
    }

    @Override
    public boolean canPlayerUse(net.minecraft.entity.player.PlayerEntity player) {
        return world != null && world.getBlockEntity(pos) == this
                && player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5,
                                            pos.getZ() + 0.5) <= 64.0;
    }

    @Override public void clear() { items.clear(); }

    public int getProgress() { return progress; }

    /** Two-slot delegate: index 0 = progress, index 1 = max. Synced to client. */
    public net.minecraft.screen.PropertyDelegate createPropertyDelegate() {
        return new net.minecraft.screen.PropertyDelegate() {
            @Override public int get(int index) {
                return index == 0 ? progress : PEARL_TIME;
            }
            @Override public void set(int index, int value) {
                if (index == 0) progress = value;
            }
            @Override public int size() { return 2; }
        };
    }

    /**
     * Pearling condition: any full block directly below, and a water SOURCE block
     * directly above. Sides no longer matter. Flowing water above does not count.
     *
     * <p>Uses isFullCube rather than isSolidBlock so gravity blocks (sand, gravel) and
     * the clambox's own kin count as valid floor - isSolidBlock rejects FallingBlocks.
     */
    private static boolean isSubmerged(net.minecraft.world.World world, BlockPos pos) {
        BlockPos below = pos.down();
        boolean solidFloor = world.getBlockState(below)
                .isFullCube(world, below);
        if (!solidFloor) return false;

        net.minecraft.fluid.FluidState above = world.getFluidState(pos.up());
        return above.isOf(net.minecraft.fluid.Fluids.WATER) && above.isStill();
    }

    public static void serverTick(net.minecraft.world.World world, BlockPos pos,
                                  BlockState state, ClamboxBlockEntity be) {
        if (world.isClient) return;

        ItemStack input = be.items.get(SLOT_INPUT);
        ItemStack output = be.items.get(SLOT_OUTPUT);

        boolean canRun = isSubmerged(world, pos)
                && !input.isEmpty()
                && output.isEmpty()
                && PearlItem.canPearl(input);

        if (!canRun) {
            if (be.progress != 0) { be.progress = 0; be.markDirty(); }
            return;
        }

        be.progress++;
        if (be.progress >= PEARL_TIME) {
            // Consume one from the input, put a pearl wrapping it in the output.
            ItemStack pearl = PearlItem.wrap(input);
            input.decrement(1);
            be.items.set(SLOT_OUTPUT, pearl);
            be.progress = 0;
        }
        be.markDirty();
    }

    /** Drop all slot contents into the world - called on destruction. */
    public void scatterItems(net.minecraft.world.World world) {
        net.minecraft.util.ItemScatterer.spawn(world, pos, this);
    }

    @Override
    public net.minecraft.text.Text getDisplayName() {
        return net.minecraft.text.Text.translatable("block.unbeta-content.clambox");
    }

    @Override
    public net.minecraft.screen.ScreenHandler createMenu(int syncId,
            net.minecraft.entity.player.PlayerInventory inv,
            net.minecraft.entity.player.PlayerEntity player) {
        return new ClamboxScreenHandler(syncId, inv, this, createPropertyDelegate());
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
        nbt.putInt("Progress", progress);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        items.clear();
        Inventories.readNbt(nbt, items);
        progress = nbt.getInt("Progress");
    }
}
