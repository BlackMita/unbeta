package net.unbeta.core.command;

import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.Resolution;
import net.unbeta.core.api.RuleKey;
import net.unbeta.core.rules.RuleRegistry;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Diagnostics. Build these before any content work - the audit CSV is what turns
 * "did I break the other 23 mods" from a feeling into a diff.
 */
public final class UnbetaCommands {

    private UnbetaCommands() {}

    public static void register(RuleRegistry registry) {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, env) ->
            dispatcher.register(CommandManager.literal("unbeta")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("audit")
                        .executes(ctx -> audit(ctx, registry)))
                .then(CommandManager.literal("rules")
                        .executes(ctx -> rules(ctx, registry, ""))
                        .then(CommandManager.argument("filter", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> rules(ctx, registry,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "filter")))))
                .then(CommandManager.literal("why")
                        .then(CommandManager.argument("id", IdentifierArgumentType.identifier())
                                .executes(ctx -> why(ctx, registry))))
                .then(CommandManager.literal("reload")
                        .executes(ctx -> reload(ctx, registry)))
                .then(CommandManager.literal("features")
                        .executes(ctx -> features(ctx, ""))
                        .then(CommandManager.argument("filter", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(ctx -> features(ctx,
                                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "filter")))))
            ));
    }

    // ------------------------------------------------------------------ audit

    private static int audit(CommandContext<ServerCommandSource> ctx, RuleRegistry registry) {
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = FabricLoader.getInstance().getGameDir().resolve("unbeta-audit-" + stamp + ".csv");

        int rows = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("kind,id,namespace,gated_namespace,removed,source,detail\n");

            rows += dump(w, registry, ContentKind.ENTITY, Registries.ENTITY_TYPE.getIds());
            rows += dump(w, registry, ContentKind.ITEM, Registries.ITEM.getIds());
            rows += dump(w, registry, ContentKind.BLOCK, Registries.BLOCK.getIds());

            var server = ctx.getSource().getServer();
            rows += dump(w, registry, ContentKind.STRUCTURE,
                    server.getRegistryManager().get(RegistryKeys.STRUCTURE).getIds());
            rows += dump(w, registry, ContentKind.DIMENSION,
                    server.getRegistryManager().get(RegistryKeys.DIMENSION_TYPE).getIds());

            // System rules have no registry; emit them from the rule set.
            for (RuleKey k : registry.keys()) {
                if (k.kind() == ContentKind.SYSTEM) {
                    Resolution r = registry.resolve(k);
                    w.write(String.join(",",
                            "system", esc(k.value()), "unbeta", "true",
                            String.valueOf(r.removed()), r.source().name(), esc(r.detail())));
                    w.write('\n');
                    rows++;
                }
            }
        } catch (Exception e) {
            UnbetaCore.LOG.error("Audit failed", e);
            ctx.getSource().sendError(Text.literal("Audit failed: " + e.getMessage()));
            return 0;
        }

        final int n = rows;
        ctx.getSource().sendFeedback(() -> Text.literal(
                "Unbeta audit: " + n + " rows -> " + out.getFileName()), false);
        ctx.getSource().sendFeedback(() -> Text.literal(
                "Commit this file to docs/audits/ and diff it after adding a mod."), false);
        return 1;
    }

    private static int dump(BufferedWriter w, RuleRegistry registry, ContentKind kind,
                            Iterable<Identifier> ids) throws Exception {
        int n = 0;
        for (Identifier id : ids) {
            boolean gated = registry.isGatedNamespace(id.getNamespace());
            Resolution r = registry.resolve(RuleKey.of(kind, id));
            boolean removed = gated && r.removed();
            w.write(String.join(",",
                    kind.id(), esc(id.toString()), id.getNamespace(), String.valueOf(gated),
                    String.valueOf(removed), r.source().name(), esc(r.detail())));
            w.write('\n');
            n++;
        }
        return n;
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.contains(",") || s.contains("\"")
                ? "\"" + s.replace("\"", "\"\"") + "\""
                : s;
    }

    // -------------------------------------------------------------------- why

    private static int why(CommandContext<ServerCommandSource> ctx, RuleRegistry registry) {
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");
        boolean any = false;

        if (!registry.isGatedNamespace(id.getNamespace())) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "Namespace '" + id.getNamespace() + "' is not gated. Unbeta never touches it."), false);
        }

        for (ContentKind kind : ContentKind.values()) {
            RuleKey key = RuleKey.of(kind, id);
            Resolution r = registry.resolve(key);
            if (r.source() != net.unbeta.core.api.RuleSource.MANIFEST_DEFAULT
                    || registry.manifest().byKey().containsKey(key)) {
                any = true;
                ctx.getSource().sendFeedback(() -> Text.literal(
                        "  " + key + " = " + (r.removed() ? "REMOVED" : "allowed")
                        + "  [" + r.source() + "] " + r.detail()), false);
            }
        }
        if (!any) {
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "No rule mentions " + id + ". It is allowed by default."), false);
        }
        return 1;
    }

    // ------------------------------------------------------------------ rules

    private static int rules(CommandContext<ServerCommandSource> ctx, RuleRegistry registry, String filter) {
        String f = filter.toLowerCase(Locale.ROOT).trim();
        int shown = 0;
        for (RuleKey k : registry.keys()) {
            if (!f.isEmpty() && !k.value().toLowerCase(Locale.ROOT).contains(f)) continue;
            if (shown >= 60) {
                ctx.getSource().sendFeedback(() -> Text.literal("  ... truncated. Use /unbeta audit for the full set."), false);
                break;
            }
            Resolution r = registry.resolve(k);
            ctx.getSource().sendFeedback(() -> Text.literal(
                    "  " + k + " = " + (r.removed() ? "REMOVED" : "allowed") + "  [" + r.source() + "]"), false);
            shown++;
        }
        final int total = registry.keys().size();
        ctx.getSource().sendFeedback(() -> Text.literal(total + " rules known."), false);
        return 1;
    }

    private static int reload(CommandContext<ServerCommandSource> ctx, RuleRegistry registry) {
        registry.reload();
        ctx.getSource().sendFeedback(() -> Text.literal(
                "Reloaded config/unbeta/overrides.json. Manifest and mod overrides are load-time only."), false);
        return 1;
    }

    // --------------------------------------------------------------- features

    /**
     * Dumps every registered placed feature ID to a CSV. Placed features live in a
     * DYNAMIC registry, so the only reliable list is the one from a running server -
     * exactly like the /unbeta audit approach for blocks and items.
     */
    private static int features(CommandContext<ServerCommandSource> ctx, String filter) {
        String f = filter.toLowerCase(Locale.ROOT).trim();
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path out = FabricLoader.getInstance().getGameDir().resolve("unbeta-features-" + stamp + ".csv");

        int n = 0;
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            w.write("placed_feature\n");
            var reg = ctx.getSource().getServer().getRegistryManager().get(RegistryKeys.PLACED_FEATURE);
            for (Identifier id : reg.getIds()) {
                if (!f.isEmpty() && !id.toString().toLowerCase(Locale.ROOT).contains(f)) continue;
                w.write(id.toString());
                w.write('\n');
                n++;
            }
        } catch (Exception e) {
            UnbetaCore.LOG.error("Feature dump failed", e);
            ctx.getSource().sendError(Text.literal("Feature dump failed: " + e.getMessage()));
            return 0;
        }

        final int count = n;
        ctx.getSource().sendFeedback(() -> Text.literal(
                "Unbeta features: " + count + " placed feature(s) -> " + out.getFileName()), false);
        return 1;
    }
}
