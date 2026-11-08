package net.unbeta.content.client.train;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.train.TrainCart;
import net.unbeta.content.train.Trains;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Draws the chain between two linked minecarts (you can see a copper chain stretch). */
public final class ChainRenderer {

    private ChainRenderer() {}

    /** Half a cart's length: the chain runs between the carts' ends, not their middles. */
    private static final double HALF_CART = 0.49, HEIGHT = 0.4;

    public static void register() {
        BlockRenderLayerMap.INSTANCE.putBlock(Trains.COPPER_CHAIN, RenderLayer.getCutout());
        WorldRenderEvents.AFTER_ENTITIES.register(ChainRenderer::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        VertexConsumerProvider consumers = ctx.consumers();
        if (mc.world == null || consumers == null) return;
        Vec3d cam = ctx.camera().getPos();
        float delta = ctx.tickDelta();
        MatrixStack ms = ctx.matrixStack();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof AbstractMinecartEntity cart) || !(e instanceof TrainCart t)) continue;
            int held = t.unbeta_heldData();
            if (held != 0 && mc.world.getEntityById(held >> 2) instanceof PlayerEntity holder) {
                Vec3d c = cart.getLerpedPos(delta).add(0, HEIGHT, 0);
                Vec3d hand = handPos(mc, holder, delta);
                Vec3d dir = hand.subtract(c);
                double dist = dir.length();
                if (dist > HALF_CART + 0.05) segments(ms, consumers, cam, c.add(dir.multiply(HALF_CART / dist)), hand, held & 3);
            }
            for (int s = 0; s < 2; s++) {
                int data = t.unbeta_linkData(s);
                if (data == 0) continue;
                int otherId = data >> 2, type = data & 3;
                if (otherId <= cart.getId()) continue;         // each chain drawn once
                Entity other = mc.world.getEntityById(otherId);
                if (other == null) continue;
                draw(ctx, ms, consumers, cam, cart.getLerpedPos(delta), other.getLerpedPos(delta), type);
            }
        }
    }

    /** Roughly where a player's right hand is (for the chain they're holding a cart by). */
    private static Vec3d handPos(MinecraftClient mc, PlayerEntity p, float delta) {
        if (p == mc.player && mc.options.getPerspective().isFirstPerson()) {
            Vec3d eye = p.getCameraPosVec(delta);
            Vec3d look = p.getRotationVec(delta);
            Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
            right = right.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : right.normalize();
            return eye.add(look.multiply(0.5)).add(right.multiply(0.3)).add(0, -0.35, 0);
        }
        float yaw = (float) Math.toRadians(MathHelper.lerp(delta, p.prevBodyYaw, p.bodyYaw));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        return p.getLerpedPos(delta).add(0, p.isSneaking() ? 0.65 : 0.8, 0).add(right.multiply(0.35));
    }

    /** A long chain drawn as links about a block long each, so it isn't stretched out of shape. */
    private static void segments(MatrixStack ms, VertexConsumerProvider consumers, Vec3d cam, Vec3d from, Vec3d to, int type) {
        Vec3d dir = to.subtract(from);
        double len = dir.length();
        if (len < 0.05) return;
        int pieces = Math.max(1, (int) Math.ceil(len));
        Vec3d step = dir.multiply(1.0 / pieces);
        for (int i = 0; i < pieces; i++) piece(ms, consumers, cam, from.add(step.multiply(i)), step, type);
    }

    private static void piece(MatrixStack ms, VertexConsumerProvider consumers, Vec3d cam, Vec3d start, Vec3d step, int type) {
        double len = step.length();
        Vec3d dir = step.multiply(1 / len);
        BlockState chain = (type == TrainCart.COPPER ? Trains.COPPER_CHAIN : Blocks.CHAIN).getDefaultState();
        MinecraftClient mc = MinecraftClient.getInstance();
        int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(start.add(step.multiply(0.5))));
        ms.push();
        ms.translate(start.x - cam.x, start.y - cam.y, start.z - cam.z);
        ms.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)));
        ms.scale(1F, (float) len, 1F);
        ms.translate(-0.5, 0, -0.5);
        mc.getBlockRenderManager().renderBlockAsEntity(chain, ms, consumers, light, OverlayTexture.DEFAULT_UV);
        ms.pop();
    }

    private static void draw(WorldRenderContext ctx, MatrixStack ms, VertexConsumerProvider consumers, Vec3d cam, Vec3d a, Vec3d b, int type) {
        a = a.add(0, HEIGHT, 0);
        b = b.add(0, HEIGHT, 0);
        Vec3d dir = b.subtract(a);
        double dist = dir.length();
        double len = dist - 2 * HALF_CART;
        if (len < 0.05) return;
        dir = dir.multiply(1 / dist);
        Vec3d start = a.add(dir.multiply(HALF_CART));
        BlockState chain = (type == TrainCart.COPPER ? Trains.COPPER_CHAIN : Blocks.CHAIN).getDefaultState();
        MinecraftClient mc = MinecraftClient.getInstance();
        int light = WorldRenderer.getLightmapCoordinates(mc.world, BlockPos.ofFloored(start.add(dir.multiply(len / 2))));
        ms.push();
        ms.translate(start.x - cam.x, start.y - cam.y, start.z - cam.z);
        ms.multiply(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z)));
        ms.scale(1F, (float) len, 1F);
        ms.translate(-0.5, 0, -0.5);
        mc.getBlockRenderManager().renderBlockAsEntity(chain, ms, consumers, light, OverlayTexture.DEFAULT_UV);
        ms.pop();
    }
}
