package net.unbeta.content;

import net.minecraft.util.Identifier;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.RuleKey;
import net.unbeta.core.api.RuleOverrideContext;
import net.unbeta.core.api.RuleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PHASE 2 — Unbeta 1.7.3 content rules.
 *
 * <p>This is where Phase 2 overrides Phase 1's vanilla gating. Every entry here is a
 * rule flip resolved through unbeta-core's precedence chain at MOD_OVERRIDE priority,
 * with zero edits to core. The first feature — removing the Nether — deliberately
 * exercises the chain in BOTH directions: gating a dimension, AND overriding several
 * Phase 1 "keep" decisions (netherrack, ghast, etc.) into removals.
 */
public final class ContentRules implements RuleProvider {

    public static final Logger LOG = LoggerFactory.getLogger("UnbetaContent");

    private static Identifier mc(String path) {
        return new Identifier("minecraft", path);
    }

    @Override
    public void registerOverrides(RuleOverrideContext ctx) {
        LOG.info("=== Unbeta Content (Phase 2) is loading rule overrides ===");

        removeTheNether(ctx);
        noHostileDaylightBurn(ctx);

        LOG.info("=== Unbeta Content applied its overrides ===");
    }

    /**
     * Unbeta 1.7.3: "The Nether removed. Netherrack removed. Ghasts and Zombie Pigmen removed."
     *
     * <p>Three kinds of override, all through the same rule engine:
     * <ol>
     *   <li>The dimension itself — travel is blocked by the already-written
     *       NetherPortalBlockMixin + DimensionGate eviction the instant this is true.</li>
     *   <li>Nether MOBS — ghast and zombified_piglin are Beta 1.8 content that Phase 1
     *       keeps; here Phase 2 overrides those keeps into removals.</li>
     *   <li>Nether BLOCKS — netherrack/soul sand/glowstone, likewise Phase 1 keeps
     *       being overridden to removals.</li>
     * </ol>
     */
    private void removeTheNether(RuleOverrideContext ctx) {
        // 1. The dimension. This one line is the architecture-validation test.
        ctx.set(RuleKey.of(ContentKind.DIMENSION, mc("the_nether")), true);

        // 2. Nether mobs (Phase 1 keeps -> Phase 2 removes).
        for (String mob : new String[]{ "ghast", "zombified_piglin" }) {
            ctx.set(RuleKey.of(ContentKind.ENTITY, mc(mob)), true);
        }

        // 3. Nether blocks (Phase 1 keeps -> Phase 2 removes).
        //    Kept minimal to the true b1.8 Nether palette. Glowstone is intentionally
        //    NOT removed here — reconsider per your doc; it may have overworld uses.
        for (String block : new String[]{ "netherrack", "soul_sand" }) {
            ctx.set(RuleKey.of(ContentKind.BLOCK, mc(block)), true);
            ctx.set(RuleKey.of(ContentKind.ITEM, mc(block)), true);
        }
    }

    /**
     * Unbeta 1.7.3: "No hostile mobs will catch fire in sunlight. No more sunrise victories."
     *
     * <p>One rule flip. The suppression mechanism (DaylightBurnMixin) already exists in
     * unbeta-core, inert by default - this line is the entire Phase 2 feature.
     */
    private void noHostileDaylightBurn(RuleOverrideContext ctx) {
        ctx.setSystem("mob_daylight_burn", true);
    }
}
