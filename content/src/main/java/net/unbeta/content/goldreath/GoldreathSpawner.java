package net.unbeta.content.goldreath;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.unbeta.content.skyhold.SkyholdSpace;

/**
 * Where Goldreaths appear (never in Peaceful):
 * <ul>
 *   <li>Within a Skyhold's footprint: hostile ones, at most 3 near a player, 24-48 blocks away
 *       and 10-36 above - so even a player on the ground beneath has something to ride up on.</li>
 *   <li>Anywhere else, rarely: a lone silent GUIDE. Every 30 s outdoors a 1-in-20 roll; it needs
 *       a Skyhold within ~1000 blocks and no other Goldreath nearby.</li>
 * </ul>
 */
public final class GoldreathSpawner {

    private static final int MAX_NEARBY = 3;
    private static final int GUIDE_ODDS = 20;          // per 30 s outdoors - about one every 10 minutes

    private GoldreathSpawner() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(GoldreathSpawner::tick);
    }

    private static void tick(ServerWorld world) {
        if (world.getRegistryKey() != World.OVERWORLD || world.getTime() % 40 != 0) return;
        if (world.getDifficulty() == Difficulty.PEACEFUL || !world.getGameRules().getBoolean(GameRules.DO_MOB_SPAWNING)) return;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) continue;
            if (SkyholdSpace.inFootprint(world, player.getBlockPos())) spawnHostile(world, player);
            else if (world.getTime() % 600 == 0) tryGuide(world, player);
        }
    }

    private static void spawnHostile(ServerWorld world, ServerPlayerEntity player) {
        if (world.getRandom().nextInt(4) != 0) return;
        if (world.getEntitiesByType(GoldreathRegistry.GOLDREATH, player.getBoundingBox().expand(96), e -> true).size() >= MAX_NEARBY) return;

        double angle = world.getRandom().nextDouble() * Math.PI * 2;
        double dist = 24 + world.getRandom().nextInt(25);
        double x = player.getX() + Math.cos(angle) * dist;
        double z = player.getZ() + Math.sin(angle) * dist;
        double y = Math.min(300, player.getY() + 10 + world.getRandom().nextInt(27));
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        if (!world.isChunkLoaded(pos) || !SkyholdSpace.inFootprint(world, pos)) return;

        GoldreathEntity g = GoldreathRegistry.GOLDREATH.create(world);
        if (g == null) return;
        g.refreshPositionAndAngles(x, y, z, world.getRandom().nextFloat() * 360F, 0F);
        if (world.isSpaceEmpty(g)) world.spawnEntity(g);
    }

    private static void tryGuide(ServerWorld world, ServerPlayerEntity player) {
        if (world.getRandom().nextInt(GUIDE_ODDS) != 0) return;
        if (!world.isSkyVisible(player.getBlockPos().up())) return;
        if (!world.getEntitiesByType(GoldreathRegistry.GOLDREATH, player.getBoundingBox().expand(128), e -> true).isEmpty()) return;

        BlockPos skyhold = world.locateStructure(GoldreathEntity.SKYHOLDS, player.getBlockPos(), 64, false);
        if (skyhold == null) return;
        double sx = skyhold.getX() - player.getX(), sz = skyhold.getZ() - player.getZ();
        if (sx * sx + sz * sz < 150 * 150) return;   // already close: no need for a guide

        double angle = world.getRandom().nextDouble() * Math.PI * 2;
        double dist = 20 + world.getRandom().nextInt(13);
        double x = player.getX() + Math.cos(angle) * dist;
        double z = player.getZ() + Math.sin(angle) * dist;
        BlockPos column = BlockPos.ofFloored(x, player.getY(), z);
        if (!world.isChunkLoaded(column)) return;
        int ground = world.getTopY(Heightmap.Type.MOTION_BLOCKING, column.getX(), column.getZ());
        double y = Math.min(300, Math.max(player.getY() + 8 + world.getRandom().nextInt(9), ground + 6));

        GoldreathEntity g = GoldreathRegistry.GOLDREATH.create(world);
        if (g == null) return;
        g.refreshPositionAndAngles(x, y, z, world.getRandom().nextFloat() * 360F, 0F);
        g.setSkyholdTarget(skyhold);
        if (world.isSpaceEmpty(g)) world.spawnEntity(g);
    }
}
