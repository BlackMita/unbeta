package net.unbeta.content.mimic;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import net.unbeta.content.lockey.CarryOnCompat;
import net.unbeta.content.lockey.LockeyItem;
import net.unbeta.content.lockey.LockeyMessages;
import net.unbeta.content.lockey.LockeyRegistry;
import net.unbeta.content.lockey.LockeyState;
import net.unbeta.content.mixin.LivingEntityLootInvoker;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.UnbetaApi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A disguised mimic locked with a Lockey is, functionally, a locked chest: it stays shut,
 * can't be killed, and each strike forces out a single unit of its contents (with a sad
 * wolf whine). The lock is a tag on the mimic itself, so it travels wherever the mimic does
 * - including in a Carry On player's arms.
 *
 * <p>At the moment of locking, Better Mimic's own loot is rolled INTO the mimic (alongside
 * whatever it had swallowed), so a locked mimic actually has something to give up - and so
 * killing it later can't roll a second helping.
 */
public final class MimicLocks {

    public static final String LOCK_PREFIX = "unbeta_lockey=";
    public static final String ROLLED = "unbeta_mimic_loot_rolled";
    private static final long STRIKE_COOLDOWN = 20; // ticks: one forced unit (and one whine) per second

    private static final ThreadLocal<List<ItemStack>> CAPTURE = new ThreadLocal<>();
    private static final Map<UUID, Long> LAST_STRIKE = new ConcurrentHashMap<>();

    private MimicLocks() {}

    public static String tagFor(UUID id) {
        return LOCK_PREFIX + id;
    }

