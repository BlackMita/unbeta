package net.unbeta.content.zombie;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * /unbeta sullied - reports on the chunk you're standing in: what it remembers, whether it
 * is night, how far below the surface you are (risers appear on the SURFACE), why the last
 * zombie death here was or wasn't remembered, and what the last beat did.
 */
public final class SulliedDebugCommand {

    private SulliedDebugCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> build(dispatcher));
    }

    private static void line(ServerCommandSource src, String text) {
        src.sendFeedback(() -> Text.literal(text).formatted(Formatting.GRAY), false);
    }

    private static String ago(ServerWorld world, SulliedChunkTick.Note note) {
        return note == null ? "nothing recorded since the game started"
                : note.text() + " (" + (world.getTime() - note.time()) / 20 + " s ago)";
    }

    private static void build(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("unbeta")
                .then(CommandManager.literal("sullied").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    ServerWorld world = src.getWorld();
                    BlockPos pos = BlockPos.ofFloored(src.getPosition());
                    ChunkPos chunk = new ChunkPos(pos);

                    List<String> queue = SulliedChunkState.getOrCreate(world).peek(chunk);
                    Map<String, Integer> counts = new TreeMap<>();
                    for (String id : queue) counts.merge(id, 1, Integer::sum);
                    StringBuilder what = new StringBuilder();
                    counts.forEach((id, n) -> what.append(what.length() > 0 ? ", " : "").append(id).append(" x").append(n));

                    line(src, "Chunk [" + chunk.x + ", " + chunk.z + "]: "
                            + (queue.isEmpty() ? "remembers nothing" : queue.size() + " remembered - " + what));
                    line(src, "It is " + (world.getAmbientDarkness() >= 4 ? "night: every 5 s beat raises one"
                            : "day: each 5 s beat raises one only 1 time in " + SulliedChunkTick.DAY_ODDS));

                    int surface = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
                    int below = surface - pos.getY();
                    line(src, "You are at y=" + pos.getY() + "; the surface here is y=" + surface
                            + (below > 3 ? " - you are " + below + " blocks below it. Risers appear on the SURFACE, "
                                    + "so you would not see them" : ""));

                    line(src, "Last death here: " + ago(world, SulliedChunkTick.lastDeath(world, chunk)));
                    line(src, "Last beat here: " + ago(world, SulliedChunkTick.lastOutcome(world, chunk)));
                    return 1;
                })));
    }
}
