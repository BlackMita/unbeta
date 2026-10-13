package net.unbeta.content.arrow;

import net.fabricmc.fabric.api.registry.FlammableBlockRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.unbeta.content.UnbetaContent;
import net.unbeta.content.block.FireColor;
import net.unbeta.content.block.ObsidianFireBlock;
import net.unbeta.content.jackolantern.JackOLanternLogic;
import net.unbeta.content.jackolantern.UnbetaJackOLanternBlock;
import net.unbeta.content.jackolantern.UnbetaLitJackOLanternBlock;
import net.unbeta.content.torch.TorchLogic;
import net.unbeta.content.torch.UnbetaTorchBlock;
import net.unbeta.content.torch.UnbetaWallTorchBlock;

/**
 * Fire arrows.
 *
 * <ul>
 *   <li>An arrow flying through a lit Unbeta torch (or any fire) catches fire. Through
 *       obsidian fire, it also carries that fire's colour.</li>
 *   <li>A burning arrow lights the unlit torches it flies through, and the unlit
 *       Jack o'Lantern it hits. Burning mobs it hits is vanilla.</li>
 *   <li>Landing on flammable blocks: the Burnt mod chars or re-embers the block it hits
 *       (and its neighbours) itself, from the arrow being on fire; without Burnt, ordinary
 *       fire is lit against the face it hit.</li>
 *   <li>A coloured arrow landing on obsidian with open air above lights obsidian fire of
 *       that colour on top.</li>
 *   <li>A few ticks after landing alight, the arrow burns out: it turns black and can no
 *       longer be picked up (see FireArrowMixin).</li>
 * </ul>
 */
public final class FireArrows {

    public static final int NO_COLOR = -1;
    public static final int BURN_SECONDS = 100;       // like a Flame bow's arrows
    public static final int CHAR_AFTER_TICKS = 10;    // landed and still burning this long -> charred
    private static final double STEP = 0.2;

    private FireArrows() {}

    /**
     * Walks the stretch the arrow is about to fly this tick, block by block, and returns the
     * fire colour it carries afterwards (NO_COLOR for plain fire or none).
     */
    public static int sweep(ServerWorld world, PersistentProjectileEntity arrow, Vec3d from, Vec3d to, int color) {
        Vec3d delta = to.subtract(from);
        double len = delta.length();
        int steps = Math.max(1, (int) Math.ceil(len / STEP));
        BlockPos last = null;
        for (int i = 0; i <= steps; i++) {
            BlockPos pos = BlockPos.ofFloored(from.add(delta.multiply(i / (double) steps)));
            if (pos.equals(last)) continue;
            last = pos;
            BlockState state = world.getBlockState(pos);
            Block block = state.getBlock();

            if (block instanceof UnbetaTorchBlock || block instanceof UnbetaWallTorchBlock) {
                if (state.get(TorchLogic.LIT)) {
                    ignite(arrow);
                    color = NO_COLOR;
                } else if (arrow.isOnFire()) {
                    TorchLogic.lightPlaced(world, pos, state);
                }
            } else if (block instanceof ObsidianFireBlock) {
                ignite(arrow);
                color = state.get(ObsidianFireBlock.COLOR).ordinal();
            } else if (block instanceof AbstractFireBlock || state.isOf(Blocks.LAVA)) {
                ignite(arrow);
                color = NO_COLOR;
            }
        }
        return arrow.isOnFire() ? color : NO_COLOR;
    }

    private static void ignite(PersistentProjectileEntity arrow) {
        if (!arrow.isWet()) arrow.setOnFireFor(BURN_SECONDS);
    }

    /** A burning arrow has just hit a block. */
    public static void onBlockHit(ServerWorld world, PersistentProjectileEntity arrow, BlockHitResult hit, int color) {
        if (!arrow.isOnFire()) return;
        BlockPos pos = hit.getBlockPos();
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();

        if ((block instanceof UnbetaJackOLanternBlock || block instanceof UnbetaLitJackOLanternBlock)
                && !state.get(JackOLanternLogic.LIT)) {
            JackOLanternLogic.lightPlaced(world, pos, state);
            return;
        }

        if (state.isOf(Blocks.OBSIDIAN)) {
            if (color < 0 || color >= FireColor.VALUES.length || UnbetaContent.OBSIDIAN_FIRE == null) return;
            BlockPos up = pos.up();
            if (!world.isAir(up)) return;
            BlockState fire = UnbetaContent.OBSIDIAN_FIRE.getDefaultState().with(ObsidianFireBlock.COLOR, FireColor.VALUES[color]);
            if (fire.canPlaceAt(world, up)) world.setBlockState(up, fire, Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
            return;
        }

        // With Burnt installed, its own burning-arrow handling does the rest.
        if (FabricLoader.getInstance().isModLoaded("burnt")) return;
        FlammableBlockRegistry.Entry entry = FlammableBlockRegistry.getDefaultInstance().get(block);
        if (entry == null || entry.getBurnChance() <= 0) return;
        Direction side = hit.getSide();
        BlockPos at = pos.offset(side);
        if (world.isAir(at) && AbstractFireBlock.canPlaceAt(world, at, side)) {
            world.setBlockState(at, AbstractFireBlock.getState(world, at), Block.NOTIFY_ALL | Block.REDRAW_ON_MAIN_THREAD);
        }
    }
}
