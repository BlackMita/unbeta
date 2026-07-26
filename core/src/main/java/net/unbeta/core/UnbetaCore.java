package net.unbeta.core;

import net.fabricmc.api.ModInitializer;
import net.unbeta.core.api.UnbetaApi;
import net.unbeta.core.command.UnbetaCommands;
import net.unbeta.core.config.UnbetaConfig;
import net.unbeta.core.manifest.Manifest;
import net.unbeta.core.manifest.ManifestLoader;
import net.unbeta.core.rules.RuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 1 entry point.
 *
 * <p>Order matters and is part of the API contract:
 * manifest defaults, then core config, then other mods' overrides, then the user file.
 */
public final class UnbetaCore implements ModInitializer {

    public static final String MOD_ID = "unbeta-core";
    public static final Logger LOG = LoggerFactory.getLogger("Unbeta");

    @Override
    public void onInitialize() {
        long t0 = System.currentTimeMillis();

        Manifest manifest = ManifestLoader.loadBundled();
        UnbetaConfig config = UnbetaConfig.loadOrCreate();

        RuleRegistry registry = RuleRegistry.bootstrap(manifest, config);
        UnbetaApi.install(registry);

        registry.applyModOverrides();
        registry.applyUserOverrides(UnbetaConfig.loadUserOverrides());

        UnbetaCommands.register(registry);

        // M4 gates: make removals actually happen at runtime.
        net.unbeta.core.gates.LootGate.register();
        if (config.hideFromCreative) {
            net.unbeta.core.gates.CreativeGate.register();
        }
        // (The recipe gate is wired via RecipeManagerMixin, not here.)

        // M5 spawn gates: two layers.
        //  - SpawnGate strips gated mobs from biome spawn tables (efficient, composable).
        //  - SpawnCatchAll vetoes any gated mob that slips through by another path.
        net.unbeta.core.gates.SpawnGate.register();
        net.unbeta.core.gates.SpawnCatchAll.register(config);

        // M7 dimension gate: generic and rule-driven. Phase 1 gates the End;
        // Phase 2 gates the Nether via a rule flip, with no code change here.
        net.unbeta.core.gates.DimensionGate.register();

        LOG.info("Unbeta Core ready in {} ms - {} manifest entries, {} rules, gated namespaces {}.",
                System.currentTimeMillis() - t0,
                manifest.entries().size(), registry.keys().size(), config.gatedNamespaces);

        long unverified = manifest.countUnverified();
        if (unverified > 0) {
            LOG.warn("{} manifest entries are still marked verified:false. "
                   + "Milestone M3 is the verification pass against minecraft.wiki.", unverified);
        }
    }
}
