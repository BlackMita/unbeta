package net.unbeta.core.api;

/**
 * Static access point. {@code UnbetaApi.rules()} is safe from any mod's
 * initializer that declares a dependency on {@code unbeta-core}.
 */
public final class UnbetaApi {

    private static UnbetaRules rules;

    private UnbetaApi() {}

    public static UnbetaRules rules() {
        if (rules == null) {
            throw new IllegalStateException(
                "Unbeta Core has not initialised yet. Declare a depends entry on \"unbeta-core\".");
        }
        return rules;
    }

    public static boolean isReady() {
        return rules != null;
    }

    /** Internal. Called once by UnbetaCore. */
    public static void install(UnbetaRules impl) {
        if (rules != null) {
            throw new IllegalStateException("Unbeta rules already installed");
        }
        rules = impl;
    }
}
