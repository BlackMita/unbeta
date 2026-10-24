package net.unbeta.hg.bot;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.unbeta.hg.Round;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static net.unbeta.hg.bot.Reaction.Outcome.RUNNING;

/**
 * What a bot does when no Reaction is running: its Player Method's Errand (design doc, C).
 * Hares stalk, Hogs walk the rounds of their chokepoints, Tortoises gear up quietly and then,
 * late in the round, hunt the isolated. Lobby: mill about. Out: drift as a Spectre.
 */
final class Errands {

    private Errands() {}

    /** The errand for now: the one it has if it's still right, else a new one. */
    static Reaction pick(Brain br, Round.Phase phase, boolean out, Reaction have) {
        if (out) return have instanceof Spectre ? have : new Spectre();
        Traits.Play play = br.traits().play();
        if (phase == Round.Phase.LOBBY) return have instanceof Mill ? have : new Mill();
        if (phase == Round.Phase.GRACE) {
            return switch (play) {
                case HARE -> have instanceof Crowd ? have : new Crowd();
                case HOG -> have instanceof SprintOut ? have : new SprintOut();
                case TORTOISE -> have instanceof Explore e && e.label().contains("Heading") ? have : Explore.tortoise(br);
            };
        }
        if (phase == Round.Phase.GAME) {
            return switch (play) {
                case HARE -> have instanceof Stalk ? have : new Stalk();
                case HOG -> have instanceof Rounds ? have : new Rounds();
                case TORTOISE -> br.terminator() ? (have instanceof Hunt ? have : new Hunt())
                        : (have instanceof Explore e && !(have instanceof Hunt) ? have : new Explore("Checklist", Float.NaN, 0));
            };
        }
        return have instanceof Still ? have : new Still();
    }

    /** F4: waiting about - shifts, looks around, now and then jumps. */
    static void fidget(Brain br) {
        Body b = br.body;
        b.stopMoving();
        int t = b.bot.age;
        if (t % 40 == 0) {
            float yaw = b.bot.getYaw() + (br.random.nextFloat() - 0.5F) * 120F;
            b.bot.setYaw(yaw);
            b.bot.setHeadYaw(yaw);
        }
        b.bot.setJumping(t % 160 == 0 && br.random.nextInt(3) == 0);
        if (t % 50 == 0) b.bot.sidewaysSpeed = (br.random.nextFloat() - 0.5F) * 0.6F;
    }

