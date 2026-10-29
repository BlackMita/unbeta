package net.unbeta.content.client.glow;

import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.unbeta.content.glow.Glow;
import net.unbeta.content.gunpowder.Gunpowder;

import java.util.ArrayList;
import java.util.List;

/** Which blocks around us glow (so our own lighting agrees with the server's), and their sparkle. */
public final class GlowClient {

    private GlowClient() {}

    public static void register() {
        BlockRenderLayerMap.INSTANCE.putBlock(Gunpowder.TRAIL, RenderLayer.getCutout());

        ClientPlayNetworking.registerGlobalReceiver(Glow.SYNC, (client, handler, buf, sender) -> {
            byte op = buf.readByte();
            long[] positions = buf.readLongArray();
            client.execute(() -> apply(client, op, positions));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Glow.CLIENT.clear());
        ClientTickEvents.END_CLIENT_TICK.register(GlowClient::sparkle);
    }

    private static void apply(MinecraftClient client, byte op, long[] positions) {
        List<Long> touched = new ArrayList<>();
        if (op == 0) {
            touched.addAll(Glow.CLIENT);
            Glow.CLIENT.clear();
        }
        for (long p : positions) {
            if (op == 2) Glow.CLIENT.remove(p);
            else Glow.CLIENT.add(p);
            touched.add(p);
        }
        ClientWorld world = client.world;
        if (world == null) return;
        for (long p : touched) world.getChunkManager().getLightingProvider().checkBlock(BlockPos.fromLong(p));
    }

    /** Now and then a glowing block nearby gives off a faint golden sparkle. */
    private static void sparkle(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null || client.player == null || Glow.CLIENT.isEmpty() || client.isPaused()) return;
        Random r = world.random;
        BlockPos me = client.player.getBlockPos();
        for (long l : Glow.CLIENT) {
            if (r.nextInt(30) != 0) continue;
            BlockPos p = BlockPos.fromLong(l);
            if (p.getSquaredDistance(me) > 24 * 24) continue;
            Direction face = Direction.random(r);
            BlockPos out = p.offset(face);
            if (world.getBlockState(out).isOpaqueFullCube(world, out)) continue;
            double x = p.getX() + 0.5 + face.getOffsetX() * 0.55 + (face.getOffsetX() == 0 ? r.nextDouble() - 0.5 : 0);
            double y = p.getY() + 0.5 + face.getOffsetY() * 0.55 + (face.getOffsetY() == 0 ? r.nextDouble() - 0.5 : 0);
            double z = p.getZ() + 0.5 + face.getOffsetZ() * 0.55 + (face.getOffsetZ() == 0 ? r.nextDouble() - 0.5 : 0);
            world.addParticle(Glow.SPARKLE, x, y, z, 0, 0.01, 0);
        }
    }
}
