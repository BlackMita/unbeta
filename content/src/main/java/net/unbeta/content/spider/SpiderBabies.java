package net.unbeta.content.spider;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.CaveSpiderEntity;
import net.minecraft.entity.mob.SpiderEntity;

/**
 * Swaps a spider marked at natural spawn (SpiderSpawnSwapMixin) for a cave spider as it
 * enters the world. The mark only exists on a freshly spawned spider, so reloading a chunk
 * never re-rolls anything.
 */
public final class SpiderBabies {

    public static final int SWAP_ODDS = 8;
    public static final String SWAP_TAG = "unbeta_becomes_baby";

    private SpiderBabies() {}

    // ------------------------------------------------------------------ growing up

    /** Saved on each baby: the world time it grows up, e.g. "unbeta_grow_at_123456". */
    public static final String GROW_TAG_PREFIX = "unbeta_grow_at_";
    /** Earlier builds used a colon, which /tag rejects. Still read, so existing babies keep their time. */
    private static final String LEGACY_GROW_TAG_PREFIX = "unbeta_grow_at:";
    /** One Minecraft day - how long vanilla livestock babies take to grow... */
    private static final long GROW_BASE = 24000L;
    /** ...plus a random extra of up to two more days, so they don't all grow up together. */
    private static final int GROW_SPREAD = 48000;

    /**
     * Called once a second per baby (SpiderGrowUpMixin). A baby is permanent until it grows
     * up - it can't despawn as a baby - then becomes an ordinary full-health spider that
     * despawns normally. Counts in total world time (unaffected by /time set), so babies in
     * unloaded chunks grow up too. A baby wedged somewhere only a baby fits waits rather than
     * growing into the wall.
     */
    public static void tickBaby(net.minecraft.entity.mob.CaveSpiderEntity baby) {
        if (!(baby.getWorld() instanceof net.minecraft.server.world.ServerWorld world)) return;
        if (baby.age % 20 != 0) return;
        long now = world.getTime();

        long growAt = Long.MAX_VALUE;
        for (String tag : baby.getCommandTags()) {
            String digits;
            if (tag.startsWith(GROW_TAG_PREFIX)) digits = tag.substring(GROW_TAG_PREFIX.length());
            else if (tag.startsWith(LEGACY_GROW_TAG_PREFIX)) digits = tag.substring(LEGACY_GROW_TAG_PREFIX.length());
            else continue;
            try {
                growAt = Math.min(growAt, Long.parseLong(digits));
            } catch (NumberFormatException ignored) {}
        }

        if (growAt == Long.MAX_VALUE) { // first time seen: pick its grow-up time
            baby.addCommandTag(GROW_TAG_PREFIX + (now + GROW_BASE + baby.getRandom().nextInt(GROW_SPREAD)));
            baby.setPersistent(); // babies never despawn before growing up
            return;
        }
        if (now < growAt) return;

        net.minecraft.util.math.Box adultBox = EntityType.SPIDER.getDimensions()
                .getBoxAt(baby.getX(), baby.getY(), baby.getZ());
        if (!world.isSpaceEmpty(adultBox)) return; // too cramped here; try again later

        SpiderEntity adult = EntityType.SPIDER.create(world);
        if (adult == null) return;
        adult.refreshPositionAndAngles(baby.getX(), baby.getY(), baby.getZ(), baby.getYaw(), baby.getPitch());
        if (baby.hasCustomName()) {
            adult.setCustomName(baby.getCustomName());
            adult.setCustomNameVisible(baby.isCustomNameVisible());
        }
        world.spawnEntity(adult);
        baby.discard();
        world.playSound(null, adult.getBlockPos(), net.minecraft.sound.SoundEvents.ENTITY_SPIDER_AMBIENT,
                net.minecraft.sound.SoundCategory.HOSTILE, 1.0F, 0.8F);
    }

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (!(entity instanceof SpiderEntity spider)) return;
            if (!spider.getCommandTags().contains(SWAP_TAG)) return;
            CaveSpiderEntity baby = EntityType.CAVE_SPIDER.create(world);
            if (baby == null) return;
            baby.refreshPositionAndAngles(spider.getX(), spider.getY(), spider.getZ(),
                    spider.getYaw(), spider.getPitch());
            world.spawnEntity(baby);
            spider.discard();
        });
    }
}
