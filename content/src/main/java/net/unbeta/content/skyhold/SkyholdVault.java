package net.unbeta.content.skyhold;

import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.unbeta.content.stronghold.StrongholdVault;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A Skyhold's vault plan, in the same shape as a stone stronghold's (see StrongholdVault). */
public final class SkyholdVault {

    private SkyholdVault() {}

    public static StrongholdVault.Plan plan(StructureStart start) {
        BlockPos vault = null;
        List<BlockPos> chests = new ArrayList<>();
        for (StructurePiece p : start.getChildren()) {
            if (p instanceof SkyholdVaultPiece v) vault = v.vaultPos();
            else if (p instanceof SkyholdChestPiece c) chests.add(c.pos());
        }
        if (vault == null) return null;
        if (chests.isEmpty()) return new StrongholdVault.Plan(vault, null, null, true);
        long mix = vault.asLong() * 0x9E3779B97F4A7C15L;
        BlockPos keyChest = chests.get((int) Math.floorMod(mix ^ (mix >>> 31), (long) chests.size()));
        UUID keyId = UUID.nameUUIDFromBytes(("unbeta:skyhold_vault:" + vault.asLong()).getBytes(StandardCharsets.UTF_8));
        return new StrongholdVault.Plan(vault, keyChest, keyId, true);
    }
}
