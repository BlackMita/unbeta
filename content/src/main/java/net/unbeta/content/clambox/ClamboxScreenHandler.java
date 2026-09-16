package net.unbeta.content.clambox;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

/**
 * Two content slots laid out like a furnace: input top-left (56,17), output right
 * (116,35). No fuel slot - the clambox is powered by submersion, not fuel.
 *
 * <p>Hand-rolled rather than subclassing the furnace handler, which drags in the
 * recipe book and a fuel slot that would have to be suppressed.
 */
public class ClamboxScreenHandler extends ScreenHandler {

    private final Inventory inventory;

    /** Client constructor: Fabric passes a fresh 2-slot inventory. */
    public ClamboxScreenHandler(int syncId, PlayerInventory playerInv) {
        this(syncId, playerInv, new SimpleInventory(2));
    }

    /** Server constructor: the real clambox block entity is the inventory. */
    public ClamboxScreenHandler(int syncId, PlayerInventory playerInv, Inventory inventory) {
        super(ClamboxRegistry.CLAMBOX_SCREEN_HANDLER, syncId);
        checkSize(inventory, 2);
        this.inventory = inventory;
        inventory.onOpen(playerInv.player);

        // Input slot
        this.addSlot(new Slot(inventory, ClamboxBlockEntity.SLOT_INPUT, 56, 17));
        // Output slot: take-only, no inserting a pearl by hand
        this.addSlot(new Slot(inventory, ClamboxBlockEntity.SLOT_OUTPUT, 116, 35) {
            @Override
            public boolean canInsert(ItemStack stack) { return false; }
        });

        // Player inventory (3 rows)
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++)
                this.addSlot(new Slot(playerInv, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
        // Player hotbar
        for (int col = 0; col < 9; col++)
            this.addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasStack()) {
            ItemStack original = slot.getStack();
            newStack = original.copy();
            // slots 0-1 = clambox, 2-37 = player inventory
            if (index < 2) {
                if (!this.insertItem(original, 2, 38, true)) return ItemStack.EMPTY;
            } else {
                // player -> clambox input only (index 0); never let a player shove
                // things into the output slot
                if (!this.insertItem(original, 0, 1, false)) return ItemStack.EMPTY;
            }
            if (original.isEmpty()) slot.setStack(ItemStack.EMPTY);
            else slot.markDirty();
        }
        return newStack;
    }
}