    /** The key that locked this mimic, or null if it isn't a locked mimic. */
    public static UUID lockOf(Entity e) {
        if (!(e instanceof MimicAccess)) return null;
        for (String tag : e.getCommandTags()) {
            if (!tag.startsWith(LOCK_PREFIX)) continue;
            try {
                return UUID.fromString(tag.substring(LOCK_PREFIX.length()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return null;
    }

    /** Does this saved entity data carry the given lock? (Used for mimics in Carry On's arms.) */
    public static boolean tagsContain(NbtCompound entityNbt, UUID id) {
        return entityNbt.getList("Tags", NbtElement.STRING_TYPE).contains(NbtString.of(tagFor(id)));
    }

    /** The loaded mimic locked by this key, or null. */
    public static Entity findLoaded(ServerWorld world, UUID id) {
        if (id == null) return null;
        for (Entity e : world.iterateEntities()) {
            if (id.equals(lockOf(e))) return e;
        }
        return null;
    }

    public static boolean capturing() {
        return CAPTURE.get() != null;
    }

    /** Called from Entity.dropStack: true if the stack was taken into the mimic instead of dropped. */
    public static boolean capture(ItemStack stack) {
        List<ItemStack> list = CAPTURE.get();
        if (list == null) return false;
        list.add(stack.copy());
        return true;
    }

    public static void register() {
        UseEntityCallback.EVENT.register(MimicLocks::onUseOnce);
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            UUID id = lockOf(entity);
            if (id != null && entity.getWorld() instanceof ServerWorld sw) LockeyState.revoke(sw, id);
        });
    }

    /** Last mimic each player's click acted on, and when - see onUseOnce. */
    private static final Map<UUID, long[]> LAST_ACTION = new ConcurrentHashMap<>();

    /**
     * One right-click on an entity reaches the server as TWO packets in the same tick, and the
     * hook fires for both. Without this, the first locked the mimic and the second - same key,
     * now matching - unlocked it again. Once a click has acted, its twin is quietly absorbed
     * (which also keeps it away from Better Mimic's own right-click).
     */
    private static ActionResult onUseOnce(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hit) {
        if (world.isClient || !(entity instanceof MimicAccess)) return ActionResult.PASS;
        long now = world.getTime();
        long[] last = LAST_ACTION.get(player.getUuid());
        if (last != null && last[0] == entity.getId() && now - last[1] <= 1) return ActionResult.SUCCESS;
        ActionResult result = onUse(player, world, hand, entity, hit);
        if (result == ActionResult.SUCCESS) LAST_ACTION.put(player.getUuid(), new long[]{entity.getId(), now});
        return result;
    }

    private static ActionResult onUse(PlayerEntity player, World world, Hand hand, Entity entity, EntityHitResult hit) {
        if (world.isClient || !(entity instanceof MimicAccess mimic) || !(entity instanceof LivingEntity living)) {
            return ActionResult.PASS;
        }
        ServerWorld sw = (ServerWorld) world;
        ItemStack held = player.getStackInHand(hand);
        boolean lockey = LockeyItem.isLockey(held);
        UUID owner = lockOf(entity);

        if (owner == null) {
            // Only a fresh key can claim it, and only while it's still pretending to be a chest.
            if (!lockey || LockeyItem.isBound(held) || !mimic.unbeta_isDisguised()) return ActionResult.PASS;
            UUID id = LockeyItem.getOrCreateId(held);
            rollOwnLoot(sw, living, mimic);
            entity.addCommandTag(tagFor(id));
            LockeyItem.bind(held, entity.getBlockPos());
            sw.playSound(null, entity.getBlockPos(), LockeyRegistry.CHEST_LOCKED, SoundCategory.BLOCKS, 0.8F, 1.0F);
            whine(sw, entity);
            return ActionResult.SUCCESS;
        }

        if (lockey && owner.equals(LockeyItem.getId(held))) {
            entity.removeScoreboardTag(tagFor(owner));
            LockeyItem.unbind(held);
            sw.playSound(null, entity.getBlockPos(), LockeyRegistry.CHEST_UNLOCKED, SoundCategory.BLOCKS, 0.8F, 1.0F);
            return ActionResult.SUCCESS;
        }

        // Carry On's pick-up gesture: carrying a locked mimic away is allowed - the lock goes with it.
        if (CarryOnCompat.tryCarryEntity(player, entity)) return ActionResult.SUCCESS;

        LockeyMessages.deny(sw, player, entity.getBlockPos(), owner);
        return ActionResult.SUCCESS;
    }

    /** Roll Better Mimic's loot into the mimic itself (once), instead of onto the floor. */
    private static void rollOwnLoot(ServerWorld world, LivingEntity mimic, MimicAccess access) {
        if (mimic.getCommandTags().contains(ROLLED)) return;
        List<ItemStack> captured = new ArrayList<>();
        CAPTURE.set(captured);
        try {
            // Its loot routine also drops (and clears) what it had swallowed - all of it lands in `captured`.
            ((LivingEntityLootInvoker) mimic).unbeta_dropLoot(world.getDamageSources().generic(), true);
        } finally {
            CAPTURE.remove();
        }
        List<ItemStack> contents = access.unbeta_contents();
        contents.clear();
        for (ItemStack stack : captured) {
            if (stack.isEmpty()) continue;
            if (UnbetaApi.isReady() && UnbetaApi.rules().isRemoved(ContentKind.ITEM, Registries.ITEM.getId(stack.getItem()))) continue;
            contents.add(stack);
        }
        mimic.addCommandTag(ROLLED);
    }

    /** A strike on a locked mimic: one unit out, weighted by unit count, like forcing a locked chest. */
    public static void strike(ServerWorld world, LivingEntity mimic, MimicAccess access) {
        long now = world.getTime();
        Long last = LAST_STRIKE.get(mimic.getUuid());
        if (last != null && now - last < STRIKE_COOLDOWN) return;
        LAST_STRIKE.put(mimic.getUuid(), now);

        List<ItemStack> contents = access.unbeta_contents();
        contents.removeIf(ItemStack::isEmpty);
        int total = 0;
        for (ItemStack s : contents) total += s.getCount();
        world.playSound(null, mimic.getBlockPos(), SoundEvents.BLOCK_CHEST_LOCKED, SoundCategory.BLOCKS, 0.4F, 1.6F);
        if (total <= 0) return;

        int roll = world.getRandom().nextInt(total);
        for (Iterator<ItemStack> it = contents.iterator(); it.hasNext(); ) {
            ItemStack s = it.next();
            if (roll < s.getCount()) {
                ItemStack one = s.split(1);
                if (s.isEmpty()) it.remove();
                ItemScatterer.spawn(world, mimic.getX(), mimic.getY() + 0.6, mimic.getZ(), one);
                whine(world, mimic);
                return;
            }
            roll -= s.getCount();
        }
    }

    private static void whine(ServerWorld world, Entity at) {
        world.playSound(null, at.getBlockPos(), SoundEvents.ENTITY_WOLF_WHINE, SoundCategory.NEUTRAL, 1.0F, 1.0F);
    }
}
