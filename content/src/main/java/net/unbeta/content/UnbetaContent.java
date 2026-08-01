package net.unbeta.content;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.unbeta.content.block.ObsidianFireBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase 2 content initializer. Registers new blocks/items and wires behaviour.
 * Separate from ContentRules (which only registers rule overrides via a different
 * entrypoint).
 */
public final class UnbetaContent implements ModInitializer {

    public static final String MOD_ID = "unbeta-content";
    public static final Logger LOG = LoggerFactory.getLogger("UnbetaContent-Main");

    public static final Identifier OBSIDIAN_FIRE_ID = new Identifier(MOD_ID, "obsidian_fire");

    public static ObsidianFireBlock OBSIDIAN_FIRE;

    @Override
    public void onInitialize() {
        OBSIDIAN_FIRE = Registry.register(
                Registries.BLOCK,
                OBSIDIAN_FIRE_ID,
                new ObsidianFireBlock(
                        AbstractBlock.Settings.create()
                                .mapColor(MapColor.BRIGHT_RED)
                                .noCollision()
                                .breakInstantly()
                                .luminance(state -> 15)
                                .sounds(BlockSoundGroup.WOOL)
                                .pistonBehavior(PistonBehavior.DESTROY)
                )
        );

        LOG.info("Registered {} (obsidian-exclusive coloured fire).", OBSIDIAN_FIRE_ID);
    }
}
