package net.unbeta.content.skeleton;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.SkeletonEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Empty-handed skeletons don't punch: they grab. A skeleton with nothing in its hand (bare, or
 * only wearing what it picked up - armour shows on its body, not in its hand) holds the player
 * it reaches still, for as long as it stays next to them and keeps grabbing. Hitting or
 * otherwise hurting that skeleton breaks the grip, and it can't grab again for a second.
 * (Skeletons holding junk - a flower, a plank - can't hurt or hold anyone: BonePileRegistry.isHarmless.)
 */
public final class SkeletonGrip {

    private SkeletonGrip() {}

    /** How long one grab holds (the skeleton grabs again every second it stays close). */
    private static final int HOLD_TICKS = 30;
    /** After being hit, how long before it can grab again. */
    private static final int REGRIP_TICKS = 20;
    private static final double REACH = 3.0;

    private static final UUID SLOW_ID = UUID.fromString("5b1f6c2e-8d0a-4f7e-9b3c-2a6e1d4f8c71");
    private static final EntityAttributeModifier HELD = new EntityAttributeModifier(SLOW_ID,
            "Held by a skeleton", -1.0, EntityAttributeModifier.Operation.MULTIPLY_TOTAL);

    private static final class Grip {
        final SkeletonEntity skeleton;
        Vec3d at;
        long until;

        Grip(SkeletonEntity skeleton, Vec3d at, long until) {
            this.skeleton = skeleton;
            this.at = at;
            this.until = until;
        }
    }

    private static final Map<ServerPlayerEntity, Grip> HELD_PLAYERS = new WeakHashMap<>();
    private static final Map<SkeletonEntity, Long> NO_GRIP_UNTIL = new WeakHashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(SkeletonGrip::tick);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof SkeletonEntity s) hurt(s);
            return true;
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            HELD_PLAYERS.clear();
            NO_GRIP_UNTIL.clear();
        });
    }

    /** Does this skeleton grab instead of punching? Nothing in its hand. */
    public static boolean grabs(SkeletonEntity skeleton) {
        return skeleton.getMainHandStack().isEmpty();
    }

    /** The skeleton holding this player, or null. */
    public static SkeletonEntity holder(ServerPlayerEntity p) {
        Grip g = HELD_PLAYERS.get(p);
        return g == null ? null : g.skeleton;
    }

    /** Its melee "hit": grabs (or keeps hold of) a player; never hurts. */
    public static void grab(SkeletonEntity skeleton, LivingEntity target) {
        if (!(target instanceof ServerPlayerEntity p) || p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        long now = skeleton.getWorld().getTime();
        Long blocked = NO_GRIP_UNTIL.get(skeleton);
        if (blocked != null && now < blocked) return;
        Grip g = HELD_PLAYERS.get(p);
        if (g != null && g.skeleton == skeleton) {
            g.until = now + HOLD_TICKS;
            return;
        }
        HELD_PLAYERS.put(p, new Grip(skeleton, p.getPos(), now + HOLD_TICKS));
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(SLOW_ID) == null) speed.addTemporaryModifier(HELD);
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_SKELETON_AMBIENT, SoundCategory.HOSTILE, 1F, 0.6F);
        p.sendMessage(Text.literal("A skeleton has hold of you - hit it to break free.").formatted(Formatting.GRAY), true);
    }

    /** Hurting a skeleton makes it let go. */
    private static void hurt(SkeletonEntity skeleton) {
        NO_GRIP_UNTIL.put(skeleton, skeleton.getWorld().getTime() + REGRIP_TICKS);
        for (Iterator<Map.Entry<ServerPlayerEntity, Grip>> it = HELD_PLAYERS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ServerPlayerEntity, Grip> e = it.next();
            if (e.getValue().skeleton == skeleton) {
                release(e.getKey());
                it.remove();
            }
        }
    }

    private static void release(ServerPlayerEntity p) {
        EntityAttributeInstance speed = p.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SLOW_ID);
    }

    private static void tick(MinecraftServer server) {
        if (HELD_PLAYERS.isEmpty()) return;
        for (Iterator<Map.Entry<ServerPlayerEntity, Grip>> it = HELD_PLAYERS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ServerPlayerEntity, Grip> e = it.next();
            ServerPlayerEntity p = e.getKey();
            Grip g = e.getValue();
            SkeletonEntity s = g.skeleton;
            boolean gone = p.isRemoved() || !p.isAlive() || p.isCreative() || p.isSpectator()
                    || s.isRemoved() || !s.isAlive() || !grabs(s) || s.getWorld() != p.getWorld()
                    || s.squaredDistanceTo(p) > REACH * REACH || s.getWorld().getTime() > g.until;
            if (gone) {
                release(p);
                it.remove();
                continue;
            }
            // Held in place: it may fall, but not walk, jump or get pushed away.
            Vec3d now = p.getPos();
            if (now.y < g.at.y) g.at = new Vec3d(g.at.x, now.y, g.at.z);
            if (now.squaredDistanceTo(g.at) > 0.04) {
                p.networkHandler.requestTeleport(g.at.x, g.at.y, g.at.z, p.getYaw(), p.getPitch());
            }
        }
    }
}
