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
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** A glowing golden halo floating over the head of every player who is out of the round. */
public final class Halo {

    private Halo() {}

    private static final int SEGMENTS = 24;
    private static final float INNER = 0.22F, OUTER = 0.30F;

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
            double y = MathHelper.lerp(delta, p.prevY, p.getY()) + p.getHeight() + 0.3 + bob - cam.y;
            ms.push();
            ms.translate(x, y, z);
            Matrix4f m = ms.peek().getPositionMatrix();
            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = Math.PI * 2 * i / SEGMENTS, a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
                float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
                // Top face and bottom face, so it shows from above and below.
                quad(buf, m, c0, s0, c1, s1, false);
                quad(buf, m, c0, s0, c1, s1, true);
            }
            ms.pop();
        }
    }

    private static void quad(VertexConsumer buf, Matrix4f m, float c0, float s0, float c1, float s1, boolean flip) {
        float[][] v = {{c0 * INNER, s0 * INNER}, {c0 * OUTER, s0 * OUTER}, {c1 * OUTER, s1 * OUTER}, {c1 * INNER, s1 * INNER}};
        for (int k = 0; k < 4; k++) {
            float[] p = v[flip ? 3 - k : k];
            buf.vertex(m, p[0], 0F, p[1]).color(255, 215, 80, 200).next();
        }
    }
}
