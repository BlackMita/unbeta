package net.unbeta.content.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.World;
import net.unbeta.content.dye.PaintedBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Boats can be painted like other wood: the colour is saved with the boat and shown to everyone. */
@Mixin(BoatEntity.class)
public abstract class BoatPaintMixin extends Entity implements PaintedBoat {

    @Unique private static final TrackedData<Integer> UNBETA_PAINT = DataTracker.registerData(BoatEntity.class, TrackedDataHandlerRegistry.INTEGER);

    protected BoatPaintMixin(EntityType<?> type, World world) {
        super(type, world);
    }

    @Override public int unbeta_paint() { return this.dataTracker.get(UNBETA_PAINT); }
    @Override public void unbeta_setPaint(int color) { this.dataTracker.set(UNBETA_PAINT, color); }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void unbeta_trackPaint(CallbackInfo ci) {
        this.dataTracker.startTracking(UNBETA_PAINT, -1);
    }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void unbeta_savePaint(NbtCompound nbt, CallbackInfo ci) {
        int c = unbeta_paint();
        if (c >= 0) nbt.putInt("UnbetaPaint", c);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void unbeta_loadPaint(NbtCompound nbt, CallbackInfo ci) {
        unbeta_setPaint(nbt.contains("UnbetaPaint") ? nbt.getInt("UnbetaPaint") : -1);
    }
}
