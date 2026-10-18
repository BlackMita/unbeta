package net.unbeta.content.bucket;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Eating Ice Scream: a scream, then you freeze solid - frost overlay at full, freezing damage,
 * unable to move for 2 seconds - before thawing out. Meanwhile a ripple of cold spreads from
 * where you stood: every 2 ticks it reaches one block further through a 9-wide sphere (4.5
 * blocks), nearest first - water sources become ice, lava (source or flowing) becomes
 * cobblestone, and every mob and other player it reaches freezes solid for 2 seconds too
 * (no moving; mobs also no AI or attacks). Anything frozen shows a blue frozen sheen (FrozenSheenMixin) that fades as it thaws.
 */
public final class IceScream {

    public static final Identifier SOUND_ID = new Identifier("unbeta-content", "ice_scream");
    public static SoundEvent SOUND;

    private static final int FROZEN_TICKS = 40;
    private static final double RADIUS = 4.5;
    private static final int TICKS_PER_SHELL = 2;

    private record Freeze(ServerWorld world, Vec3d at, int[] left) {}
    private record Ripple(ServerWorld world, BlockPos center, int[] age) {}

    private static final Map<LivingEntity, Freeze> FROZEN = new WeakHashMap<>();
    private static final List<Ripple> RIPPLES = new ArrayList<>();

    private IceScream() {}

    public static void register() {
        SOUND = Registry.register(Registries.SOUND_EVENT, SOUND_ID, SoundEvent.of(SOUND_ID));
        ServerTickEvents.END_WORLD_TICK.register(IceScream::tick);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            FROZEN.clear();
            RIPPLES.clear();
        });
    }

    public static void eaten(ServerWorld world, LivingEntity eater) {
        world.playSound(null, eater.getX(), eater.getY(), eater.getZ(), SOUND, SoundCategory.PLAYERS, 1.0F, 1.0F);
        freeze(world, eater);
        RIPPLES.add(new Ripple(world, eater.getBlockPos(), new int[]{0}));
    }

    /** Freeze solid for 2 seconds: freezing damage, can't move, and (for mobs) no AI or attacks. */
    public static void freeze(ServerWorld world, LivingEntity who) {
        who.damage(world.getDamageSources().freeze(), 2.0F);
        who.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, FROZEN_TICKS, 7, false, false, false));
        who.setFrozenTicks(who.getMinFreezeDamageTicks());
        FROZEN.put(who, new Freeze(world, who.getPos(), new int[]{FROZEN_TICKS}));
    }

    /** Mobs and players (not in creative or spectator); not armour stands. */
    private static boolean freezable(LivingEntity who) {
        if (!who.isAlive() || isFrozen(who) || who instanceof net.minecraft.entity.decoration.ArmorStandEntity) return false;
        if (who instanceof net.minecraft.entity.player.PlayerEntity p) return !p.isCreative() && !p.isSpectator();
        return who instanceof net.minecraft.entity.mob.MobEntity;
    }

    /** True while frozen by Ice Scream (server side). */
    public static boolean isFrozen(LivingEntity who) {
        return FROZEN.containsKey(who);
    }

    private static void tick(ServerWorld world) {
        for (Iterator<Map.Entry<LivingEntity, Freeze>> it = FROZEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<LivingEntity, Freeze> e = it.next();
            Freeze f = e.getValue();
            if (f.world() != world) continue;
            LivingEntity who = e.getKey();
            if (!who.isAlive() || --f.left()[0] < 0) { it.remove(); continue; }   // thaws from here on its own
            who.setFrozenTicks(who.getMinFreezeDamageTicks());
            if (f.left()[0] == FROZEN_TICKS / 2) who.damage(world.getDamageSources().freeze(), 1.0F);
            // Frozen in place: anyone who moves (or jumps) is put back.
            if (who.getPos().squaredDistanceTo(f.at()) > 0.04) {
                if (who instanceof ServerPlayerEntity p) {
                    p.networkHandler.requestTeleport(f.at().x, f.at().y, f.at().z, p.getYaw(), p.getPitch());
                } else {
                    who.refreshPositionAfterTeleport(f.at());
                }
            }
            who.setVelocity(Vec3d.ZERO);
        }

        for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext(); ) {
            Ripple r = it.next();
            if (r.world() != world) continue;
            int age = r.age()[0]++;
            if (age % TICKS_PER_SHELL != 0) continue;
            int shell = age / TICKS_PER_SHELL;                 // 0, 1, 2 ... blocks out
            if (shell > Math.ceil(RADIUS)) { it.remove(); continue; }
            freezeShell(world, r.center(), shell);
        }
    }

    /** Water sources whose distance from the centre falls in (shell - 1, shell]. */
    private static void freezeShell(ServerWorld world, BlockPos center, int shell) {
        int reach = (int) Math.ceil(RADIUS);
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > RADIUS || d > shell || (shell > 0 && d <= shell - 1)) continue;
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState s = world.getBlockState(p);
                    if (s.isOf(Blocks.WATER) && s.getFluidState().isStill()) {
                        world.setBlockState(p, Blocks.ICE.getDefaultState());
                    } else if (s.isOf(Blocks.LAVA)) {
                        world.setBlockState(p, Blocks.COBBLESTONE.getDefaultState());
                        world.syncWorldEvent(net.minecraft.world.WorldEvents.LAVA_EXTINGUISHED, p.toImmutable(), 0);
                    }
                }
            }
        }
        // Mobs and other players the ripple reaches this tick freeze too.
        Vec3d c = Vec3d.ofCenter(center);
        for (LivingEntity who : world.getEntitiesByClass(LivingEntity.class,
                new net.minecraft.util.math.Box(center).expand(RADIUS + 1), IceScream::freezable)) {
            double d = who.getPos().distanceTo(c);
            if (d <= RADIUS + 0.5 && d <= shell + 0.5) freeze(world, who);
        }
    }
}
