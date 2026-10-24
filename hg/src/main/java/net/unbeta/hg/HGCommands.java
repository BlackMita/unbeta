package net.unbeta.hg;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Operator commands for running and testing rounds:
 * <ul>
 *   <li>/hg status - the phase, clocks and who's in</li>
 *   <li>/hg start - skip the rest of the lobby: countdown now</li>
 *   <li>/hg end - end the round now (ranked winner), then a new world</li>
 *   <li>/hg out &lt;player&gt; - put a player out</li>
 *   <li>/hg test &lt;true|false&gt; - test mode: one player is enough to start</li>
 *   <li>/hg dummy add [count] | /hg dummy remove - wandering dummy players</li>
 * </ul>
 * And for everyone: /surrender, which asks you to click to confirm (as does the cage's button).
 */
public final class HGCommands {

    private HGCommands() {}

    /** "Surrender and be out of this round? [Yes, I surrender]" - from /surrender or the cage's button. */
    public static void surrenderPrompt(ServerPlayerEntity p) {
        Text yes = Text.literal("[Yes, I surrender]").styled(st -> st.withColor(Formatting.RED).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/surrender confirm"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("Puts you out of this round for good, and drops everything you carry"))));
        p.sendMessage(Text.literal("Surrender and be out of this round? ").formatted(Formatting.GRAY).append(yes));
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("surrender")
                        .executes(ctx -> {
                            surrenderPrompt(ctx.getSource().getPlayerOrThrow());
                            return 1;
                        })
                        .then(CommandManager.literal("confirm").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            String refusal = Round.surrender(ctx.getSource().getServer(), p);
                            if (refusal != null) p.sendMessage(Text.literal(refusal).formatted(Formatting.GRAY));
                            return 1;
                        }))));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("hg").requires(src -> src.hasPermissionLevel(2))
                        .then(CommandManager.literal("perf").executes(ctx -> say(ctx.getSource(), Perf.report())))
                        .then(CommandManager.literal("cage")
                                .then(CommandManager.literal("save").executes(ctx ->
                                        say(ctx.getSource(), Cage.save(ctx.getSource().getServer().getOverworld()))))
                                .then(CommandManager.literal("default").executes(ctx -> say(ctx.getSource(), Cage.forget())))
                                .then(CommandManager.literal("rebuild").executes(ctx -> {
                                    var world = ctx.getSource().getServer().getOverworld();
                                    Cage.build(world, world.getSpawnPos());
                                    return say(ctx.getSource(), "Rebuilt the cage at spawn.");
                                })))
                        .then(CommandManager.literal("status").executes(ctx -> say(ctx.getSource(), Round.status(ctx.getSource().getServer()))))
                        .then(CommandManager.literal("start").executes(ctx -> {
                            MinecraftServer server = ctx.getSource().getServer();
                            if (Round.phase() != Round.Phase.LOBBY) return say(ctx.getSource(), "Not in the lobby.");
                            Round.startCountdown(server);
                            return 1;
                        }))
                        .then(CommandManager.literal("end").executes(ctx -> {
                            Round.end(ctx.getSource().getServer(), null, "ended by an operator");
                            return 1;
                        }))
                        .then(CommandManager.literal("out")
                                .then(CommandManager.argument("player", EntityArgumentType.player()).executes(ctx -> {
                                    ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "player");
                                    Round.putOut(ctx.getSource().getServer(), p, "put out by an operator");
                                    return 1;
                                })))
                        .then(CommandManager.literal("test")
                                .then(CommandManager.argument("on", BoolArgumentType.bool()).executes(ctx -> {
                                    Round.setTestMode(BoolArgumentType.getBool(ctx, "on"));
                                    return say(ctx.getSource(), "Test mode " + (Round.testMode() ? "on: one player is enough to start." : "off."));
                                })))
                        .then(CommandManager.literal("dummy")
                                .then(CommandManager.literal("add")
                                        .executes(ctx -> say(ctx.getSource(), Dummies.add(ctx.getSource().getServer(), 1) + " dummy joined."))
                                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 14)).executes(ctx ->
                                                say(ctx.getSource(), Dummies.add(ctx.getSource().getServer(),
                                                        IntegerArgumentType.getInteger(ctx, "count")) + " dummies joined."))))
                                .then(CommandManager.literal("remove").executes(ctx ->
                                        say(ctx.getSource(), Dummies.removeAll(ctx.getSource().getServer()) + " dummies removed."))))));
    }

    private static int say(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message), false);
        return 1;
    }
}
