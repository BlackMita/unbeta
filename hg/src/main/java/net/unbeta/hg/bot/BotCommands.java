package net.unbeta.hg.bot;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.unbeta.hg.Dummies;
import net.unbeta.hg.DummyPlayer;

import java.util.List;

/**
 * /hg bot ... - adding bots, giving them orders, and seeing inside their heads.
 * <ul>
 *   <li>/hg bot add [count] [traits] - e.g. /hg bot add 3, or /hg bot add 1 lower lightener hare</li>
 *   <li>/hg bot come &lt;bot&gt; - walk to you</li>
 *   <li>/hg bot goto &lt;bot&gt; &lt;x y z&gt;</li>
 *   <li>/hg bot mine &lt;bot&gt; - break the block you're looking at</li>
 *   <li>/hg bot craft &lt;bot&gt; &lt;item&gt; - make it, crafting whatever it needs on the way</li>
 *   <li>/hg bot smelt &lt;bot&gt; - smelt what it carries (makes and places a furnace if needed)</li>
 *   <li>/hg bot stop &lt;bot&gt;, /hg bot what &lt;bot&gt;, /hg bot remove</li>
 *   <li>/hg bot why &lt;bot&gt; - what it's doing and why, and its last 10 Reactions</li>
 *   <li>/hg bot notebook &lt;bot&gt; - what it remembers seeing</li>
 *   <li>/hg bot brains on|off - every bot thinks for itself (on) or only follows orders (off)</li>
 * </ul>
 */
public final class BotCommands {

    private BotCommands() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("hg").requires(src -> src.hasPermissionLevel(2)).then(CommandManager.literal("bot")
                        .then(CommandManager.literal("add")
                                .executes(ctx -> add(ctx.getSource(), 1, null))
                                .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 15))
                                        .executes(ctx -> add(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"), null))
                                        .then(CommandManager.argument("traits", StringArgumentType.greedyString())
                                                .executes(ctx -> add(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "count"),
                                                        StringArgumentType.getString(ctx, "traits"))))))
                        .then(CommandManager.literal("remove").executes(ctx ->
                                say(ctx.getSource(), Dummies.removeAll(ctx.getSource().getServer()) + " bots and dummies removed.")))
                        .then(CommandManager.literal("come").then(bot().executes(ctx -> {
                            Body b = body(ctx);
                            b.start(new Orders.Come(ctx.getSource().getPlayerOrThrow()));
                            return say(ctx.getSource(), name(b) + ": " + b.doing());
                        })))
                        .then(CommandManager.literal("goto").then(bot().then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                .executes(ctx -> {
                                    Body b = body(ctx);
                                    b.start(new Orders.GoTo(BlockPosArgumentType.getLoadedBlockPos(ctx, "pos")));
                                    return say(ctx.getSource(), name(b) + ": " + b.doing());
                                }))))
                        .then(CommandManager.literal("mine").then(bot().executes(ctx -> {
                            Body b = body(ctx);
                            HitResult hit = ctx.getSource().getPlayerOrThrow().raycast(64.0, 0F, false);
                            if (!(hit instanceof BlockHitResult bh) || hit.getType() != HitResult.Type.BLOCK) {
                                return say(ctx.getSource(), "Look at the block you want mined (within 64 blocks).");
                            }
                            b.start(new Orders.Mine(bh.getBlockPos()));
                            return say(ctx.getSource(), name(b) + ": " + b.doing());
                        })))
                        .then(CommandManager.literal("craft").then(bot().then(CommandManager.argument("item", ItemStackArgumentType.itemStack(registryAccess))
                                .executes(ctx -> {
                                    Body b = body(ctx);
                                    b.start(new Orders.Craft(ItemStackArgumentType.getItemStackArgument(ctx, "item").getItem()));
                                    return say(ctx.getSource(), name(b) + ": " + b.doing());
                                }))))
                        .then(CommandManager.literal("smelt").then(bot().executes(ctx -> {
                            Body b = body(ctx);
                            b.start(new Orders.Smelt());
                            return say(ctx.getSource(), name(b) + ": " + b.doing());
                        })))
                        .then(CommandManager.literal("stop").then(bot().executes(ctx -> {
                            Body b = body(ctx);
                            b.stop();
                            return say(ctx.getSource(), name(b) + " stopped.");
                        })))
                        .then(CommandManager.literal("what").then(bot().executes(ctx -> {
                            Body b = body(ctx);
                            return say(ctx.getSource(), name(b) + " " + b.traits.title() + ": " + b.brain.doing());
                        })))
                        .then(CommandManager.literal("why").then(bot().executes(ctx -> {
                            Body b = body(ctx);
                            say(ctx.getSource(), name(b) + " " + b.traits.title() + ":");
                            for (String line : b.brain.why()) say(ctx.getSource(), line);
                            return 1;
                        })))
                        .then(CommandManager.literal("notebook").then(bot().executes(ctx -> {
                            Body b = body(ctx);
                            say(ctx.getSource(), name(b) + "'s notebook:");
                            for (String line : b.brain.notebook.describe(b.brain.now(), b.bot.getBlockPos())) say(ctx.getSource(), "  " + line);
                            return 1;
                        })))
                        .then(CommandManager.literal("brains")
                                .then(CommandManager.literal("on").executes(ctx -> {
                                    Bots.brainsOn = true;
                                    return say(ctx.getSource(), "Bot brains on: they act for themselves.");
                                }))
                                .then(CommandManager.literal("off").executes(ctx -> {
                                    Bots.brainsOn = false;
                                    return say(ctx.getSource(), "Bot brains off: they only follow orders.");
                                }))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<ServerCommandSource, ?> bot() {
        return CommandManager.argument("bot", EntityArgumentType.player());
    }

    private static Body body(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        ServerPlayerEntity p = EntityArgumentType.getPlayer(ctx, "bot");
        if (p instanceof DummyPlayer d && d.body != null) return d.body;
        throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                Text.literal(p.getName().getString() + " isn't a bot.")).create();
    }

    private static String name(Body b) {
        return b.bot.getName().getString();
    }

    private static int add(ServerCommandSource src, int count, String traits) {
        List<String> added = Bots.add(src.getServer(), count, traits);
        if (added.isEmpty()) return say(src, "No bots added (no names or trait mixes left, or no match for \"" + traits + "\").");
        for (String a : added) say(src, "Joined: " + a);
        return added.size();
    }

    private static int say(ServerCommandSource source, String message) {
        source.sendFeedback(() -> Text.literal(message).formatted(Formatting.GRAY), false);
        return 1;
    }
}
