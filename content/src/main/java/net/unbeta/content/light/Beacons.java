package net.unbeta.content.light;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.Angerable;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.unbeta.content.corruption.CorruptedAnimal;
import net.unbeta.content.jackolantern.JackOLanternItems;
import net.unbeta.content.jackolantern.JackOLanternLogic;

import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Worn Jack o'Lanterns.
 * <ul>
 *   <li>UNLIT on a head (player or armour stand): passive animals keep away. On a player it's
 *       also a disguise - hostile mobs ignore them (JolDisguiseMixin).</li>
 *   <li>LIT on a head: a BEACON. Light on the ground just keeps mobs from spawning, but a flame
 *       carried by a body can be seen from far off. Hostile mobs see a beacon from twice their
 *       usual range and go for the nearest one over anything else - unless something has just
 *       hit them, which always comes first. Passive animals still keep away.</li>
 *   <li>A player wearing a lit one burns (unless in water) - Fire Resistance, e.g. from ice
 *       cream, makes that harmless.</li>
 *   <li>An armour stand wearing either is a SCARECROW. A lit scarecrow is a decoy: mobs attack it,
 *       and its armour soaks up their hits until it breaks.</li>
 * </ul>
 */
public final class Beacons {

    /** Armour stands wearing a lit Jack o'Lantern, per world, with the tick they were last seen so. */
    private static final Map<ServerWorld, Map<ArmorStandEntity, Long>> STANDS = new WeakHashMap<>();
    private static final long STAND_MEMORY = 40;

    private Beacons() {}

    public static boolean wearsLit(LivingEntity e) {
        return JackOLanternItems.isLit(e.getEquippedStack(EquipmentSlot.HEAD));
    }

    public static boolean wearsJol(LivingEntity e) {
        ItemStack head = e.getEquippedStack(EquipmentSlot.HEAD);
        return JackOLanternItems.isLit(head) || JackOLanternItems.isUnlit(head);
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (!wearsLit(p) || p.isSpectator() || p.isCreative() || p.isWet()) continue;
                if (p.getFireTicks() <= 0) p.setFireTicks(20);   // burning: 1 damage a second
            }
        });
    }

    /** Called every tick by each armour stand (server). Keeps the beacon list and burns the head down. */
    public static void tickStand(ServerWorld world, ArmorStandEntity stand) {
        ItemStack head = stand.getEquippedStack(EquipmentSlot.HEAD);
        if (!JackOLanternItems.isLit(head)) return;
        long now = world.getTime();
        long burnoutAt = JackOLanternItems.getBurnoutAt(head);
        if (burnoutAt < 0) {
            stand.equipStack(EquipmentSlot.HEAD, JackOLanternItems.createLitFromBlock(
                    now + JackOLanternLogic.FULL_BURN_TICKS, JackOLanternLogic.FULL_BURN_TICKS));
        } else if (now >= burnoutAt) {
            stand.equipStack(EquipmentSlot.HEAD, JackOLanternItems.createUnlit());
            return;
        }
        if (stand.isMarker() || stand.isInvisible()) return;
        STANDS.computeIfAbsent(world, w -> new WeakHashMap<>()).put(stand, now);
    }

    /** The nearest beacon within range of this mob, or null. */
    private static LivingEntity nearest(ServerWorld world, MobEntity mob, double range) {
        double best = range * range;
        LivingEntity found = null;
        for (ServerPlayerEntity p : world.getPlayers()) {
            if (p.isSpectator() || p.isCreative() || !p.isAlive() || !wearsLit(p) || !mob.canTarget(p)) continue;
            double d = mob.squaredDistanceTo(p);
            if (d < best) { best = d; found = p; }
        }
        Map<ArmorStandEntity, Long> stands = STANDS.get(world);
        if (stands != null) {
            long now = world.getTime();
            for (Iterator<Map.Entry<ArmorStandEntity, Long>> it = stands.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<ArmorStandEntity, Long> e = it.next();
                ArmorStandEntity s = e.getKey();
                if (s.isRemoved() || now - e.getValue() > STAND_MEMORY) { it.remove(); continue; }
                double d = mob.squaredDistanceTo(s);
                if (d < best) { best = d; found = s; }
            }
        }
        return found;
    }

    /** Every half second, for each hostile mob: go for the nearest beacon. */
    public static void tickMob(ServerWorld world, MobEntity mob) {
        // A scarecrow that's broken, or whose lantern went out or came off, is no longer a target.
        if (mob.getTarget() instanceof ArmorStandEntity crow
                && (!crow.isAlive() || crow.isRemoved() || !wearsLit(crow))) {
            mob.setTarget(null);
        }
        if (mob.age % 10 != 0) return;
        if (!(mob instanceof Monster || mob instanceof CorruptedAnimal) || mob instanceof Angerable) return;
        LivingEntity attacker = mob.getAttacker();
        if (attacker != null && attacker.isAlive()) return;   // whoever just hit it comes first
        double follow = mob.getAttributes().hasAttribute(EntityAttributes.GENERIC_FOLLOW_RANGE)
                ? mob.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE) : 16.0;
        LivingEntity beacon = nearest(world, mob, follow * 2.0);
        if (beacon == null) return;
        if (mob.squaredDistanceTo(beacon) <= follow * follow) {
            if (mob.getTarget() != beacon) mob.setTarget(beacon);
        } else if (mob.getTarget() == null && mob instanceof PathAwareEntity) {
            mob.getNavigation().startMovingTo(beacon, 1.0);   // seen from afar: head that way
        }
    }
}
