package net.unbeta.hg.bot;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.mob.CreeperEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.unbeta.hg.DummyPlayer;

import static net.unbeta.hg.bot.Reaction.Outcome.DONE;
import static net.unbeta.hg.bot.Reaction.Outcome.FAILED;
import static net.unbeta.hg.bot.Reaction.Outcome.RUNNING;
import static net.unbeta.hg.bot.Reaction.Tag.AGGRESSIVE;
import static net.unbeta.hg.bot.Reaction.Tag.FEARFUL;
import static net.unbeta.hg.bot.Reaction.Tag.GATHERING;
import static net.unbeta.hg.bot.Reaction.Tag.HIDING;
import static net.unbeta.hg.bot.Reaction.Tier.SURVIVAL;

/**
 * The Survival table (design doc): about to die or be badly hurt. These take over from anything.
 * S13 (a Mimic bites) arrives with chests and looting in step 6.
 */
final class Survival {

    private Survival() {}

    private static boolean has(Brain br, java.util.function.Predicate<ItemStack> t) {
        return br.body.has(t);
    }

    // ------------------------------------------------------------------ S1 on fire

    static final class OnFire extends Reaction {
        OnFire() { super("S1", "On fire", SURVIVAL, 8, false, FEARFUL); }
        public Reaction fresh() { return new OnFire(); }

        private boolean poured;

        public boolean trigger(Brain br) {
            DummyPlayer bot = br.bot();
            if (!bot.isOnFire() || bot.isTouchingWater() || bot.isInLava() || bot.isFireImmune()) return false;
            because = "I'm burning";
            return true;
        }

