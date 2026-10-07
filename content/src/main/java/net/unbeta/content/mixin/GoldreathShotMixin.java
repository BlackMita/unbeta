package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.world.World;
import net.unbeta.content.goldreath.GoldreathBulletEntity;
import net.unbeta.content.goldreath.GoldreathEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** When a Goldreath's Ghast attack fires, it looses a Goldreath bullet instead of a fireball. */
@Mixin(targets = "net.minecraft.entity.mob.GhastEntity$ShootFireballGoal")
public abstract class GoldreathShotMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"))
    private boolean unbeta_goldreathShot(World world, Entity entity) {
        if (entity instanceof FireballEntity fireball && fireball.getOwner() instanceof GoldreathEntity g
                && g.getTarget() != null) {
            return world.spawnEntity(GoldreathBulletEntity.fire(world, g, g.getTarget()));
        }
        return world.spawnEntity(entity);
    }
}