    /** No one else has been seen within 24 blocks of them in the last minute. */
    static boolean isolated(Brain br, UUID id) {
        Notebook.Sighting s = br.notebook.sightings.get(id);
        if (s == null) return false;
        for (Notebook.Sighting o : br.notebook.sightings.values()) {
            if (o == s || br.now() - o.seen > 1200) continue;
            if (o.pos.distanceTo(s.pos) < 24) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ lobby, countdown, out

    /** Lobby: strolls about near spawn, looking at people. */
    static final class Mill extends Reaction {
        Mill() { super("E", "Milling about", Tier.ERRAND, 1e9, false, Tag.SOCIAL); }
        public Reaction fresh() { return new Mill(); }
        public boolean trigger(Brain br) { return true; }

        private BlockPos to;
        private int wait;

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (to != null) {
                if (b.walkTo(to, 1.0) != Body.Walk.WALKING) to = null;
                return RUNNING;
            }
            PlayerEntity look = null;
            for (PlayerEntity p : br.senses.visible(PlayerEntity.class)) if (look == null || p.squaredDistanceTo(b.bot) < look.squaredDistanceTo(b.bot)) look = p;
            if (look != null) b.lookAt(look.getEyePos());
            if (--wait <= 0) {
                wait = 60 + br.random.nextInt(140);
                BlockPos spawn = br.world().getSpawnPos();
                int x = spawn.getX() + br.random.nextInt(11) - 5, z = spawn.getZ() + br.random.nextInt(11) - 5;
                to = new BlockPos(x, br.world().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            }
            return RUNNING;
        }
    }

    /** Countdown and the end: stands still. */
    static final class Still extends Reaction {
        Still() { super("E", "Waiting", Tier.ERRAND, 1e9, false); }
        public Reaction fresh() { return new Still(); }
        public boolean trigger(Brain br) { return true; }

        public Outcome tick(Brain br) {
            br.body.stopMoving();
            return RUNNING;
        }
    }

    /** Out of the round: drifts about, watching. */
    static final class Spectre extends Reaction {
        Spectre() { super("E", "Spectre", Tier.ERRAND, 1e9, false); }
        public Reaction fresh() { return new Spectre(); }
        public boolean trigger(Brain br) { return true; }

        private int next;
        private boolean walking;

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (--next <= 0) {
                next = 30 + br.random.nextInt(70);
                walking = br.random.nextInt(3) != 0;
                float yaw = b.bot.getYaw() + (br.random.nextFloat() - 0.5F) * 160F;
                b.bot.setYaw(yaw);
                b.bot.setHeadYaw(yaw);
                b.bot.setBodyYaw(yaw);
            }
            b.bot.forwardSpeed = walking ? 0.7F : 0F;
            b.bot.setJumping(walking && (b.bot.horizontalCollision || b.bot.isTouchingWater()));
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ exploring (everyone's fallback)

    /**
     * Walks a heading in legs, stopping now and then to look all around, so its eyes find trees,
     * stone and ore. Can be told to keep away from spawn (Hogs), and to start on a set heading.
     */
    static class Explore extends Reaction {
        private float heading;
        private final int avoidSpawn;
        private BlockPos to;
        private int scanning;

        Explore(String name, float heading, int avoidSpawn) {
            super("E", name, Tier.ERRAND, 1e9, false);
            this.heading = heading;
            this.avoidSpawn = avoidSpawn;
        }

        public Reaction fresh() { return new Explore(name, Float.NaN, avoidSpawn); }
        public boolean trigger(Brain br) { return true; }

        /** Tortoise, grace minute: a heading at least 90 degrees off the way to spawn, walked, never sprinted. */
        static Explore tortoise(Brain br) {
            Vec3d toSpawn = br.spawn().subtract(br.bot().getPos());
            float back = (float) (MathHelper.atan2(toSpawn.z, toSpawn.x) * 180 / Math.PI) - 90F;
            return new Explore("Heading out", back + 90F + br.random.nextFloat() * 180F, 0);
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (Float.isNaN(heading)) {
                Vec3d away = b.bot.getPos().subtract(br.spawn());
                heading = away.horizontalLengthSquared() < 4 ? br.random.nextFloat() * 360F
                        : (float) (MathHelper.atan2(away.z, away.x) * 180 / Math.PI) - 90F + (br.random.nextFloat() - 0.5F) * 120F;
            }
            if (scanning > 0) {
                scanning--;
                b.stopMoving();
                float yaw = b.bot.getYaw() + 9F;
                b.bot.setYaw(yaw);
                b.bot.setHeadYaw(yaw);
                b.bot.setBodyYaw(yaw);
                b.bot.setPitch(MathHelper.sin(scanning / 6F) * 25F);
                return RUNNING;
            }
            if (to == null) {
                double rad = Math.toRadians(heading + 90);
                int x = MathHelper.floor(b.bot.getX() + Math.cos(rad) * 24), z = MathHelper.floor(b.bot.getZ() + Math.sin(rad) * 24);
                if (br.world().getWorldBorder().getDistanceInsideBorder(x, z) < 32) {
                    heading += 180F;
                    return RUNNING;
                }
                Vec3d sp = br.spawn();
                if (avoidSpawn > 0 && (x - sp.x) * (x - sp.x) + (z - sp.z) * (z - sp.z) < avoidSpawn * avoidSpawn) {
                    heading += 120F + br.random.nextFloat() * 120F;
                    return RUNNING;
                }
                to = new BlockPos(x, br.world().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z);
            }
            Body.Walk w = b.walkTo(to, 2.0);
            if (w == Body.Walk.WALKING) return RUNNING;
            if (w == Body.Walk.FAILED) heading += 90F + br.random.nextFloat() * 180F;
            else heading += (br.random.nextFloat() - 0.5F) * 140F;
            to = null;
            scanning = 40;
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ Hare

    /** Grace minute: stays near the crowd and watches where people go. */
    static final class Crowd extends Reaction {
        Crowd() { super("E", "Watching the crowd", Tier.ERRAND, 1e9, false, Tag.SOCIAL); }
        public Reaction fresh() { return new Crowd(); }
        public boolean trigger(Brain br) { return true; }

        public Outcome tick(Brain br) {
            Body b = br.body;
            Vec3d c = Vec3d.ZERO;
            int n = 0;
            for (Notebook.Sighting s : br.notebook.recent(br.now())) {
                if (s.pos.distanceTo(b.bot.getPos()) > 64) continue;
                c = c.add(s.pos);
                n++;
            }
            if (n == 0) c = br.spawn();
            else c = c.multiply(1.0 / n);
            if (b.bot.getPos().distanceTo(c) > 24) {
                b.walkTo(BlockPos.ofFloored(c), 16);
                return RUNNING;
            }
            PlayerEntity look = null;
            for (PlayerEntity p : br.senses.visible(PlayerEntity.class)) if (look == null || p.squaredDistanceTo(b.bot) < look.squaredDistanceTo(b.bot)) look = p;
            if (look != null) {
                b.stopMoving();
                b.lookAt(look.getEyePos());
            } else {
                fidget(br);
            }
            return RUNNING;
        }
    }

    /** Stalking: keeps 16 to 48 blocks from its quarry; lost, goes to where they were last seen. */
    static final class Stalk extends Reaction {
        Stalk() { super("E", "Stalking", Tier.ERRAND, 1e9, false); }
        public Reaction fresh() { return new Stalk(); }
        public boolean trigger(Brain br) { return true; }

        private long picked = -10000;
        private final Explore lost = new Explore("Looking for someone", Float.NaN, 0);

        public Outcome tick(Brain br) {
            Body b = br.body;
            long now = br.now();
            if (br.quarry == null || now - picked > 600) {
                picked = now;
                Notebook.Sighting best = null;
                for (Notebook.Sighting s : br.notebook.recent(now)) {
                    PlayerEntity p = br.world().getPlayerByUuid(s.id);
                    if (p == null || !br.isFoe(p)) continue;
                    if (best == null || s.pos.distanceTo(b.bot.getPos()) < best.pos.distanceTo(b.bot.getPos())) best = s;
                }
                br.quarry = best == null ? null : best.id;
            }
            Notebook.Sighting s = br.quarry == null ? null : br.notebook.sightings.get(br.quarry);
            if (s == null || now - s.seen > Notebook.SIGHTING_LASTS) {
                br.quarry = null;
                return lost.tick(br);
            }
            double d = s.pos.distanceTo(b.bot.getPos());
            if (now - s.seen > 100) {
                // Out of sight: to where they were last seen.
                if (d < 4) {
                    br.quarry = null;
                    return RUNNING;
                }
                b.walkTo(BlockPos.ofFloored(s.pos), 3);
                return RUNNING;
            }
            if (d > 40) b.walkTo(BlockPos.ofFloored(s.pos), 32);
            else if (d < 16) b.flee(s.pos);
            else {
                fidget(br);
                b.lookAt(s.pos.add(0, 1.6, 0));
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ Hog

    /** Grace minute: sprints straight away from spawn until 80 blocks out. */
    static final class SprintOut extends Reaction {
        SprintOut() { super("E", "Sprinting out", Tier.ERRAND, 1e9, false); }
        public Reaction fresh() { return new SprintOut(); }
        public boolean trigger(Brain br) { return true; }

        private final Explore after = new Explore("Claiming ground", Float.NaN, 48);

        public Outcome tick(Brain br) {
            Body b = br.body;
            Vec3d away = b.bot.getPos().subtract(br.spawn()).multiply(1, 0, 1);
            if (away.length() >= 80) return after.tick(br);
            if (away.lengthSquared() < 1) away = b.bot.getRotationVector().multiply(1, 0, 1);
            Vec3d to = b.bot.getPos().add(away.normalize().multiply(24));
            int x = MathHelper.floor(to.x), z = MathHelper.floor(to.z);
            if (b.walkTo(new BlockPos(x, br.world().getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z), z), 3) == Body.Walk.FAILED) {
                b.flee(br.spawn());
            }
            b.bot.setSprinting(true);
            return RUNNING;
        }
    }

    /** Rounds: walks a circuit of its chokepoints (ore clusters, lava, water by lava, chests), waiting at each. */
    static final class Rounds extends Reaction {
        Rounds() { super("E", "On its rounds", Tier.ERRAND, 1e9, false); }
        public Reaction fresh() { return new Rounds(); }
        public boolean trigger(Brain br) { return true; }

        private BlockPos at;
        private long arrived;
        private final Map<BlockPos, Long> visited = new HashMap<>();
        private final Explore wander = new Explore("Looking for a spot", Float.NaN, 48);

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (at == null) {
                at = next(br);
                if (at == null) {
                    br.hogMark = null;
                    return wander.tick(br);
                }
            }
            if (b.bot.getBlockPos().getSquaredDistance(at) > 9) {
                if (b.walkTo(at, 2.5) == Body.Walk.FAILED) {
                    visited.put(at, br.now());
                    at = null;
                }
                return RUNNING;
            }
            br.hogMark = at;
            if (arrived == 0) arrived = br.now();
            if (br.now() - arrived < 200) {
                fidget(br);
                return RUNNING;
            }
            visited.put(at, br.now());
            at = null;
            arrived = 0;
            return RUNNING;
        }

        private BlockPos next(Brain br) {
            BlockPos me = br.bot().getBlockPos();
            Vec3d spawn = br.spawn();
            BlockPos best = null;
            double bestD = 160 * 160;
            for (BlockPos p : chokepoints(br)) {
                Long v = visited.get(p);
                if (v != null && br.now() - v < 2400) continue;
                if (p.getSquaredDistance(spawn) < 48 * 48) continue;
                if (br.notebook.nearest(Notebook.Kind.DEATH, p, 8) != null) continue;   // it died there: struck off
                double d = p.getSquaredDistance(me);
                if (d < bestD) {
                    bestD = d;
                    best = p;
                }
            }
            return best;
        }

        private static java.util.List<BlockPos> chokepoints(Brain br) {
            java.util.List<BlockPos> out = new java.util.ArrayList<>();
            java.util.List<Notebook.Mark> ores = br.notebook.all(Notebook.Kind.ORE);
            for (Notebook.Mark m : ores) {
                int n = 0;
                for (Notebook.Mark o : ores) if (o.pos.getSquaredDistance(m.pos) <= 16) n++;
                if (n >= 3) out.add(m.pos);
            }
            for (Notebook.Mark m : br.notebook.all(Notebook.Kind.LAVA)) out.add(m.pos);
            for (Notebook.Mark m : br.notebook.all(Notebook.Kind.WATER)) {
                if (br.notebook.nearest(Notebook.Kind.LAVA, m.pos, 6) != null) out.add(m.pos);
            }
            for (Notebook.Mark m : br.notebook.all(Notebook.Kind.CHEST)) if (!m.what.startsWith("my")) out.add(m.pos);
            return out;
        }
    }

    // ------------------------------------------------------------------ Tortoise

    /** Terminator: hunts the isolated, one at a time, walking to where they were last seen. */
    static final class Hunt extends Explore {
        Hunt() { super("Hunting the isolated", Float.NaN, 0); }
        public Reaction fresh() { return new Hunt(); }

        public Outcome tick(Brain br) {
            Notebook.Sighting target = null;
            for (Notebook.Sighting s : br.notebook.recent(br.now())) {
                PlayerEntity p = br.world().getPlayerByUuid(s.id);
                if (p == null || !br.isFoe(p) || !isolated(br, s.id)) continue;
                if (target == null || s.seen > target.seen) target = s;
            }
            if (target == null) return super.tick(br);
            br.body.walkTo(BlockPos.ofFloored(target.pos), 6);
            return RUNNING;
        }
    }
}
