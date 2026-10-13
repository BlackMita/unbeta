package net.unbeta.content.obsidiandoor;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BlockSetType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.world.event.GameEvent;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Obsidian Door.
 *
 * <p>Pass 1: a door that floats (no support block needed), glows at redstone-torch level,
 * and is obsidian-tough. The XP gate comes in pass 2.
 */
public class ObsidianDoorBlock extends DoorBlock {

    public ObsidianDoorBlock(Settings settings, BlockSetType blockSetType) {
        super(settings, blockSetType);
    }

    /**
     * Floating: always placeable, never pops off. Overriding canPlaceAt to always return
     * true stops the vanilla "destroy when block below is gone" logic in
     * getStateForNeighborUpdate, since that path is guarded by !canPlaceAt.
     */
    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return true;
    }

    public static final int XP_COST = 550;

    /**
     * Manual right-click. Opening is gated on the nearest player's XP; closing releases
     * the stored XP. We override onUse entirely because vanilla's toggles the blockstate
     * directly without routing through setOpen.
     */
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        boolean isOpen = state.get(OPEN);
        if (!world.isClient) {
            if (!isOpen) {
                // Opening: charge the player
                if (!net.unbeta.content.obsidiandoor.XpHelper.canAfford(player, XP_COST)) {
                    world.playSound(null, pos,
                            net.unbeta.content.lockey.LockeyRegistry.CHEST_DENY,
                            SoundCategory.BLOCKS, 0.8F, 1.0F);
                    player.sendMessage(Text.literal("This door needs more experience to open.")
                            .formatted(Formatting.GREEN), false);
                    return ActionResult.SUCCESS;
                }
                player.addExperience(-XP_COST);
            } else {
                // Closing: release the stored XP as orbs
                ExperienceOrbEntity.spawn((ServerWorld) world,
                        new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                        XP_COST);
            }
            BlockState newState = state.cycle(OPEN);
            world.setBlockState(pos, newState, Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD);
            playOpenCloseSound(player, world, pos, newState.get(OPEN));
            world.emitGameEvent(player, newState.get(OPEN)
                    ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
        }
        return ActionResult.success(world.isClient);
    }

    /**
     * If an open door is broken, release the stored XP as orbs before it disappears.
     * Only fires on the lower half to avoid spawning orbs twice (doors are two blocks).
     */
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient
                && state.get(OPEN)
                && state.get(HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER) {
            ExperienceOrbEntity.spawn((ServerWorld) world,
                    new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                    XP_COST);
        }
        super.onBreak(world, pos, state, player);
    }

    /** How far a redstone-powered door looks for someone to pay (or be repaid) its experience. */
    public static final double REDSTONE_PAYER_RANGE = 16.0;

    /**
     * Redstone (buttons, levers, plates...) can open the door only if a player within 16 blocks
     * has the 550 points (level 20) to pay - the nearest such player pays. Closing by redstone
     * releases the stored experience as orbs, as closing by hand does. A Lockey-locked door
     * ignores redstone entirely.
     */
    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
                               BlockPos sourcePos, boolean notify) {
        if (world.isClient || !(world instanceof ServerWorld sw)) return;
        boolean powered = world.isReceivingRedstonePower(pos) || world.isReceivingRedstonePower(
                pos.offset(state.get(HALF) == net.minecraft.block.enums.DoubleBlockHalf.LOWER
                        ? net.minecraft.util.math.Direction.UP : net.minecraft.util.math.Direction.DOWN));
        if (getDefaultState().isOf(sourceBlock) || powered == state.get(POWERED)) return;

        BlockState next = state.with(POWERED, powered);
        BlockPos lower = net.unbeta.content.lockey.LockeyDoors.lowerHalf(pos, state);
        boolean locked = net.unbeta.content.lockey.LockeyDoors.lockedBy(sw, lower) != null;
        if (powered != state.get(OPEN) && !locked) {
            Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            boolean swing = true;
            if (powered) {
                PlayerEntity payer = world.getClosestPlayer(center.x, center.y, center.z, REDSTONE_PAYER_RANGE,
                        e -> e instanceof PlayerEntity p && !p.isSpectator()
                                && net.unbeta.content.obsidiandoor.XpHelper.canAfford(p, XP_COST));
                if (payer == null) {
                    swing = false;
                    world.playSound(null, pos, net.unbeta.content.lockey.LockeyRegistry.CHEST_DENY,
                            SoundCategory.BLOCKS, 0.8F, 1.0F);
                } else {
                    payer.addExperience(-XP_COST);
                }
            } else {
                ExperienceOrbEntity.spawn(sw, center, XP_COST);
            }
            if (swing) {
                next = next.with(OPEN, powered);
                playOpenCloseSound(null, world, pos, powered);
                world.emitGameEvent(null, powered ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            }
        }
        world.setBlockState(pos, next, Block.NOTIFY_LISTENERS);
    }

    private void playOpenCloseSound(PlayerEntity player, World world, BlockPos pos, boolean open) {
        world.playSound(null, pos,
                open ? net.unbeta.content.obsidiandoor.ObsidianDoorRegistry.DOOR_OPEN
                     : net.unbeta.content.obsidiandoor.ObsidianDoorRegistry.DOOR_CLOSE,
                SoundCategory.BLOCKS, 1.0F, 1.0F);
    }
}