        public Outcome tick(Brain br) {
            DummyPlayer bot = br.bot();
            if (!bot.isOnFire() || bot.isTouchingWater()) return DONE;
            Notebook.Mark water = br.notebook.nearest(Notebook.Kind.WATER, bot.getBlockPos(), 40,
                    m -> Math.abs(m.pos.getX() - bot.getBlockX()) <= 15 && Math.abs(m.pos.getZ() - bot.getBlockZ()) <= 15);
            if (water != null) {
                if (br.body.walkTo(water.pos, 0.5) != Body.Walk.FAILED) return RUNNING;
                br.notebook.forget(water.kind, water.pos);
            }
            if (!poured && has(br, Moves::waterBucket)) {
                br.body.hold(Moves::waterBucket);
                br.body.stopMoving();
                if (br.body.lookAt(bot.getPos().add(0, -1, 0))) {
                    br.body.useHeld();
                    poured = true;
                }
                return RUNNING;
            }
            br.body.run();
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S2 in lava

    static final class InLava extends Reaction {
        InLava() { super("S2", "In lava", SURVIVAL, 5, false, FEARFUL); }
        public Reaction fresh() { return new InLava(); }

        public boolean trigger(Brain br) {
            if (!br.bot().isInLava()) return false;
            because = "standing in lava";
            return true;
        }

        public Outcome tick(Brain br) {
            DummyPlayer bot = br.bot();
            if (!bot.isInLava()) return DONE;
            if (br.lastSafe != null) {
                br.body.lookAt(Vec3d.ofCenter(br.lastSafe).add(0, 1.2, 0));
                bot.forwardSpeed = 1F;
            }
            bot.setJumping(true);
            return RUNNING;
        }

        public void stopped(Brain br) {
            br.bot().setJumping(false);
        }
    }

    // ------------------------------------------------------------------ S3 drowning

    static final class Drowning extends Reaction {
        Drowning() { super("S3", "Out of air", SURVIVAL, 15, false, FEARFUL); }
        public Reaction fresh() { return new Drowning(); }

        public boolean trigger(Brain br) {
            DummyPlayer bot = br.bot();
            if (!bot.isSubmergedInWater() || bot.getAir() >= bot.getMaxAir() / 3) return false;
            because = "air " + bot.getAir() * 100 / bot.getMaxAir() + "%";
            return true;
        }

        public Outcome tick(Brain br) {
            DummyPlayer bot = br.bot();
            if (bot.getAir() >= bot.getMaxAir() && !bot.isSubmergedInWater()) return DONE;
            br.body.lookAt(bot.getEyePos().add(bot.getRotationVector().multiply(1, 0, 1)).add(0, 4, 0));
            bot.setJumping(true);
            bot.forwardSpeed = 0.4F;
            return RUNNING;
        }

        public void stopped(Brain br) {
            br.bot().setJumping(false);
        }
    }

    // ------------------------------------------------------------------ S4 creeper

    static final class Creeper extends Reaction {
        Creeper() { super("S4", "Creeper hissing", SURVIVAL, 3, true, FEARFUL); }
        public Reaction fresh() { return new Creeper(); }

        private CreeperEntity creeper;
        private boolean struck;

        public boolean trigger(Brain br) {
            for (CreeperEntity c : br.senses.visible(CreeperEntity.class)) {
                if (c.getFuseSpeed() > 0 && c.squaredDistanceTo(br.bot()) < 36) {
                    creeper = c;
                    because = "a creeper is hissing " + (int) c.distanceTo(br.bot()) + " blocks away";
                    return true;
                }
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (creeper == null || creeper.isRemoved()) return DONE;
            if (br.win() == Traits.Win.LOWER && !struck && br.body.has(s -> s.getItem() instanceof net.minecraft.item.SwordItem)) {
                br.body.holdBestWeapon();
                br.body.attack(creeper);
                if (br.bot().getAttackCooldownProgress(0.5F) < 0.5F) struck = true;
                return RUNNING;
            }
            if (creeper.squaredDistanceTo(br.bot()) > 64) return DONE;
            br.body.flee(creeper.getPos());
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S5 retreat

    static final class Retreat extends Reaction {
        Retreat() { super("S5", "Retreat", SURVIVAL, 10, true, FEARFUL); }
        public Reaction fresh() { return new Retreat(); }

        private LivingEntity foe;

        public boolean trigger(Brain br) {
            float at = br.retreatHealth();
            if (br.traits().keep() == Traits.Keep.MEDIC) at += 4;      // retreats early to heal
            if (br.traits().keep() == Traits.Keep.BULWARK) at = 3;     // never flees first
            if (br.bot().getHealth() > at) return false;
            foe = br.nearestFoe(12);
            if (foe == null) return false;
            because = "down to " + Math.round(br.bot().getHealth()) / 2.0 + " hearts with " + foe.getName().getString() + " near";
            return true;
        }

        public Outcome tick(Brain br) {
            if (foe == null) return DONE;
            br.body.flee(foe.isRemoved() ? br.bot().getPos().add(br.bot().getRotationVector().multiply(-1)) : foe.getPos());
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S6 heal / S7 hungry

    /** Eats its best food, one item after another, until told it's had enough. */
    static final class EatLoop {
        private Moves.Eating eating = new Moves.Eating();
        int ate;

        /** RUNNING while eating; FAILED when it has no food left. */
        Reaction.Outcome tick(Brain br) {
            int slot = Moves.bestFoodSlot(br.body);
            if (slot < 0) return FAILED;
            ItemStack food = br.bot().getInventory().getStack(slot);
            Reaction.Outcome o = eating.tick(br.body, st -> st == food);
            if (o != RUNNING) {
                ate++;
                eating = new Moves.Eating();
            }
            return RUNNING;
        }
    }

    static final class Heal extends Reaction {
        Heal() { super("S6", "Heal up", SURVIVAL, 30, false, GATHERING); }
        public Reaction fresh() { return new Heal(); }

        private final EatLoop eat = new EatLoop();
        private final Moves.Forage forage = new Moves.Forage();

        public boolean trigger(Brain br) {
            DummyPlayer bot = br.bot();
            if (bot.getHealth() >= bot.getMaxHealth() * 0.6F) return false;
            if (br.now() - br.lastFoeSeen < 200) return false;
            if (bot.getHungerManager().getFoodLevel() >= 20) return false;
            because = "hurt, and nobody about: eat to heal";
            return true;
        }

        public Outcome tick(Brain br) {
            if (br.bot().getHungerManager().getFoodLevel() >= 20 || eat.ate >= 6) return DONE;
            if (eat.tick(br) == RUNNING) return RUNNING;
            return forage.tick(br);
        }
    }

    static final class Hungry extends Reaction {
        Hungry() { super("S7", "Hungry", SURVIVAL, 30, false, GATHERING); }
        public Reaction fresh() { return new Hungry(); }

        private final EatLoop eat = new EatLoop();
        private final Moves.Forage forage = new Moves.Forage();

        public boolean trigger(Brain br) {
            int food = br.bot().getHungerManager().getFoodLevel();
            int at = br.traits().keep() == Traits.Keep.SURVIVOR ? 14 : 6;   // Survivors eat early
            if (food > at || (food > 6 && Moves.bestFoodSlot(br.body) < 0)) return false;
            because = "hunger at " + food;
            return true;
        }

        public Outcome tick(Brain br) {
            if (br.bot().getHungerManager().getFoodLevel() >= 16 || eat.ate >= 4) return DONE;
            if (eat.tick(br) == RUNNING) return RUNNING;
            return forage.tick(br);
        }
    }

    // ------------------------------------------------------------------ S8 water drop

    static final class WaterDrop extends Reaction {
        WaterDrop() { super("S8", "Water drop", SURVIVAL, 6, false); }
        public Reaction fresh() { return new WaterDrop(); }

        private boolean poured;

        public boolean trigger(Brain br) {
            DummyPlayer bot = br.bot();
            if (bot.fallDistance <= 5 || bot.getVelocity().y > -0.4 || bot.isOnGround()) return false;
            if (!br.body.has(Moves::waterBucket)) return false;
            if (!br.fallRolled) {
                br.fallRolled = true;
                BotConfig c = BotConfig.get();
                double chance = switch (br.win()) {
                    case LOWER -> c.lowerWaterDrop;
                    case MIDDLING -> c.middlingWaterDrop;
                    case HIGHER -> c.higherWaterDrop;
                };
                if (br.traits().keep() == Traits.Keep.BUCKETEER) chance = 1.0;
                br.fallSave = br.random.nextDouble() < chance;
            }
            if (!br.fallSave) return false;
            because = "falling " + (int) bot.fallDistance + " blocks with water";
            return true;
        }

        public Outcome tick(Brain br) {
            DummyPlayer bot = br.bot();
            if (bot.isOnGround() || bot.isTouchingWater()) return DONE;
            br.body.hold(Moves::waterBucket);
            br.body.lookAt(bot.getPos().add(0, -3, 0));
            if (!poured) {
                BlockPos below = bot.getBlockPos();
                for (int i = 1; i <= 3; i++) {
                    if (!br.world().getBlockState(below.down(i)).isAir()) {
                        poured = br.body.useHeld();
                        break;
                    }
                }
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S9 zombification

    static final class Zombification extends Reaction {
        Zombification() { super("S9", "Zombification", SURVIVAL, 60, false, FEARFUL, AGGRESSIVE); }
        public Reaction fresh() { return new Zombification(); }

        static final Identifier EFFECT = new Identifier("unbeta-content", "inevitable_zombification");
        private final Moves.Eating eating = new Moves.Eating();
        private final Moves.Hide hide = new Moves.Hide();
        private Orders.Craft crafting;
        private boolean craftFailed;
        private LivingEntity victim;

        static boolean infected(LivingEntity e) {
            StatusEffect effect = Registries.STATUS_EFFECT.get(EFFECT);
            return effect != null && e.hasStatusEffect(effect);
        }

        public boolean trigger(Brain br) {
            if (!infected(br.bot())) return false;
            because = "I've got Inevitable Zombification";
            return true;
        }

        public Outcome tick(Brain br) {
            if (!infected(br.bot())) return DONE;
            if (br.body.has(s -> s.isOf(Items.GOLDEN_APPLE))) return eating.tick(br.body, s -> s.isOf(Items.GOLDEN_APPLE)) == FAILED ? FAILED : RUNNING;
            if (crafting == null && !craftFailed && Crafting.plan(br.bot(), Items.GOLDEN_APPLE, Moves.tableAt(br, 8)) != null) {
                crafting = new Orders.Craft(Items.GOLDEN_APPLE);
            }
            if (crafting != null && !crafting.succeeded) {
                if (crafting.tick(br.body) && !crafting.succeeded) {
                    crafting = null;
                    craftFailed = true;
                }
                return RUNNING;
            }
            switch (br.win()) {
                case LOWER -> {
                    if (victim == null || victim.isDead() || !br.senses.sees(victim)) {
                        victim = null;
                        for (PlayerEntity p : br.senses.visible(PlayerEntity.class)) if (br.isFoe(p)) victim = p;
                    }
                    if (victim == null) return RUNNING;
                    br.body.holdBestWeapon();
                    br.body.attack(victim);
                    return RUNNING;
                }
                case MIDDLING -> {
                    return hide.tick(br, true) == FAILED ? FAILED : RUNNING;
                }
                default -> {
                    return HigherStash.tick(br) ? DONE : RUNNING;
                }
            }
        }
    }

    /** Higher, infected: puts its best things in a chest so its revenant can't carry them off. */
    static final class HigherStash {
        static boolean tick(Brain br) {
            Body b = br.body;
            if (!b.has(s -> s.isOf(Items.CHEST))) return true;
            Body.Placed placed = b.placeNearby(Items.CHEST, net.minecraft.block.Blocks.CHEST);
            if (placed.working()) return false;
            if (placed.failed()) return true;
            if (br.world().getBlockEntity(placed.at()) instanceof net.minecraft.inventory.Inventory chest) {
                int put = 0;
                for (int i = 0; i < 36 && put < chest.size(); i++) {
                    ItemStack s = b.bot.getInventory().getStack(i);
                    if (s.isEmpty() || Moves.food(s)) continue;
                    chest.setStack(put++, s.copy());
                    b.bot.getInventory().setStack(i, ItemStack.EMPTY);
                }
                chest.markDirty();
                br.notebook.note(Notebook.Kind.CHEST, placed.at(), br.now(), "my stash", placed.at());
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ S10 hit from behind

    static final class HitFromBehind extends Reaction {
        HitFromBehind() { super("S10", "Hit from behind", SURVIVAL, 1, true, FEARFUL); }
        public Reaction fresh() { return new HitFromBehind(); }

        private LivingEntity attacker;
        private int t;

        public boolean trigger(Brain br) {
            DummyPlayer bot = br.bot();
            if (bot.hurtTime < 5 || br.handledHurt == bot.getLastAttackedTime()) return false;
            LivingEntity a = bot.getAttacker();
            if (a == null) return false;
            Vec3d to = a.getPos().subtract(bot.getPos()).normalize();
            if (to.dotProduct(bot.getRotationVector()) > 0) return false;
            br.handledHurt = bot.getLastAttackedTime();
            attacker = a;
            because = "hit by something behind me";
            return true;
        }

        public Outcome tick(Brain br) {
            if (attacker == null) return DONE;
            if (t++ < 10) {
                br.body.flee(attacker.getPos());
                return RUNNING;
            }
            br.body.stopMoving();
            return br.body.lookAt(attacker.getEyePos()) ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ S11 shot at

    static final class ShotAt extends Reaction {
        ShotAt() { super("S11", "Shot at", SURVIVAL, 2, true, FEARFUL); }
        public Reaction fresh() { return new ShotAt(); }

        private Vec3d from;
        private int t;

        public boolean trigger(Brain br) {
            for (PersistentProjectileEntity arrow : br.senses.visible(PersistentProjectileEntity.class)) {
                if (arrow.squaredDistanceTo(br.bot()) > 9 || arrow.age > 60) continue;
                Entity owner = arrow.getOwner();
                if (owner == br.bot() || (owner != null && br.senses.sees(owner))) continue;
                from = arrow.getPos().subtract(arrow.getVelocity().normalize().multiply(10));
                because = "an arrow from someone I can't see";
                return true;
            }
            return false;
        }

        public Outcome tick(Brain br) {
            Notebook.Mark cover = br.notebook.nearest(Notebook.Kind.LOG, br.bot().getBlockPos(), 16);
            if (cover != null) {
                br.body.walkTo(cover.pos, 1.5);
            } else {
                br.body.flee(from);
            }
            br.bot().sidewaysSpeed = (t++ / 8) % 2 == 0 ? 0.8F : -0.8F;
            br.bot().setSprinting(true);
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S12 dark

    static final class Dark extends Reaction {
        Dark() { super("S12", "Dark", SURVIVAL, 600, false, HIDING); }
        public Reaction fresh() { return new Dark(); }

        private final Moves.LightTorch lighting = new Moves.LightTorch();
        private final Moves.Hide hide = new Moves.Hide();
        private boolean hiding;
        private boolean huddling;
        private boolean lightFailed;

        public void stopped(Brain br) {
            br.bot().setSneaking(false);
        }

        static boolean dark(Brain br) {
            return br.world().isNight() && br.world().getLightLevel(br.bot().getBlockPos()) <= 4;
        }

        public boolean trigger(Brain br) {
            if (!dark(br)) return false;
            because = "night, and too dark to see";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            if (huddling) {
                // Couldn't dig in (stone needs an iron pick): crouch by a wall till it's light.
                if (!dark(br)) return DONE;
                b.stopMoving();
                b.bot.setSneaking(true);
                return RUNNING;
            }
            if (hiding) {
                boolean stay = br.world().isNight();
                Outcome o = hide.tick(br, stay);
                if (o == FAILED && !hide.started()) {
                    huddling = true;
                    return RUNNING;
                }
                return o;
            }
            if (!dark(br)) return DONE;
            if (b.has(Moves::litTorch)) {
                net.minecraft.block.Block torch = Registries.BLOCK.get(new Identifier("unbeta-content", "torch"));
                Body.Placed p = b.placeNearby(b.bot.getInventory().getStack(b.slot(Moves::litTorch)).getItem(), torch);
                if (p.working()) return RUNNING;
                return p.failed() ? FAILED : DONE;
            }
            if (!lightFailed && b.has(Moves::unlitTorch) && br.notebook.nearest(Notebook.Kind.FLAME, b.bot.getBlockPos(), 32) != null) {
                Outcome o = lighting.tick(br);
                if (o == FAILED) lightFailed = true;
                return RUNNING;
            }
            hiding = true;
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S13 a Mimic

    static final class Mimic extends Reaction {
        Mimic() { super("S13", "Mimic", SURVIVAL, 3, true, FEARFUL); }
        public Reaction fresh() { return new Mimic(); }

        private LivingEntity mimic;

        public boolean trigger(Brain br) {
            LivingEntity a = br.bot().getAttacker();
            if (a == null || br.bot().hurtTime < 5) return false;
            if (!Registries.ENTITY_TYPE.getId(a.getType()).getPath().contains("mimic")) return false;
            mimic = a;
            br.notebook.note(Notebook.Kind.DANGER, a.getBlockPos(), br.now(), "Mimic", br.bot().getBlockPos());
            because = "the chest bit me";
            return true;
        }

        public Outcome tick(Brain br) {
            if (mimic.isDead() || mimic.isRemoved()) return DONE;
            if (br.win() == Traits.Win.LOWER) return Fight.melee(br, mimic) ? DONE : RUNNING;
            br.body.flee(mimic.getPos());
            return br.bot().distanceTo(mimic) > 6 ? DONE : RUNNING;
        }
    }

    // ------------------------------------------------------------------ S14 Guardian Post

    static final class GuardianBeam extends Reaction {
        GuardianBeam() { super("S14", "Guardian beam", SURVIVAL, 4, true, FEARFUL); }
        public Reaction fresh() { return new GuardianBeam(); }

        private LivingEntity post;
        private boolean charged;

        public boolean trigger(Brain br) {
            DummyPlayer bot = br.bot();
            LivingEntity a = bot.getAttacker();
            if (a == null || bot.hurtTime < 5) return false;
            if (!Registries.ENTITY_TYPE.getId(a.getType()).getPath().equals("guardian_post")) return false;
            post = a;
            br.notebook.note(Notebook.Kind.DANGER, a.getBlockPos(), br.now(), "Guardian Post", bot.getBlockPos());
            because = "a Guardian Post's beam";
            return true;
        }

        public Outcome tick(Brain br) {
            if (post == null) return DONE;
            if (br.win() == Traits.Win.LOWER && !charged) {
                br.body.attack(post);
                if (br.bot().distanceTo(post) < 3) charged = true;
                return RUNNING;
            }
            br.body.flee(post.getPos());
            return post.canSee(br.bot()) ? RUNNING : DONE;
        }
    }

    // ------------------------------------------------------------------ S15 Enderman

    static final class Enderman extends Reaction {
        Enderman() { super("S15", "Enderman", SURVIVAL, 6, true, FEARFUL, AGGRESSIVE); }
        public Reaction fresh() { return new Enderman(); }

        private EndermanEntity man;

        public boolean trigger(Brain br) {
            for (EndermanEntity e : br.senses.visible(EndermanEntity.class)) {
                if (e.squaredDistanceTo(br.bot()) < 64 && (e.isAngry() || e.getTarget() == br.bot())) {
                    man = e;
                    because = "an Enderman is coming for me";
                    return true;
                }
            }
            return false;
        }

        public Outcome tick(Brain br) {
            if (man == null || man.isRemoved()) return DONE;
            Body b = br.body;
            switch (br.win()) {
                case LOWER -> {
                    b.holdBestWeapon();
                    b.attack(man);
                }
                case HIGHER -> {
                    if (b.hold(s -> s.isOf(Items.GLOW_BERRIES))) {
                        b.stopMoving();
                        b.lookAt(man.getEyePos());
                    } else if (b.has(Moves::litTorch) && br.world().getLightLevel(b.bot.getBlockPos()) < 10) {
                        b.placeBeside(b.bot.getInventory().getStack(b.slot(Moves::litTorch)).getItem(),
                                Registries.BLOCK.get(new Identifier("unbeta-content", "torch")));
                    } else if (br.world().getLightLevel(b.bot.getBlockPos()) < 10) {
                        b.flee(man.getPos());
                    }
                }
                default -> b.flee(man.getPos());
            }
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S16 respawned

    static final class Respawned extends Reaction {
        Respawned() { super("S16", "Respawned", SURVIVAL, 10, true, FEARFUL); }
        public Reaction fresh() { return new Respawned(); }

        public boolean trigger(Brain br) {
            if (br.now() - br.respawnedAt > 100) return false;
            LivingEntity foe = br.nearestFoe(16);
            if (foe == null) return false;
            because = "just respawned with " + foe.getName().getString() + " near";
            return true;
        }

        public Outcome tick(Brain br) {
            br.body.flee(br.spawn());
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S17 border

    static final class Border extends Reaction {
        Border() { super("S17", "World border", SURVIVAL, 3, true); }
        public Reaction fresh() { return new Border(); }

        public boolean trigger(Brain br) {
            if (br.world().getWorldBorder().getDistanceInsideBorder(br.bot()) > 16) return false;
            because = "near the world border";
            return true;
        }

        public Outcome tick(Brain br) {
            br.body.walkTo(br.world().getSpawnPos(), 4);
            return RUNNING;
        }
    }

    // ------------------------------------------------------------------ S18 / S19 Boom Spores

    static final class SporePlanted extends Reaction {
        SporePlanted() { super("S18", "Boom Spore on me", SURVIVAL, 1, false, FEARFUL, AGGRESSIVE); }
        public Reaction fresh() { return new SporePlanted(); }

        private int t;

        public boolean trigger(Brain br) {
            if (!br.body.has(Moves::boomSpore)) return false;
            because = "a creeper planted a Boom Spore on me";
            return true;
        }

        public Outcome tick(Brain br) {
            Body b = br.body;
            int slot = b.slot(Moves::boomSpore);
            if (slot < 0) return DONE;
            Vec3d aim = null;
            boolean atThem = br.win() == Traits.Win.LOWER || br.traits().play() == Traits.Play.HARE;
            PlayerEntity near = null;
            for (PlayerEntity p : br.senses.visible(PlayerEntity.class)) {
                if (br.isFoe(p) && p.squaredDistanceTo(b.bot) < 144 && (near == null || p.squaredDistanceTo(b.bot) < near.squaredDistanceTo(b.bot))) near = p;
            }
            if (atThem && near != null) aim = near.getEyePos().add(0, 1, 0);
            else if (near != null) aim = b.bot.getEyePos().add(b.bot.getPos().subtract(near.getPos()).normalize().multiply(8)).add(0, 2, 0);
            else aim = b.bot.getEyePos().add(b.bot.getRotationVector().multiply(8)).add(0, 2, 0);
            b.stopMoving();
            if (b.lookAt(aim) || ++t > 8) {
                b.throwSlot(slot, 1.0);
                return DONE;
            }
            return RUNNING;
        }
    }

    static final class SporeLanded extends Reaction {
        SporeLanded() { super("S19", "Boom Spore landed", SURVIVAL, 2, true, FEARFUL); }
        public Reaction fresh() { return new SporeLanded(); }

        private Vec3d at;

        public boolean trigger(Brain br) {
            for (ItemEntity it : br.senses.visible(ItemEntity.class)) {
                if (Moves.boomSpore(it.getStack()) && it.squaredDistanceTo(br.bot()) < 36) {
                    at = it.getPos();
                    because = "a Boom Spore landed near me";
                    return true;
                }
            }
            return false;
        }

        public Outcome tick(Brain br) {
            br.body.flee(at);
            return RUNNING;
        }
    }
}
