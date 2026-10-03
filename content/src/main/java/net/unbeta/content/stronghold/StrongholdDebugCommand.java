package net.unbeta.content.stronghold;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;

/**
 * /unbeta stronghold - says whether you're inside the stronghold, and which piece.
 * A way to see the metric directly before anything is built on top of it.
 */
public final class StrongholdDebugCommand {

    private StrongholdDebugCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("unbeta")
                .then(CommandManager.literal("stronghold").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    BlockPos pos = BlockPos.ofFloored(src.getPosition());
                    StructureStart start = StrongholdSpace.startAt(src.getWorld(), pos);
                    StructurePiece piece = StrongholdSpace.pieceAt(src.getWorld(), pos);

                    if (piece != null) {
                        BlockBox b = piece.getBoundingBox();
                        src.sendFeedback(() -> Text.literal("INSIDE the stronghold")
                                .formatted(Formatting.GREEN)
                                .append(Text.literal(" - piece " + piece.getType()
                                        + " [" + b.getMinX() + "," + b.getMinY() + "," + b.getMinZ()
                                        + " to " + b.getMaxX() + "," + b.getMaxY() + "," + b.getMaxZ() + "]")
                                        .formatted(Formatting.GRAY)), false);
                    } else if (start != null) {
                        // Only reachable in the margin: vanilla's own check said no, ours said yes.
                        src.sendFeedback(() -> Text.literal("Just outside a piece (within the doorway margin)")
                                .formatted(Formatting.YELLOW), false);
                    } else {
                        src.sendFeedback(() -> Text.literal("Not in a stronghold")
                                .formatted(Formatting.RED), false);
                    }
                    return 1;
                })));
    }
}
