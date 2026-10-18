package net.unbeta.content.darkhold;

import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.unbeta.content.stronghold.StrongholdVault;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A Darkhold's vault plan, in the same shape as a stone stronghold's (see StrongholdVault). */
public final class DarkholdVault {

    /** The Darkhold vault's centre-slot prize (a placeholder until the real one is decided). */
    public static final Identifier PRIZE = new Identifier("minecraft", "diamond_block");

    private DarkholdVault() {}

    public static StrongholdVault.Plan plan(StructureStart start) {
        BlockPos vault = null;
        List<BlockPos> chests = new ArrayList<>();
        for (StructurePiece p : start.getChildren()) {
            if (!(p instanceof DarkholdRoomPiece room)) continue;
            for (BlockPos gate : room.markers("endgate")) vault = DarkholdRoomPiece.vaultAt(gate);
            chests.addAll(room.markers("chest"));
        }
        if (vault == null) return null;
        if (chests.isEmpty()) return new StrongholdVault.Plan(vault, null, null, PRIZE);
        long mix = vault.asLong() * 0x9E3779B97F4A7C15L;
        BlockPos keyChest = chests.get((int) Math.floorMod(mix ^ (mix >>> 31), (long) chests.size()));
        UUID keyId = UUID.nameUUIDFromBytes(("unbeta:darkhold_vault:" + vault.asLong()).getBytes(StandardCharsets.UTF_8));
        return new StrongholdVault.Plan(vault, keyChest, keyId, PRIZE);
    }
}
