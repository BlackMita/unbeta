package net.unbeta.core.api;

/**
 * Implement this and declare it under the {@code "unbeta:rules"} entrypoint in
 * your fabric.mod.json to change what Unbeta Core gates.
 *
 * <pre>{@code
 * "entrypoints": { "unbeta:rules": [ "net.unbeta.content.ContentRules" ] }
 * }</pre>
 *
 * <p>Called once during core initialisation, after manifest defaults and the
 * core config have been applied, and before the user override file.
 */
@FunctionalInterface
public interface RuleProvider {
    void registerOverrides(RuleOverrideContext ctx);
}
