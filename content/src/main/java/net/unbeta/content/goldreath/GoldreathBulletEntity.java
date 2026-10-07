package net.unbeta.content.goldreath;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.unbeta.content.mixin.ShulkerBulletAccessor;

/**
 * A Goldreath's shot: a fat shulker bullet that moves twice as fast and lifts hard
 * (Levitation IV, 10 s - roughly 40 blocks). Like any shulker bullet, one melee swipe destroys it.
 */
public class GoldreathBulletEntity extends ShulkerBulletEntity {

    public GoldreathBulletEntity(EntityType<? extends GoldreathBulletEntity> type, World world) {
        super(type, world);
    }

    public static GoldreathBulletEntity fire(World world, LivingEntity owner, Entity target) {
        GoldreathBulletEntity bullet = new GoldreathBulletEntity(GoldreathRegistry.BULLET, world);
        bullet.setOwner(owner);
        bullet.refreshPositionAndAngles(owner.getX(), owner.getY() - 0.5, owner.getZ(), owner.getYaw(), owner.getPitch());
        ShulkerBulletAccessor access = (ShulkerBulletAccessor) bullet;
        access.unbeta_setTarget(target);
        access.unbeta_setDirection(Direction.DOWN);
        access.unbeta_changeTargetDirection(Direction.Axis.Y);
        return bullet;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.isRemoved()) super.tick();   // twice the steps per tick: twice as fast
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (hit.getEntity() instanceof LivingEntity living) {
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 200, 3), this.getOwner());
        }
    }
}
