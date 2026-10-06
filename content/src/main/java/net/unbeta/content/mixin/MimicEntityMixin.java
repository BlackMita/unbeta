package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.unbeta.content.mimic.MimicAccess;
import net.unbeta.content.mimic.MimicChests;
import net.unbeta.content.mimic.MimicLocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Better Mimic's mimic. Unbeta decides its mood from the light, once a second while it's
 * disguised: Aggressive (creeps up when unwatched) in darkness where monsters could spawn,
 * Passive (harmless until opened or hit) anywhere brighter. Better Mimic's own coin-flip
 * at spawn is overridden. Optional: inert if Better Mimic is absent.
 */
@Pseudo
@Mixin(targets = "com.bettermimic.entity.MimicEntity", remap = false)
public abstract class MimicEntityMixin implements MimicAccess {

    @Shadow(remap = false) private List<ItemStack> stolenItems;

    @Shadow(remap = false) public abstract boolean isDisguised();

    @Shadow(remap = false) public abstract void setMimicType(byte type);

    @Override
    public List<ItemStack> unbeta_contents() {
        return this.stolenItems;
    }

    /** True while Better Mimic is reading this mimic's saved data. */
    @Unique private boolean unbeta_loading;

    /**
     * Better Mimic snaps to the block centre while LOADING, by reading the block under it. For a
     * mimic in a chunk that is still being finalised, that read waits on the very chunk being
     * finished - the server thread waits on itself, forever. ("Is the chunk loaded?" can't be
     * trusted here: Minecraft answers yes as soon as the chunk is scheduled.) So no snapping
     * during a load at all; a disguised mimic re-snaps by itself every tick it's on the ground.
     */
    @Inject(method = "method_5749", at = @At("HEAD"), remap = false)
    private void unbeta_beginLoad(NbtCompound nbt, CallbackInfo ci) {
        this.unbeta_loading = true;
    }

    @Inject(method = "method_5749", at = @At("RETURN"), remap = false)
    private void unbeta_endLoad(NbtCompound nbt, CallbackInfo ci) {
        this.unbeta_loading = false;
    }

    @Inject(method = "snapToBlockCenter", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_noSnapWhileLoading(CallbackInfo ci) {
        if (this.unbeta_loading) ci.cancel();
    }

    @Inject(method = "method_5773", at = @At("TAIL"), remap = false, require = 0)
    private void unbeta_lightMood(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        World world = self.getWorld();
        if (world.isClient || self.age % 20 != 0 || !this.isDisguised()) return;
        this.setMimicType(MimicChests.isDark(world, self.getBlockPos()) ? (byte) 0 : (byte) 1); // 0 aggressive, 1 passive
    }

    @Override
    public boolean unbeta_isDisguised() {
        return this.isDisguised();
    }

    // ---- locked: it is a chest. It never wakes, sneaks, hops or eats.

    @Inject(method = "activate", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_lockedStaysShut(net.minecraft.entity.player.PlayerEntity player, CallbackInfo ci) {
        if (MimicLocks.lockOf((Entity) (Object) this) != null) ci.cancel();
    }

    @Inject(method = "tickSneak", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_lockedNoSneak(CallbackInfo ci) {
        if (MimicLocks.lockOf((Entity) (Object) this) != null) ci.cancel();
    }

    @Inject(method = "tryJumpToChest", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_lockedNoHop(CallbackInfo ci) {
        if (MimicLocks.lockOf((Entity) (Object) this) != null) ci.cancel();
    }

    @Inject(method = "tickCollectNearbyItems", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_lockedNoEating(CallbackInfo ci) {
        if (MimicLocks.lockOf((Entity) (Object) this) != null) ci.cancel();
    }

    /** Unkillable while locked (only /kill and the void get through); a player's strike forces out one unit. */
    @Inject(method = "method_5643", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_lockedIsAChest(net.minecraft.entity.damage.DamageSource source, float amount,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.getWorld().isClient || MimicLocks.lockOf(self) == null) return;
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)
                || source.isOf(net.minecraft.entity.damage.DamageTypes.GENERIC_KILL)) return;
        cir.setReturnValue(false);
        if (source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity) {
            MimicLocks.strike((net.minecraft.server.world.ServerWorld) self.getWorld(),
                    (net.minecraft.entity.LivingEntity) self, this);
        }
    }

    /** Its loot already went inside when it was locked - don't roll it again on death. */
    @Inject(method = "dropMimicLoot", at = @At("HEAD"), cancellable = true, remap = false)
    private void unbeta_lootAlreadyInside(CallbackInfo ci) {
        if (((Entity) (Object) this).getCommandTags().contains(MimicLocks.ROLLED) && !MimicLocks.capturing()) ci.cancel();
    }
}
