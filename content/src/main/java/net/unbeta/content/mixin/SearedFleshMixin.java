package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.unbeta.content.corruption.CorruptedAnimal;
import net.unbeta.content.corruption.SearedFlesh;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * A zombie (any variant except the Unmason), corrupted animal or Unlike Like that dies
 * while on fire drops Seared Flesh in place of rotten flesh, count unchanged.
 *
 * <p>Hooked on Entity.dropStack, which every death drop passes through - loot tables and
 * code-based drops alike - so no loot table needs editing, including vanilla's zombie
 * tables that datapacks in the pack may also touch.
 */
@Mixin(Entity.class)
public abstract class SearedFleshMixin {

    @ModifyVariable(method = "dropStack(Lnet/minecraft/item/ItemStack;F)Lnet/minecraft/entity/ItemEntity;",
                    at = @At("HEAD"), argsOnly = true)
    private ItemStack unbeta_searFlesh(ItemStack stack) {
        if (!stack.isOf(Items.ROTTEN_FLESH)) return stack;
        Entity self = (Entity)(Object)this;
        if (!(self instanceof LivingEntity living) || !living.isDead()) return stack; // death drops only
        if (!self.isOnFire()) return stack;

        boolean fleshBearer =
                (self instanceof ZombieEntity && !(self instanceof net.unbeta.content.unmason.UnmasonEntity))
                || self instanceof CorruptedAnimal
                || self instanceof net.unbeta.content.unlikelike.UnlikeLikeEntity;
        if (!fleshBearer) return stack;

        return new ItemStack(SearedFlesh.ITEM, stack.getCount());
    }
}
