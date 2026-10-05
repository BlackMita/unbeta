package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The manifest's loot_filter, for items: when a mob dies, any drop the manifest marks as
 * removed simply doesn't drop (spider eyes from spiders, leather from cows, and so on).
 *
 * <p>LootGate only empties the whole table of a removed block or entity; nothing stripped
 * a removed ITEM out of an otherwise-allowed table. Hooked on Entity.dropStack, which every
 * death drop passes through. Chest loot is untouched, as before. Revenants are exempt:
 * they carry a player's own inventory, which must come back intact.
 */
@Mixin(Entity.class)
public abstract class RemovedItemDropMixin {

    @ModifyVariable(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;",
                    at = @At("HEAD"), argsOnly = true)
    private ItemStack unbeta_noRemovedLoot(ItemStack stack) {
        if (stack.isEmpty()) return stack;
        Object self = this;
        if (!(self instanceof LivingEntity living) || !living.isDead()) return stack; // death drops only
        if (self instanceof net.unbeta.content.corruption.RevenantEntity) return stack;
        if (!UnbetaApi.isReady()) return stack;
        return UnbetaApi.rules().isRemoved(ContentKind.ITEM, Registries.ITEM.getId(stack.getItem()))
                ? ItemStack.EMPTY : stack;
    }
}
