package net.unbeta.content.goldreath;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.unbeta.content.skyhold.SkyholdSpace;

/**
 * Goldreaths appear only within a Skyhold's footprint (where its fog begins), never in Peaceful:
 * at most 3 near a player, 24-48 blocks away and 10-36 blocks above them - so even a player on
 * the ground beneath a Skyhold has something to ride up on.
 */
public final class GoldreathSpawner {

    private static final int MAX_NEARBY = 3;

    private GoldreathSpawner() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(GoldreathSpawner::tick);
    }

    private static void tick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % 40 != 0) return;
        if (world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) return;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator() || !SkyholdSpace.inFootprint(world, player.getBlockPos())) continue;
            if (world.getRandom().nextInt(4) != 0) continue;
            if (world.getEntitiesByType(GoldreathRegistry.GOLDREATH, player.getBoundingBox().expand(96), e -> true).size() >= MAX_NEARBY) continue;

            double angle = world.getRandom().nextDouble() * Math.PI * 2;
            double dist = 24 + world.getRandom().nextInt(25);
            double x = player.getX() + Math.cos(angle) * dist;
            double z = player.getZ() + Math.sin(angle) * dist;
            double y = Math.min(300, player.getY() + 10 + world.getRandom().nextInt(27));
            BlockPos pos = BlockPos.ofFloored(x, y, z);
            if (!world.isChunkLoaded(pos) || !SkyholdSpace.inFootprint(world, pos)) continue;

            GoldreathEntity g = GoldreathRegistry.GOLDREATH.create(world);
            if (g == null) continue;
            g.refreshPositionAndAngles(x, y, z, world.getRandom().nextFloat() * 360F, 0F);
            if (world.isSpaceEmpty(g)) world.spawnEntity(g);
        }
    }
}
