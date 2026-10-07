package net.unbeta.content.stronghold;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StrongholdGenerator;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.StructureWorldAccess;
import net.unbeta.content.burntchest.BurntChestBlockEntity;
import net.unbeta.content.lockey.LockeyItem;
import net.unbeta.content.lockey.LockeyRegistry;
import net.unbeta.content.lockey.LockeyState;
import net.unbeta.content.mixin.ChestInventoryAccessor;
import net.unbeta.content.mixin.StrongholdLibraryAccessor;
import net.unbeta.content.mixin.StrongholdSquareRoomAccessor;
import net.unbeta.content.mixin.StructurePieceInvoker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The stronghold's vault: a Burnt Chest on the old End Gate platform, locked by a Lockey
 * hidden in one of the stronghold's other chests.
 *
 * <p>A stronghold generates chunk by chunk in no particular order, so the vault and the
 * key's chest can't talk to each other. Instead both work out the same PLAN from the
 * stronghold's full layout (known to every chunk while its pieces generate): where the
 * vault is, which chest gets the key, and the key's id - all derived from the vault's
 * position, so every chunk reaches identical answers. No other chests -> no key, and the
 * vault stays unlocked.
 */
public final class StrongholdVault {

    /** The structure whose pieces are generating on this thread right now. */
    public static final ThreadLocal<StructureStart> CURRENT = new ThreadLocal<>();

    private static final Map<StructureStart, Plan> PLANS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int CENTRE = 13; // middle slot of a 27-slot chest

    /** sky: a Skyhold's vault (Cloud Boots) rather than a stone stronghold's (Hookshot). */
    public record Plan(BlockPos vault, BlockPos keyChest, UUID keyId, boolean sky) {}

    private StrongholdVault() {}

    public static Plan currentPlan() {
        StructureStart start = CURRENT.get();
        return start == null ? null : PLANS.computeIfAbsent(start, StrongholdVault::plan);
    }

    private static BlockPos at(StructurePiece piece, int x, int y, int z) {
        return ((StructurePieceInvoker) piece).unbeta_offsetPos(x, y, z).toImmutable();
    }

    /** Every chest this stronghold will contain, in layout order, plus the vault. Null if not a stronghold. */
    private static Plan plan(StructureStart start) {
        StructurePiece portal = null;
        List<BlockPos> chests = new ArrayList<>();
        for (StructurePiece p : start.getChildren()) {
            if (p instanceof StrongholdGenerator.PortalRoom) {
                portal = p;
            } else if (p instanceof StrongholdGenerator.ChestCorridor) {
                chests.add(at(p, 3, 2, 3));
            } else if (p instanceof StrongholdGenerator.Library) {
                chests.add(at(p, 3, 3, 5));
                if (((StrongholdLibraryAccessor) (Object) p).unbeta_isTall()) chests.add(at(p, 12, 8, 1));
            } else if (p instanceof StrongholdGenerator.SquareRoom
                    && ((StrongholdSquareRoomAccessor) (Object) p).unbeta_getRoomType() == 2) {
                chests.add(at(p, 3, 4, 8));
            }
        }
        if (portal == null) return net.unbeta.content.skyhold.SkyholdVault.plan(start);
        BlockPos vault = at(portal, 5, 4, 10);
        if (chests.isEmpty()) return new Plan(vault, null, null, false);
        long mix = vault.asLong() * 0x9E3779B97F4A7C15L;
        BlockPos keyChest = chests.get((int) Math.floorMod(mix ^ (mix >>> 31), (long) chests.size()));
        UUID keyId = UUID.nameUUIDFromBytes(("unbeta:stronghold_vault:" + vault.asLong()).getBytes(StandardCharsets.UTF_8));
        return new Plan(vault, keyChest, keyId, false);
    }

    /** Called right after the portal room places the vault chest. */
    public static void onVaultPlaced(StructureWorldAccess world, BlockPos vault, Random random) {
        if (!(world.getBlockEntity(vault) instanceof BurntChestBlockEntity chest)) return;
        Plan plan = currentPlan();
        fill(chest, random, plan != null && plan.sky());
        if (plan == null || plan.keyId() == null || !plan.vault().equals(vault)) return; // unlocked
        // World state belongs to the server thread; generation runs on worker threads.
        ServerWorld sw = world.toServerWorld();
        UUID id = plan.keyId();
        sw.getServer().execute(() -> LockeyState.lock(sw, vault, id));
    }

        /** The vault's key if this position is the stronghold's chosen key chest, else empty. */
    public static ItemStack keyFor(BlockPos pos) {
        Plan plan = currentPlan();
        if (plan == null || plan.keyChest() == null || !plan.keyChest().equals(pos)) return ItemStack.EMPTY;
        ItemStack key = new ItemStack(LockeyRegistry.LOCKEY);
        key.getOrCreateNbt().putUuid(LockeyItem.NBT_ID, plan.keyId());
        LockeyItem.bind(key, plan.vault());
        return key;
    }

    /** Called right after a structure piece places a loot chest (that didn't become a mimic). */
    public static void onChestPlaced(ServerWorldAccess world, BlockPos pos, Random random) {
        if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return;
        ItemStack key = keyFor(pos);
        if (key.isEmpty()) return;
        // Straight into the slot list: setStack would try to roll the chest's loot table
        // now, and there's no world attached yet. The loot rolls into the free slots later.
        DefaultedList<ItemStack> inv = ((ChestInventoryAccessor) chest).unbeta_getInventory();
        inv.set(random.nextInt(inv.size()), key);
    }

    /** The vault's contents: a quiet lesson in what a Lockey is made of. */
    private static void fill(BurntChestBlockEntity chest, Random random, boolean sky) {
        List<ItemStack> loot = new ArrayList<>();
        Item[] junk = {Items.DIRT, Items.GRAVEL, Items.COBBLESTONE};
        for (int i = 3 + random.nextInt(2); i > 0; i--) loot.add(new ItemStack(junk[random.nextInt(3)], 32 + random.nextInt(33)));
        for (int i = 2 + random.nextInt(2); i > 0; i--) loot.add(new ItemStack(Items.IRON_NUGGET, 1 + random.nextInt(4)));
        for (int i = 2 + random.nextInt(2); i > 0; i--) loot.add(new ItemStack(Items.GOLD_NUGGET, 1 + random.nextInt(4)));
        loot.add(new ItemStack(Items.BONE, 1 + random.nextInt(3)));
        loot.add(new ItemStack(Items.STRING, 1 + random.nextInt(3)));
        loot.add(new ItemStack(Items.BREAD));
        loot.add(new ItemStack(Items.CORNFLOWER));

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 27; i++) if (i != CENTRE) slots.add(i);
        for (int i = slots.size() - 1; i > 0; i--) Collections.swap(slots, i, random.nextInt(i + 1));
        for (int i = 0; i < loot.size(); i++) chest.setStack(slots.get(i), loot.get(i));

        Item prize = Registries.ITEM.get(sky ? new Identifier("cloudboots", "cloud_boots")
                                             : new Identifier("hookshot", "cyan_hookshot"));
        if (prize != Items.AIR) chest.setStack(CENTRE, new ItemStack(prize));
    }
}
