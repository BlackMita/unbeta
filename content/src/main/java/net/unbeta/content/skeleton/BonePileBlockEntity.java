package net.unbeta.content.skeleton;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.Generic3x3ContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Bone Pile. 9-slot inventory (3x3 grid, dispenser layout).
 * Stores the skeleton's gear and any items the player puts in. When the respawn
 * timer fires, these items are equipped on the new skeleton.
 */
public class BonePileBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {

    private final DefaultedList<ItemStack> items = DefaultedList.ofSize(9, ItemStack.EMPTY);
    private long respawnAt = -1L;

    public BonePileBlockEntity(BlockPos pos, BlockState state) {
        super(BonePileRegistry.BONE_PILE_BLOCK_ENTITY, pos, state);
    }

    public void setRespawnAt(long time) {
        this.respawnAt = time;
        markDirty();
    }

    public long getRespawnAt() {
        return respawnAt;
    }

    // --- Inventory ---

    @Override public int size() { return 9; }

    @Override
    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getStack(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeStack(int slot, int amount) {
        ItemStack result = Inventories.splitStack(items, slot, amount);
        if (!result.isEmpty()) markDirty();
        return result;
    }

    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(items, slot);
    }

    @Override
    public void setStack(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxCountPerStack())
            stack.setCount(getMaxCountPerStack());
        markDirty();
    }

    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    @Override public void clear() { items.clear(); }

    // --- NBT ---

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, items);
        nbt.putLong("RespawnAt", respawnAt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        items.clear();
        Inventories.readNbt(nbt, items);
        if (nbt.contains("RespawnAt")) respawnAt = nbt.getLong("RespawnAt");
    }

    // --- Screen ---

    @Override
    public Text getDisplayName() {
        return Text.literal("Bone Pile");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity player) {
        return new Generic3x3ContainerScreenHandler(syncId, playerInv, this);
    }

    /** Returns a copy of the items list for skeleton spawning. */
    public DefaultedList<ItemStack> getItems() {
        DefaultedList<ItemStack> copy = DefaultedList.ofSize(9, ItemStack.EMPTY);
        for (int i = 0; i < 9; i++) copy.set(i, items.get(i).copy());
        return copy;
    }

    /** Scatter all items as drops at this position. */
    public void scatterItems(net.minecraft.world.World world) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                net.minecraft.block.Block.dropStack(world, pos, stack);
                items.set(i, ItemStack.EMPTY);
            }
        }
    }
}
