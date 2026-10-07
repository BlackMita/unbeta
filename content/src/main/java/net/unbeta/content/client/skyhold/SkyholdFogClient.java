package net.unbeta.content.client.skyhold;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.math.MathHelper;
import net.unbeta.content.skyhold.SkyholdFog;

/** Receives the server's Skyhold fog strength and fades towards it over ~2.5 seconds. */
public final class SkyholdFogClient {

    private static final float STEP = 0.02f;

    private static float target, current, previous;

    private SkyholdFogClient() {}

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(SkyholdFog.PACKET, (client, handler, buf, sender) -> {
            float strength = buf.readFloat();
            client.execute(() -> target = strength);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            previous = current;
            if (client.world == null) {
                target = current = previous = 0f;
                return;
            }
            if (current < target) current = Math.min(target, current + STEP);
            else if (current > target) current = Math.max(target, current - STEP);
        });
    }

    public static float strength(float tickDelta) {
        return MathHelper.lerp(tickDelta, previous, current);
    }
}
