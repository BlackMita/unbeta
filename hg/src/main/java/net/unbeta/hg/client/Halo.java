package net.unbeta.hg.client;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A glowing golden halo floating over the head of every player who is out of the round. */
public final class Halo {

    private Halo() {}

    private static final int SEGMENTS = 24;
    private static final float INNER = 0.22F, OUTER = 0.30F, THICK = 0.045F;
    /** How far above the neck (the point the head turns about) the ring floats. */
    private static final float ABOVE_NECK = 0.6F;

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(Halo::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        VertexConsumerProvider consumers = ctx.consumers();
        if (mc.world == null || consumers == null) return;
        Vec3d cam = ctx.camera().getPos();
        float delta = ctx.tickDelta();
        VertexConsumer buf = consumers.getBuffer(RenderLayer.getLightning());
        MatrixStack ms = ctx.matrixStack();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            if (!OutClient.isOut(p.getUuid())) continue;
            if (p == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
            double x = MathHelper.lerp(delta, p.prevX, p.getX()) - cam.x;
            double z = MathHelper.lerp(delta, p.prevZ, p.getZ()) - cam.z;
            float bob = MathHelper.sin((p.age + delta) * 0.08F) * 0.04F;
            // Turn about the neck, the way the head does, so the halo tilts and turns with it.
            double neck = MathHelper.lerp(delta, p.prevY, p.getY()) + p.getHeight() - 0.3 - cam.y;
            float yaw = MathHelper.lerpAngleDegrees(delta, p.prevHeadYaw, p.headYaw);
            float pitch = MathHelper.lerp(delta, p.prevPitch, p.getPitch());
            ms.push();
            ms.translate(x, neck, z);
            ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
            ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
            ms.translate(0, ABOVE_NECK + bob, 0);
            Matrix4f m = ms.peek().getPositionMatrix();
            float top = THICK / 2, bot = -THICK / 2;
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                // A solid gold ring: top, bottom, outer rim and inner rim (each drawn both ways round).
                face(buf, m, c0 * INNER, top, s0 * INNER, c0 * OUTER, top, s0 * OUTER, c1 * OUTER, top, s1 * OUTER, c1 * INNER, top, s1 * INNER, 255);
                face(buf, m, c0 * INNER, bot, s0 * INNER, c0 * OUTER, bot, s0 * OUTER, c1 * OUTER, bot, s1 * OUTER, c1 * INNER, bot, s1 * INNER, 190);
                face(buf, m, c0 * OUTER, bot, s0 * OUTER, c0 * OUTER, top, s0 * OUTER, c1 * OUTER, top, s1 * OUTER, c1 * OUTER, bot, s1 * OUTER, 215);
                face(buf, m, c0 * INNER, bot, s0 * INNER, c0 * INNER, top, s0 * INNER, c1 * INNER, top, s1 * INNER, c1 * INNER, bot, s1 * INNER, 170);
            }
            ms.pop();
        }
    }

    /** One quad, both sides; shade dims the gold a little so the rim reads as having depth. */
    private static void face(VertexConsumer buf, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int shade) {
        int r = 255 * shade / 255, g = 215 * shade / 255, b = 80 * shade / 255;
        float[][] v = {{x0, y0, z0}, {x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}};
        for (int k = 0; k < 4; k++) buf.vertex(m, v[k][0], v[k][1], v[k][2]).color(r, g, b, 200).next();
        for (int k = 3; k >= 0; k--) buf.vertex(m, v[k][0], v[k][1], v[k][2]).color(r, g, b, 200).next();
    }
}
