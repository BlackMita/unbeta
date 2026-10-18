package net.unbeta.content.darkhold;

import net.minecraft.block.Blocks;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import net.unbeta.content.guardianpost.GuardianPostEntity;
import net.unbeta.content.guardianpost.GuardianPostRegistry;

/**
 * What stands at the centre of a Darkhold room (each "challenge" marker): an Endermite
 * spawner or a Guardian Post, about half and half, and now and then nothing at all.
 */
public final class DarkholdChallenges {

    /** Out of 100: below EMPTY nothing, below SPAWNER a spawner, otherwise a Guardian Post. */
    private static final int EMPTY = 12;
    private static final int SPAWNER = 56;

    private DarkholdChallenges() {}

    public static void place(ServerWorldAccess world, BlockPos pos, BlockBox box) {
        if (!box.contains(pos)) return;   // markers are offered to every chunk; act only in our own
        world.setBlockState(pos, Blocks.AIR.getDefaultState(), 2);
        int roll = (int) Math.floorMod(DarkholdBlocks.mix(world.toServerWorld().getSeed(), pos), 100L);
        if (roll < EMPTY) return;
        if (roll < SPAWNER) spawner(world, pos);
        else post(world, pos, (roll * 37) % 360);
    }

    /** An Endermite spawner that ignores light (the obsidian doors glow). */
    static void spawner(ServerWorldAccess world, BlockPos pos) {
        world.setBlockState(pos, Blocks.SPAWNER.getDefaultState(), 2);
        if (!(world.getBlockEntity(pos) instanceof MobSpawnerBlockEntity spawner)) return;
        NbtCompound entity = new NbtCompound();
        entity.putString("id", "minecraft:endermite");
        NbtCompound data = new NbtCompound();
        data.put("entity", entity);
        data.put("custom_spawn_rules", new NbtCompound());   // any light, any sky
        NbtCompound nbt = new NbtCompound();
        nbt.put("SpawnData", data);
        nbt.putShort("Delay", (short) 20);
        spawner.readNbt(nbt);
    }

    static void post(ServerWorldAccess world, BlockPos pos, float yaw) {
        GuardianPostEntity post = GuardianPostRegistry.GUARDIAN_POST.create(world.toServerWorld());
        if (post == null) return;
        post.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0.0F);
        post.setHeadYaw(yaw);
        post.setBodyYaw(yaw);
        post.setPersistent();
        world.spawnEntity(post);
    }
}
