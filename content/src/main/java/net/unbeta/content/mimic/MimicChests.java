package net.unbeta.content.mimic;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.world.LightType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import net.unbeta.content.burntchest.BurntChestBlock;
import net.unbeta.content.stronghold.StrongholdVault;

/**
 * Mimics, Unbeta's way (built on Better Mimic):
 * <ul>
 *   <li>They never spawn naturally - Better Mimic's biome spawns are removed.</li>
 *   <li>In an abandoned village (the ones with cobwebs), 1 loot chest in 3 is a mimic, so most
 *       have at least one. Ordinary villages never have mimics.</li>
 *   <li>1 in 8 generated loot chests (dungeons and the like) becomes a disguised mimic instead, facing the same
 *       way. Fixed per position and seed. Double chests, underwater chests and the
 *       stronghold's Burnt Chest vault are never replaced.</li>
 *   <li>If the replaced chest was the stronghold's key chest, the mimic swallows the key.</li>
 *   <li>Replacement mimics never despawn.</li>
 * </ul>
 * Everything is inert if Better Mimic isn't installed.
 */
public final class MimicChests {

    public static final Identifier MIMIC_ID = new Identifier("bettermimic", "mimic");
    private static final int ODDS = 8;

    private MimicChests() {}

    public static EntityType<?> mimicType() {
        return Registries.ENTITY_TYPE.containsId(MIMIC_ID) ? Registries.ENTITY_TYPE.get(MIMIC_ID) : null;
    }

    public static void register() {
        BiomeModifications.create(new Identifier("unbeta-content", "no_natural_mimics"))
                .add(ModificationPhase.REMOVALS, BiomeSelectors.all(), ctx -> {
                    EntityType<?> type = mimicType();
                    if (type != null) ctx.getSpawnSettings().removeSpawnsOfEntityType(type);
                });
    }

    /** Dark enough for monsters to spawn: no block light, and sky light (after night/rain dimming) of 7 or less. */
    public static boolean isDark(World world, BlockPos pos) {
        int block = world.getLightLevel(LightType.BLOCK, pos);
        int sky = world.getLightLevel(LightType.SKY, pos) - world.getAmbientDarkness();
        return block == 0 && sky <= 7;
    }

    /** Called during world generation right after a loot chest is placed. True if it became a mimic. */
    public static boolean tryReplace(ServerWorldAccess world, BlockPos pos) {
        if (!(world instanceof ChunkRegion region)) return false; // generation only
        BlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock) || state.getBlock() instanceof BurntChestBlock) return false;
        if (state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE || state.get(ChestBlock.WATERLOGGED)) return false;

        long mix = region.getSeed() ^ (pos.asLong() * 0x9E3779B97F4A7C15L);
        mix ^= mix >>> 29;
        mix *= 0xBF58476D1CE4E5B9L;
        mix ^= mix >>> 32;
        if (Math.floorMod(mix, (long) ODDS) != 0) return false;

        float yaw = state.get(ChestBlock.FACING).asRotation();
        MobEntity mob = makeMimic(region, pos, yaw);
        if (mob == null) return false;
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        ItemStack key = StrongholdVault.keyFor(pos);
        if (!key.isEmpty()) ((MimicAccess) mob).unbeta_contents().add(key);
        world.spawnEntity(mob);
        return true;
    }

    /** A disguised mimic sitting where a chest would be, facing the same way (not yet spawned), or null. */
    private static MobEntity makeMimic(ChunkRegion region, BlockPos pos, float yaw) {
        EntityType<?> type = mimicType();
        if (type == null) return null;
        Entity entity = type.create(region.toServerWorld());
        if (!(entity instanceof MobEntity mob) || !(entity instanceof MimicAccess)) return null;
        mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        mob.setBodyYaw(yaw);
        mob.setHeadYaw(yaw);
        mob.setPersistent();
        return mob;
    }

    /** Abandoned villages: 1 chest in 3 is a mimic (so most such villages have one). */
    private static final int ABANDONED_ODDS = 3;

    /**
     * A chest in an abandoned village's building, about to be placed: maybe it's a mimic instead.
     * If so the mimic is spawned there now, and true says the chest itself must be left out.
     */
    public static boolean abandonedChest(ServerWorldAccess world, BlockPos pos, BlockState chest) {
        if (!(world instanceof ChunkRegion region)) return false;
        if (!(chest.getBlock() instanceof ChestBlock) || chest.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE
                || chest.get(ChestBlock.WATERLOGGED)) return false;
        long mix = region.getSeed() ^ (pos.asLong() * 0xD1B54A32D192ED03L);
        mix ^= mix >>> 31;
        mix *= 0x94D049BB133111EBL;
        mix ^= mix >>> 29;
        if (Math.floorMod(mix, (long) ABANDONED_ODDS) != 0) return false;
        MobEntity mob = makeMimic(region, pos, chest.get(ChestBlock.FACING).asRotation());
        if (mob == null) return false;
        world.spawnEntity(mob);
        return true;
    }
}
