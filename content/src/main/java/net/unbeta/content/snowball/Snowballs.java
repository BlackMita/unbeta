package net.unbeta.content.snowball;

import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldEvents;
import net.unbeta.content.jackolantern.JackOLanternLogic;
import net.unbeta.content.jackolantern.UnbetaJackOLanternBlock;
import net.unbeta.content.jackolantern.UnbetaLitJackOLanternBlock;
import net.unbeta.content.torch.TorchLogic;
import net.unbeta.content.torch.UnbetaTorchBlock;
import net.unbeta.content.torch.UnbetaWallTorchBlock;

import java.lang.reflect.Method;

/**
 * Snowballs put things out. A thrown snowball snuffs the first fire, obsidian fire, lit Unbeta
 * torch or lit Jack o'Lantern it flies into (and breaks on it), cools a hot Burnt block it
 * hits, and puts out a burning mob or player it hits.
 */
public final class Snowballs {

    private static final TagKey<Block> BURNT_FIRE = TagKey.of(RegistryKeys.BLOCK, new Identifier("burnt", "fire"));
    private static final double STEP = 0.2;
    private static Method burntExtinguish;
    private static boolean burntLooked;

    private Snowballs() {}

    /** Puts out whatever is burning at pos. True if something was put out. */
    public static boolean snuff(ServerWorld world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        Block b = s.getBlock();
        if (s.isIn(BURNT_FIRE)) {
            if (!burntSplash(world, pos)) world.removeBlock(pos, false);
            world.syncWorldEvent(WorldEvents.FIRE_EXTINGUISHED, pos, 0);
            return true;
        }
        if (b instanceof AbstractFireBlock) {   // vanilla fire and obsidian fire
            world.removeBlock(pos, false);
            world.syncWorldEvent(WorldEvents.FIRE_EXTINGUISHED, pos, 0);
            return true;
        }
        if ((b instanceof UnbetaTorchBlock || b instanceof UnbetaWallTorchBlock) && s.get(TorchLogic.LIT)) {
            TorchLogic.extinguishPlaced(world, pos, s);
            return true;
        }
        if ((b instanceof UnbetaJackOLanternBlock || b instanceof UnbetaLitJackOLanternBlock) && s.get(JackOLanternLogic.LIT)) {
            JackOLanternLogic.extinguishPlaced(world, pos, s);
            return true;
        }
        Property<?> hot = b.getStateManager().getProperty("hot");   // Burnt's still-hot sooty blocks
        if (hot instanceof BooleanProperty bp && s.get(bp)) {
            world.setBlockState(pos, s.with(bp, false), Block.NOTIFY_ALL);
            world.syncWorldEvent(WorldEvents.FIRE_EXTINGUISHED, pos, 0);
            return true;
        }
        return false;
    }

    /** Checks each block on the stretch the snowball is about to fly; true if it snuffed something. */
    public static boolean sweep(ServerWorld world, SnowballEntity snowball, Vec3d from, Vec3d to) {
        Vec3d delta = to.subtract(from);
        int steps = Math.max(1, (int) Math.ceil(delta.length() / STEP));
        BlockPos last = null;
        for (int i = 0; i <= steps; i++) {
            BlockPos pos = BlockPos.ofFloored(from.add(delta.multiply(i / (double) steps)));
            if (pos.equals(last)) continue;
            last = pos;
            if (snuff(world, pos)) return true;
        }
        return false;
    }

    /** Burnt's own splash-water extinguish, so its fire bookkeeping stays right. */
    private static boolean burntSplash(ServerWorld world, BlockPos pos) {
        if (!burntLooked) {
            burntLooked = true;
            try {
                burntExtinguish = Class.forName("net.pixelbank.burnt.procedures.ExtinguishBlockProcedure")
                        .getMethod("execute", WorldAccess.class, double.class, double.class, double.class, boolean.class);
            } catch (Throwable ignored) {
                burntExtinguish = null;
            }
        }
        if (burntExtinguish == null) return false;
        try {
            burntExtinguish.invoke(null, world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, false);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
