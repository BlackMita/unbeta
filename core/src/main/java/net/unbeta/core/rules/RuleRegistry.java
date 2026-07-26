package net.unbeta.core.rules;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.unbeta.core.UnbetaCore;
import net.unbeta.core.api.*;
import net.unbeta.core.config.UnbetaConfig;
import net.unbeta.core.manifest.Manifest;
import net.unbeta.core.manifest.ManifestEntry;

import java.util.*;

/**
 * The precedence chain, implemented literally:
 *
 * <pre>
 *   MANIFEST_DEFAULT  <  CORE_CONFIG  <  MOD_OVERRIDE  <  USER_OVERRIDE
 * </pre>
 *
 * Each layer is a separate map. Resolution walks them from highest to lowest and
 * records which one won, so {@code /unbeta why} can explain itself.
 */
public final class RuleRegistry implements UnbetaRules {

    private final Manifest manifest;
    private final UnbetaConfig config;

    private final Map<RuleKey, Boolean> manifestDefaults = new LinkedHashMap<>();
    private final Map<RuleKey, Boolean> coreConfig = new LinkedHashMap<>();
    private final Map<RuleKey, Boolean> modOverrides = new LinkedHashMap<>();
    private final Map<RuleKey, String> modOverrideOwners = new LinkedHashMap<>();
    private final Map<RuleKey, Boolean> userOverrides = new LinkedHashMap<>();

    /** Hot-path cache, invalidated on reload. */
    private final Map<RuleKey, Boolean> cache = new HashMap<>();

    private RuleRegistry(Manifest manifest, UnbetaConfig config) {
        this.manifest = manifest;
        this.config = config;
    }

    public static RuleRegistry bootstrap(Manifest manifest, UnbetaConfig config) {
        RuleRegistry r = new RuleRegistry(manifest, config);
        r.loadManifestDefaults();
        r.loadCoreConfig();
        return r;
    }

    // ---------------------------------------------------------------- loading

    private void loadManifestDefaults() {
        for (ManifestEntry e : manifest.entries()) {
            manifestDefaults.put(e.ruleKey(), e.action().removedByDefault());
        }
    }

    private void loadCoreConfig() {
        config.rules.forEach((k, v) -> coreConfig.put(RuleKey.of(k), v));
    }

    /** Runs every mod's {@code unbeta:rules} entrypoint. */
    public void applyModOverrides() {
        List<EntrypointContainer<RuleProvider>> providers =
                FabricLoader.getInstance().getEntrypointContainers("unbeta:rules", RuleProvider.class);

        for (EntrypointContainer<RuleProvider> container : providers) {
            String modId = container.getProvider().getMetadata().getId();
            try {
                container.getEntrypoint().registerOverrides(new RuleOverrideContext() {
                    @Override public String modId() { return modId; }
                    @Override public void set(RuleKey key, boolean removed) {
                        override(modId, key, removed);
                    }
                });
            } catch (Throwable t) {
                UnbetaCore.LOG.error("Mod '{}' threw from its unbeta:rules entrypoint. Ignoring it.", modId, t);
            }
        }
        if (!providers.isEmpty()) {
            UnbetaCore.LOG.info("Applied rule overrides from {} mod(s).", providers.size());
        }
    }

    public void applyUserOverrides(Map<String, Boolean> raw) {
        raw.forEach((k, v) -> userOverrides.put(RuleKey.of(k), v));
        cache.clear();
    }

    public void reload() {
        cache.clear();
        userOverrides.clear();
        applyUserOverrides(UnbetaConfig.loadUserOverrides());
    }

    public Manifest manifest() {
        return manifest;
    }

    public UnbetaConfig config() {
        return config;
    }

    // ---------------------------------------------------------------- queries

    @Override
    public boolean isRemoved(RuleKey key) {
        Boolean c = cache.get(key);
        if (c != null) return c;
        boolean v = resolve(key).removed();
        cache.put(key, v);
        return v;
    }

    @Override
    public boolean isRemoved(ContentKind kind, Identifier id) {
        // The keystone. Anything outside a gated namespace is never our business.
        if (!isGatedNamespace(id.getNamespace())) return false;
        return isRemoved(RuleKey.of(kind, id));
    }

    @Override
    public boolean isRemoved(EntityType<?> type) {
        return isRemoved(ContentKind.ENTITY, Registries.ENTITY_TYPE.getId(type));
    }

    @Override
    public boolean isRemoved(Item item) {
        return isRemoved(ContentKind.ITEM, Registries.ITEM.getId(item));
    }

    @Override
    public boolean isRemoved(Block block) {
        return isRemoved(ContentKind.BLOCK, Registries.BLOCK.getId(block));
    }

    @Override
    public boolean isDimensionRemoved(RegistryKey<World> world) {
        return isRemoved(ContentKind.DIMENSION, world.getValue());
    }

    @Override
    public boolean isSystemDisabled(String name) {
        return isRemoved(RuleKey.system(name));
    }

    @Override
    public boolean isGatedNamespace(String namespace) {
        return config.gatedNamespaces.contains(namespace);
    }

    @Override
    public Resolution resolve(RuleKey key) {
        if (userOverrides.containsKey(key)) {
            return new Resolution(key, userOverrides.get(key), RuleSource.USER_OVERRIDE,
                    "config/unbeta/overrides.json");
        }
        if (modOverrides.containsKey(key)) {
            return new Resolution(key, modOverrides.get(key), RuleSource.MOD_OVERRIDE,
                    modOverrideOwners.getOrDefault(key, "unknown mod"));
        }
        if (coreConfig.containsKey(key)) {
            return new Resolution(key, coreConfig.get(key), RuleSource.CORE_CONFIG,
                    "config/unbeta/core.json");
        }
        Boolean def = manifestDefaults.get(key);
        if (def != null) {
            ManifestEntry e = manifest.byKey().get(key);
            String detail = e == null ? "manifest.json"
                    : "manifest.json (" + e.action().name().toLowerCase(Locale.ROOT)
                      + (e.introducedIn().isEmpty() ? "" : ", added " + e.introducedIn())
                      + (e.verified() ? ", verified" : ", UNVERIFIED") + ")";
            return new Resolution(key, def, RuleSource.MANIFEST_DEFAULT, detail);
        }
        // Unknown key: never gate something we were not told about.
        return new Resolution(key, false, RuleSource.MANIFEST_DEFAULT, "no manifest entry - defaulting to allowed");
    }

    @Override
    public Set<RuleKey> keys() {
        TreeSet<RuleKey> all = new TreeSet<>(manifestDefaults.keySet());
        all.addAll(coreConfig.keySet());
        all.addAll(modOverrides.keySet());
        all.addAll(userOverrides.keySet());
        return Collections.unmodifiableSet(all);
    }

    @Override
    public void override(String modId, RuleKey key, boolean removed) {
        Boolean prev = modOverrides.put(key, removed);
        String prevOwner = modOverrideOwners.put(key, modId);
        if (prev != null && prev != removed) {
            UnbetaCore.LOG.warn("Rule '{}' overridden to {} by '{}', clobbering {} from '{}'.",
                    key, removed, modId, prev, prevOwner);
        }
        cache.remove(key);
    }
}
