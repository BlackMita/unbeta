package net.unbeta.content.boomspore;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * A thrown Boom Spore. Explodes on impact with creeper-strength, or when its fuse
 * expires mid-flight / on the ground.
 */
public class BoomSporeEntity extends ThrownItemEntity {

    private long detonationAt = Long.MAX_VALUE;

    public BoomSporeEntity(EntityType<? extends BoomSporeEntity> type, World world) {
        super(type, world);
    }

    public BoomSporeEntity(World world, LivingEntity owner) {
        super(BoomSporeRegistry.BOOM_SPORE_ENTITY, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return BoomSporeRegistry.BOOM_SPORE_ITEM;
    }

    public void setDetonationAt(long tick) {
        this.detonationAt = tick;
    }

    @Override
    public void tick() {
        super.tick();
        // Fuse expiry mid-flight or on ground
        if (!this.getWorld().isClient && this.getWorld().getTime() >= detonationAt) {
            explode();
        }
    }

    @Override
    protected void onCollision(HitResult hitResult) {
        super.onCollision(hitResult);
        if (!this.getWorld().isClient) {
            explode();
        }
    }

    private void explode() {
        this.getWorld().createExplosion(
                this, this.getX(), this.getY(), this.getZ(),
                3.0F, World.ExplosionSourceType.MOB);
        this.discard();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putLong("DetonationAt", detonationAt);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("DetonationAt")) detonationAt = nbt.getLong("DetonationAt");
    }
}
