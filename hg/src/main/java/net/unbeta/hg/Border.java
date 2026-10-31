package net.unbeta.hg;

import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LeverBlock;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.border.WorldBorder;

/**
 * The moving world border. The round's world is centred on 0,0 (the cage is built there) and the
 * border starts 445 blocks out. Through the lobby, countdown and grace minute it stands still;
 * from then on it is always moving, 1 block a second: away from spawn, until someone flips the
 * border switch in the cage, then toward it, never closing in tighter than the cage. Any player
 * still in the round can flip it, once every 30 seconds; nobody in Creative can.
 */
public final class Border {

    private Border() {}

    public static final int START_RADIUS = 445;

    /** On an island world, the border starts just far enough out to still see the island. */
    public static int startRadius(net.minecraft.server.MinecraftServer server) {
        return net.unbeta.content.island.Island.active() ? net.unbeta.content.island.Island.borderRadius(server) : START_RADIUS;
    }
    public static final int COOLDOWN_TICKS = 30 * 20;

    public static Block SWITCH;

    private static boolean shrinking;
    private static long lastFlip = -1_000_000;

    /** The block itself: registered on both sides (players' games need to draw it). */
    public static void registerBlock() {
        SWITCH = Registry.register(Registries.BLOCK, new Identifier(UnbetaHG.MOD_ID, "border_switch"),
                new BorderSwitchBlock(AbstractBlock.Settings.copy(Blocks.LEVER).strength(-1F, 3_600_000F).dropsNothing()
                        .pistonBehavior(net.minecraft.block.piston.PistonBehavior.BLOCK)));
    }

    /** A new round's world: border centred on 0,0, 445 out, standing still. */
    public static void setUp(ServerWorld world) {
        WorldBorder b = world.getWorldBorder();
        b.setCenter(0.5, 0.5);
        b.setSize(startRadius(world.getServer()) * 2 + 1);
        shrinking = false;
        lastFlip = -1_000_000;
    }

    public static boolean shrinking() {
        return shrinking;
    }

    /** Once a second through the game: one block out, or one block in (down to the cage). */
    public static void tick(MinecraftServer server) {
        ServerWorld world = server.getOverworld();
        if (world.getTime() % 20 != 0) return;
        Cage.keepSwitch(world);
        if (Round.phase() != Round.Phase.GAME) return;
        WorldBorder b = world.getWorldBorder();
        double size = b.getSizeLerpTarget();
        double min = Cage.SIZE + 2;
        double target = shrinking ? Math.max(min, size - 2) : size + 2;
        if (target != size) b.interpolateSize(b.getSize(), target, 1000L);
    }

    /** Someone pulled the switch. */
    public static void flip(ServerPlayerEntity p, ServerWorld world, BlockPos pos, BlockState state) {
        Round.Phase ph = Round.phase();
        if (p.isCreative()) {
            p.sendMessage(Text.literal("Nobody in Creative can flip the border switch.").formatted(Formatting.GRAY), true);
            return;
        }
        if (ph != Round.Phase.GRACE && ph != Round.Phase.GAME) {
            p.sendMessage(Text.literal("The border switch works once the round is under way.").formatted(Formatting.GRAY), true);
            return;
        }
        if (Round.isOut(p) || !Round.tookPart(p.getUuid())) {
            p.sendMessage(Text.literal("Only players still in the round can flip the border switch.").formatted(Formatting.GRAY), true);
            return;
        }
        long since = world.getTime() - lastFlip;
        if (since < COOLDOWN_TICKS) {
            p.sendMessage(Text.literal("The border switch is still settling: " + ((COOLDOWN_TICKS - since) / 20 + 1) + "s.").formatted(Formatting.GRAY), true);
            return;
        }
        shrinking = !shrinking;
        lastFlip = world.getTime();
        if (state.getBlock() instanceof LeverBlock) world.setBlockState(pos, state.with(LeverBlock.POWERED, shrinking), Block.NOTIFY_ALL);
        world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 1F, shrinking ? 0.6F : 0.5F);
        world.playSound(null, pos, SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.BLOCKS, 1F, shrinking ? 0.7F : 1.3F);
        Text title = Text.literal(shrinking ? "World border now moving TOWARD spawn!" : "World border now moving AWAY from spawn!")
                .formatted(shrinking ? Formatting.RED : Formatting.AQUA, Formatting.BOLD);
        Text sub = Text.literal(p.getName().getString() + " flipped the border switch").formatted(Formatting.GRAY);
        for (ServerPlayerEntity q : world.getServer().getPlayerManager().getPlayerList()) {
            q.networkHandler.sendPacket(new TitleFadeS2CPacket(5, 40, 10));
            q.networkHandler.sendPacket(new SubtitleS2CPacket(sub));
            q.networkHandler.sendPacket(new TitleS2CPacket(title));
        }
        if (ph == Round.Phase.GRACE) {
            p.sendMessage(Text.literal("The border starts moving when the grace minute ends.").formatted(Formatting.GRAY), true);
        }
    }

    /** "moving out, 903 across" for /hg status. */
    public static String describe(ServerWorld world) {
        return (Round.phase() == Round.Phase.GAME ? (shrinking ? "closing in" : "moving out") : "still")
                + ", " + (int) world.getWorldBorder().getSize() + " across";
    }
}
