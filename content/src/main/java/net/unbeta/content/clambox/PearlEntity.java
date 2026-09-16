package net.unbeta.content.clambox;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * A thrown pearl. Flies like a snowball; on hitting anything it breaks and drops the
 * item stored inside it at the impact point. If that item is another pearl, the player
 * picks it up and throws again - peeling the nesting.
 */
public class PearlEntity extends ThrownItemEntity {

    public PearlEntity(EntityType<? extends PearlEntity> type, World world) {
        super(type, world);
    }

    public PearlEntity(World world, LivingEntity thrower) {
        super(ClamboxRegistry.PEARL_ENTITY, thrower, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ClamboxRegistry.PEARL_ITEM;
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (this.getWorld().isClient) return;

        // The pearl stack the entity is carrying (setItem preserved its NBT).
        ItemStack pearl = this.getStack();
        ItemStack contents = PearlItem.contentsOf(pearl);

        // Break: drop the stored contents at the impact point.
        if (!contents.isEmpty()) {
            ItemEntity drop = new ItemEntity(this.getWorld(),
                    this.getX(), this.getY(), this.getZ(), contents);
            drop.setToDefaultPickupDelay();
            this.getWorld().spawnEntity(drop);
        }

        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                ClamboxRegistry.PEARL_CRACK,
                net.minecraft.sound.SoundCategory.NEUTRAL, 0.8F, 1.0F);
        // A little pop, then remove the projectile.
        this.getWorld().sendEntityStatus(this, (byte) 3); // vanilla "item break" particles
        this.discard();
    }
}
