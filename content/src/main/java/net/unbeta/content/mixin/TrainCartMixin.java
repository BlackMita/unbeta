package net.unbeta.content.mixin;

import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.unbeta.content.train.TrainCart;
import net.unbeta.content.train.Trains;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/**
 * Minecart trains and jumping: every minecart gets two chain ends, an "in the air" state that
 * makes it ignore rails, and a jump when its rider presses jump.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class TrainCartMixin extends Entity implements TrainCart {

    @Unique private static final TrackedData<Integer> UNBETA_LINK0 = DataTracker.registerData(AbstractMinecartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Integer> UNBETA_LINK1 = DataTracker.registerData(AbstractMinecartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Off the rails (jumping or derailed): tells the client to draw the cart where it really is. */
    @Unique private static final TrackedData<Boolean> UNBETA_OFF_RAILS = DataTracker.registerData(AbstractMinecartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    @Shadow private boolean onRail;

    @Unique private final UUID[] unbeta_links = new UUID[2];
    @Unique private final int[] unbeta_types = new int[2];
    @Unique private final int[] unbeta_missing = new int[2];
    @Unique private boolean unbeta_airborne, unbeta_derailed, unbeta_lastJump;
    @Unique private int unbeta_airTicks;

    protected TrainCartMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    // ------------------------------------------------------------------ TrainCart

    @Override public UUID unbeta_link(int slot) { return unbeta_links[slot]; }
    @Override public int unbeta_linkType(int slot) { return unbeta_links[slot] == null ? NONE : unbeta_types[slot]; }

    @Override
    public void unbeta_setLink(int slot, UUID other, int type) {
        unbeta_links[slot] = other;
        unbeta_types[slot] = other == null ? NONE : type;
        unbeta_missing[slot] = 0;
        if (other == null) unbeta_setLinkData(slot, 0);
    }

    @Override public int unbeta_linkData(int slot) { return this.dataTracker.get(slot == 0 ? UNBETA_LINK0 : UNBETA_LINK1); }

    @Override
    public void unbeta_setLinkData(int slot, int data) {
        TrackedData<Integer> key = slot == 0 ? UNBETA_LINK0 : UNBETA_LINK1;
        if (this.dataTracker.get(key) != data) this.dataTracker.set(key, data);
    }

    @Override public int unbeta_missing(int slot) { return unbeta_missing[slot]; }
    @Override public void unbeta_setMissing(int slot, int ticks) { unbeta_missing[slot] = ticks; }
    @Override public boolean unbeta_onRail() { return onRail; }
    @Override public boolean unbeta_airborne() { return unbeta_airborne; }

    @Override
    public void unbeta_setAirborne(boolean airborne) {
        unbeta_airborne = airborne;
        unbeta_airTicks = 0;
        unbeta_syncOffRails();
    }

    @Unique
    private void unbeta_syncOffRails() {
        boolean off = unbeta_airborne || unbeta_derailed;
        if (this.dataTracker.get(UNBETA_OFF_RAILS) != off) this.dataTracker.set(UNBETA_OFF_RAILS, off);
    }

    @Override public boolean unbeta_derailed() { return unbeta_derailed; }
    @Override
    public void unbeta_setDerailed(boolean derailed) {
        unbeta_derailed = derailed;
        unbeta_syncOffRails();
    }

    // ------------------------------------------------------------------ saving and syncing

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void unbeta_trackLinks(CallbackInfo ci) {
        this.dataTracker.startTracking(UNBETA_LINK0, 0);
        this.dataTracker.startTracking(UNBETA_LINK1, 0);
        this.dataTracker.startTracking(UNBETA_OFF_RAILS, false);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void unbeta_saveLinks(NbtCompound nbt, CallbackInfo ci) {
        for (int s = 0; s < 2; s++) {
            if (unbeta_links[s] != null) {
                nbt.putUuid("UnbetaLink" + s, unbeta_links[s]);
                nbt.putInt("UnbetaLinkType" + s, unbeta_types[s]);
            }
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void unbeta_loadLinks(NbtCompound nbt, CallbackInfo ci) {
        for (int s = 0; s < 2; s++) {
            if (nbt.containsUuid("UnbetaLink" + s)) unbeta_setLink(s, nbt.getUuid("UnbetaLink" + s), nbt.getInt("UnbetaLinkType" + s));
            else unbeta_setLink(s, null, NONE);
        }
    }

    // ------------------------------------------------------------------ riding the rails (or not)

    /** In the air (or derailed), a cart doesn't see the rails under it at all. */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/block/AbstractRailBlock;isRail(Lnet/minecraft/block/BlockState;)Z"))
    private boolean unbeta_ignoreRailsInAir(BlockState state) {
        if (unbeta_airborne || unbeta_derailed) return false;
        return AbstractRailBlock.isRail(state);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void unbeta_jumpInput(CallbackInfo ci) {
        if (!(this.getWorld() instanceof ServerWorld)) return;
        boolean pressed = Trains.riderJumping((AbstractMinecartEntity) (Object) this);
        if (pressed && !unbeta_lastJump && onRail && !unbeta_airborne && !unbeta_derailed) {
            Trains.jump((AbstractMinecartEntity) (Object) this);
        }
        unbeta_lastJump = pressed;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void unbeta_afterMove(CallbackInfo ci) {
        if (!(this.getWorld() instanceof ServerWorld world)) return;
        AbstractMinecartEntity self = (AbstractMinecartEntity) (Object) this;
        if (unbeta_airborne) {
            unbeta_airTicks++;
            Trains.airTick(self);
            Trains.checkLanding(self, unbeta_airTicks);
        } else if (unbeta_derailed) {
            Trains.checkRerail(self);
        }
        if (unbeta_hasLinks()) Trains.track(world, self);
    }

    /**
     * The renderer draws a cart sitting on the rail under it, wherever the cart really is. In the
     * air (or derailed) there's no rail to sit on, so the cart is drawn where it is: hopping.
     */
    @Inject(method = {"snapPositionToRail", "snapPositionToRailWithOffset"}, at = @At("HEAD"), cancellable = true)
    private void unbeta_drawInAir(CallbackInfoReturnable<Vec3d> cir) {
        if (this.getWorld().isClient && this.dataTracker.get(UNBETA_OFF_RAILS)) cir.setReturnValue(null);
    }

    /** Chained carts don't bump each other apart: the chain decides their spacing. */
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void unbeta_noBumpWhenChained(Entity entity, CallbackInfo ci) {
        if (entity instanceof TrainCart other && unbeta_slotOf(entity.getUuid()) >= 0) ci.cancel();
    }

    /** A broken cart drops its chains and lets go of its neighbours. */
    @Inject(method = "dropItems", at = @At("HEAD"))
    private void unbeta_dropChains(DamageSource source, CallbackInfo ci) {
        if (this.getWorld() instanceof ServerWorld world) Trains.unlinkAll(world, (AbstractMinecartEntity) (Object) this, true);
    }
}
