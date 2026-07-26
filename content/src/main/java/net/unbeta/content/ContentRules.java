package net.unbeta.content;

import net.minecraft.util.Identifier;
import net.unbeta.core.api.ContentKind;
import net.unbeta.core.api.RuleKey;
import net.unbeta.core.api.RuleOverrideContext;
import net.unbeta.core.api.RuleProvider;

/**
 * PHASE 2 SCAFFOLD - not active yet.
 *
 * <p>This class exists in Phase 1 for one reason: to prove the architecture works.
 * Everything below is commented out. Uncomment a line and the corresponding vanilla
 * behaviour changes, with zero edits to unbeta-core.
 *
 * <p>That is the whole test of whether Phase 1 was designed correctly.
 */
public final class ContentRules implements RuleProvider {

    @Override
    public void registerOverrides(RuleOverrideContext ctx) {

        // --- Unbeta 1.7.3: "The Nether removed (and The End will NEVER be added)" ---
        // Beta 1.8 HAS the Nether, so Phase 1 leaves it alone. Phase 2 turns it off
        // by flipping one rule. No new mixin. No change to the dimension gate.
        //
        // ctx.set(RuleKey.of(ContentKind.DIMENSION, new Identifier("minecraft", "the_nether")), true);

        // --- "No hostile mobs will catch fire in sunlight. No more sunrise victories." ---
        // ctx.setSystem("mob_daylight_burn", true);

        // --- "Mob spawners REMOVED from game" ---
        // ctx.set(RuleKey.of(ContentKind.BLOCK, new Identifier("minecraft", "spawner")), true);

        // --- Phase 2 re-enables nothing from Phase 1 yet, but it could: ---
        // ctx.set(RuleKey.of(ContentKind.ITEM, new Identifier("minecraft", "trident")), false);
        //   ("THEY DON'T HAVE TRIDENTS (Tridents are chest loot exclusive)" - your doc
        //    keeps the trident as loot, so Phase 2 would un-gate the item and gate the
        //    drowned's ability to hold one. Both are rule flips, not code edits.)
    }
}
