package net.unbeta.content.client.zombie;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;

/**
 * How strongly the sullied atmosphere applies right now, 0..1. Eases toward 1 while the
 * camera is inside a sullied chunk and back toward 0 outside, over FADE_SECONDS, so
 * crossing a chunk border never snaps. Time-based, so it fades the same at any framerate.
 */
public final class SulliedAtmosphere {

    private static final float FADE_SECONDS = 2.0F;
    private static float strength = 0.0F;
    private static long lastNanos = 0L;

    private SulliedAtmosphere() {}

    /** Advance the fade (call once per frame) and return the current strength. */
    public static float update(Camera camera) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0.0F : Math.min(0.25F, (now - lastNanos) / 1.0e9F);
        lastNanos = now;

        BlockPos p = camera.getBlockPos();
        float target = SulliedChunksClient.isSullied(p.getX(), p.getZ()) ? 1.0F : 0.0F;
        float step = dt / FADE_SECONDS;
        if (strength < target) strength = Math.min(target, strength + step);
        else if (strength > target) strength = Math.max(target, strength - step);
        return strength;
    }

    public static float strength() {
        return strength;
    }

    /** Tint strength in total darkness, as a fraction of full daylight strength. */
    private static final float DARK_FACTOR = 0.4F;
    private static final float LIGHT_FADE_SECONDS = 1.0F;
    private static float lightFactor = 1.0F;
    private static long lastLightNanos = 0L;

    /**
     * How strong the tint should be for the light at the camera, DARK_FACTOR..1. A green
     * layer adds the same amount of green over any scene, so over a dark view it stands out
     * far more; this dims it in the dark and leaves full daylight untouched. Counts block
     * light (torches, lava) and sky light weighted by time of day, and eases over about a
     * second so walking past a torch doesn't make the tint flicker.
     */
    public static float updateLight(Camera camera) {
        net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
        net.minecraft.client.world.ClientWorld world = client.world;
        if (world == null) return lightFactor;

        long now = System.nanoTime();
        float dt = lastLightNanos == 0L ? 0.0F : Math.min(0.25F, (now - lastLightNanos) / 1.0e9F);
        lastLightNanos = now;

        BlockPos p = camera.getBlockPos();
        float block = world.getLightLevel(net.minecraft.world.LightType.BLOCK, p) / 15.0F;
        float sky = world.getLightLevel(net.minecraft.world.LightType.SKY, p) / 15.0F;
        // Sky brightness runs ~0.2 (night) to 1.0 (day); rescale so night sky counts as dark.
        float daylight = net.minecraft.util.math.MathHelper.clamp(
                (world.getSkyBrightness(client.getTickDelta()) - 0.2F) / 0.8F, 0.0F, 1.0F);
        float brightness = Math.max(block, sky * daylight);

        float target = DARK_FACTOR + (1.0F - DARK_FACTOR) * brightness;
        float step = dt / LIGHT_FADE_SECONDS;
        if (lightFactor < target) lightFactor = Math.min(target, lightFactor + step);
        else if (lightFactor > target) lightFactor = Math.max(target, lightFactor - step);
        return lightFactor;
    }
}
