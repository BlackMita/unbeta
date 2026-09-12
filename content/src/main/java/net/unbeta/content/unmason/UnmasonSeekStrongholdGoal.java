package net.unbeta.content.unmason;

import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.gen.structure.Structure;

import java.util.EnumSet;

public class UnmasonSeekStrongholdGoal extends Goal {

    private final UnmasonEntity unmason;
    private BlockPos strongholdPos = null;
    private int searchCooldown = 0;

    public UnmasonSeekStrongholdGoal(UnmasonEntity unmason) {
        this.unmason = unmason;
        this.setControls(EnumSet.of(Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (!(unmason.getWorld() instanceof ServerWorld sw)) return false;
        // Only activate if a player is within 8 blocks
        PlayerEntity player = sw.getClosestPlayer(
                unmason.getX(), unmason.getY(), unmason.getZ(), 8.0, false);
        if (player == null) return false;
        // Find stronghold if we don't have one cached
        if (strongholdPos == null && searchCooldown <= 0) {
            locateStronghold(sw);
            searchCooldown = 200; // don't search every tick
        }
        return strongholdPos != null;
    }

    @Override
    public boolean shouldContinue() {
        if (!(unmason.getWorld() instanceof ServerWorld sw)) return false;
        PlayerEntity player = sw.getClosestPlayer(
                unmason.getX(), unmason.getY(), unmason.getZ(), 8.0, false);
        return player != null && strongholdPos != null;
    }

    @Override
    public void tick() {
        if (searchCooldown > 0) searchCooldown--;
        if (strongholdPos == null) return;
        unmason.getNavigation().startMovingTo(
                strongholdPos.getX(), unmason.getY(), strongholdPos.getZ(), 1.0);
    }

    @Override
    public void stop() {
        unmason.getNavigation().stop();
    }

    private void locateStronghold(ServerWorld world) {
        var result = world.locateStructure(
                net.minecraft.registry.tag.StructureTags.EYE_OF_ENDER_LOCATED,
                unmason.getBlockPos(), 100, false);
        if (result != null) {
            strongholdPos = result;
        }
    }
}
