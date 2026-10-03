package net.unbeta.content.stronghold;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** A player breaking stronghold stonework is one breach. Plus /unbeta breaches to list them. */
public final class BreachHooks {

    private BreachHooks() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (world instanceof ServerWorld sw && !player.isCreative()) {
                // AFTER fires once the block is gone: pass the state we were handed.
                Breach.record(sw, java.util.Map.of(pos.toImmutable(), state));
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> build(dispatcher));
    }

    private static void build(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("unbeta")
                .then(CommandManager.literal("breaches").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    List<Breach> all = Breach.load(src.getWorld());
                    if (all.isEmpty()) {
                        src.sendFeedback(() -> Text.literal("No outstanding breaches")
                                .formatted(Formatting.GRAY), false);
                        return 1;
                    }
                    src.sendFeedback(() -> Text.literal(all.size() + " outstanding breach(es):")
                            .formatted(Formatting.GOLD), false);
                    for (Breach b : all) {
                        src.sendFeedback(() -> Text.literal("  " + b.missing.size() + " block(s) at "
                                + b.centre.getX() + "," + b.centre.getY() + "," + b.centre.getZ())
                                .formatted(Formatting.GRAY), false);
                    }
                    return 1;
                })));
    }
}
