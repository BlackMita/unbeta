package net.unbeta.content;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.ItemGroups;
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
                                .replaceable()
                                .dropsNothing()
                )
        );

        LOG.info("Registered {} (obsidian-exclusive coloured fire).", OBSIDIAN_FIRE_ID);

        // Unbeta torches (Phase 2 lighting tech-tree, tier 0).
        net.unbeta.content.torch.UnbetaTorchRegistry.register();
        net.unbeta.content.torch.TorchBurnout.register();
        net.unbeta.content.torch.UnbetaTorchItemBurnout.register();
        net.unbeta.content.torch.TorchLightingHooks.register();
        LOG.info("Registered Unbeta torches.");

        // Glowsand: luminous gravity-affected block. Smelts to glowstone.
        net.minecraft.block.Block glowsand = net.minecraft.registry.Registry.register(
                net.minecraft.registry.Registries.BLOCK,
                new net.minecraft.util.Identifier("unbeta-content", "glowsand"),
                new net.unbeta.content.block.GlowsandBlock(
                        net.minecraft.block.AbstractBlock.Settings.create()
                                .mapColor(net.minecraft.block.MapColor.YELLOW)
                                .strength(0.5F)
                                .sounds(net.minecraft.sound.BlockSoundGroup.SAND)
                                .luminance(state -> 15)));
        net.minecraft.item.Item glowsandItem = net.minecraft.registry.Registry.register(
                net.minecraft.registry.Registries.ITEM,
                new net.minecraft.util.Identifier("unbeta-content", "glowsand"),
                new net.minecraft.item.BlockItem(glowsand,
                        new net.minecraft.item.Item.Settings()));
        // Add to creative building blocks tab
        ItemGroupEvents.modifyEntriesEvent(net.minecraft.item.ItemGroups.FUNCTIONAL)
                .register(entries -> entries.add(glowsandItem));
        LOG.info("Registered Glowsand.");

        // Unbeta Jack o'Lanterns (Phase 2 lighting tier 1.5 - semi-permanent, waterproof).
        net.unbeta.content.jackolantern.JackOLanternRegistry.register();
        net.unbeta.content.jackolantern.JackOLanternBurnout.register();
        LOG.info("Registered Unbeta Jack o\'Lanterns.");

        // Boom Spore: creeper-planted grenade, throwable like snowball.
        net.unbeta.content.boomspore.BoomSporeRegistry.register();
        LOG.info("Registered Boom Spore.");

        // Bone Pile: skeleton death remnant with 9-slot inventory, respawns after 20s.
        net.unbeta.content.skeleton.BonePileRegistry.register();
        net.unbeta.content.skeleton.BonePileRespawn.register();
        LOG.info("Registered Bone Pile.");

        // Sullied chunks: zombie death marks chunk, rising zombie spawns on next player entry.
        net.unbeta.content.zombie.SulliedChunkTick.register();
        LOG.info("Sullied chunk system registered.");

        // Lit torch melee: 50% chance to set mob on fire for 4 seconds when struck.
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
            (player, world, hand, entity, hitResult) -> {
                if (world.isClient) return net.minecraft.util.ActionResult.PASS;
                net.minecraft.item.ItemStack held = player.getStackInHand(hand);
                boolean litTorch = net.unbeta.content.torch.TorchItems.isLitTorch(held)
                        || net.unbeta.content.jackolantern.JackOLanternItems.isLit(held);
                if (litTorch && entity instanceof net.minecraft.entity.LivingEntity
                        && world.random.nextFloat() < 0.5F) {
                    entity.setOnFireFor(4);
                }
                return net.minecraft.util.ActionResult.PASS;
            });

        // Furnace ignition: must right-click with flint+steel or lit torch to start.
        // Right-clicking a lit furnace with an unlit torch lights the torch.
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            var state = world.getBlockState(hit.getBlockPos());
            if (!(state.getBlock() instanceof net.minecraft.block.AbstractFurnaceBlock)) {
                return net.minecraft.util.ActionResult.PASS;
            }
            net.minecraft.item.ItemStack held = player.getStackInHand(hand);
            boolean isIgniter = held.isOf(net.minecraft.item.Items.FLINT_AND_STEEL)
                    || net.unbeta.content.torch.TorchItems.isLitTorch(held)
                    || net.unbeta.content.jackolantern.JackOLanternItems.isLit(held);
            boolean isUnlitTorch = net.unbeta.content.torch.TorchItems.isUnlitTorch(held);
            boolean furnaceLit = state.get(net.minecraft.block.AbstractFurnaceBlock.LIT);

            // Unlit torch + lit furnace → light the torch
            if (isUnlitTorch && furnaceLit) {
                if (!world.isClient) {
                    player.setStackInHand(hand,
                        net.unbeta.content.torch.TorchItems.createLit(held, world.getTime()));
                }
                return net.minecraft.util.ActionResult.SUCCESS;
            }

            // Igniter + unlit furnace → ignite it
            if (isIgniter && !furnaceLit) {
                if (!world.isClient) {
                    // Mark this furnace as ignited so getFuelTime will work
                    net.unbeta.content.furnace.FurnaceIgnitionTracker.ignite(hit.getBlockPos());
                    if (held.isOf(net.minecraft.item.Items.FLINT_AND_STEEL)
                            && !player.getAbilities().creativeMode) {
                        held.damage(1, player, p2 -> p2.sendToolBreakStatus(hand));
                    }
                }
                return net.minecraft.util.ActionResult.SUCCESS;
            }
            return net.minecraft.util.ActionResult.PASS;
        });

        // Mining hierarchy: stone/obsidian indestructible without correct tool.
        // Using PlayerBlockBreakEvents.BEFORE (returns false = cancel break entirely)
        // so the block never becomes air — true "indestructible" illusion.
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
            (world, player, pos, state, blockEntity) -> {
                net.minecraft.item.ItemStack tool = player.getMainHandStack();

                // Stone/deepslate: needs iron+ pickaxe
                if (state.isIn(net.minecraft.registry.tag.BlockTags.NEEDS_IRON_TOOL)) {
                    boolean hasIron = tool.isOf(net.minecraft.item.Items.IRON_PICKAXE)
                            || tool.isOf(net.minecraft.item.Items.DIAMOND_PICKAXE)
                            || tool.isOf(net.minecraft.item.Items.NETHERITE_PICKAXE);
                    if (!hasIron) return false; // cancel - block stays
                }

                // Obsidian: needs diamond pickaxe
                if (state.isIn(net.minecraft.registry.tag.BlockTags.NEEDS_DIAMOND_TOOL)) {
                    boolean hasDiamond = tool.isOf(net.minecraft.item.Items.DIAMOND_PICKAXE)
                            || tool.isOf(net.minecraft.item.Items.NETHERITE_PICKAXE);
                    if (!hasDiamond) return false; // cancel - block stays
                }

                return true; // allow break
            });

        // When obsidian is mined, remove any fire block above it.
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
            (world, player, pos, state, blockEntity) -> {
                if (state.isOf(net.minecraft.block.Blocks.OBSIDIAN)
                        || state.isOf(net.minecraft.block.Blocks.CRYING_OBSIDIAN)) {
                    var above = world.getBlockState(pos.up());
                    if (above.getBlock() instanceof net.minecraft.block.AbstractFireBlock) {
                        world.removeBlock(pos.up(), false);
                    }
                }
            });
        // Remove all burnt mod creative entries except the 11 approved burnt-aesthetic blocks.
        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.MODIFY_ENTRIES_ALL.register((group, entries) -> {
            entries.getDisplayStacks().removeIf(stack -> {
                var id = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString();
                return id.startsWith("burnt:") && !java.util.Set.of(
                    "burnt:burnt_log", "burnt:burnt_planks", "burnt:burnt_stairs",
                    "burnt:burnt_slab", "burnt:burnt_fence", "burnt:burnt_fencegate",
                    "burnt:burnt_pressure_plate", "burnt:burnt_oak_door",
                    "burnt:burnt_oak_trapdoor", "burnt:burnt_ladder", "burnt:burnt_carpet"
                ).contains(id);
            });
        });

        // TEMP (testing): put the torch in the creative functional-blocks tab so it can be obtained.
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
                entries.add(net.unbeta.content.torch.UnbetaTorchRegistry.TORCH_ITEM);
                entries.add(net.unbeta.content.torch.UnbetaTorchRegistry.LIT_TORCH_ITEM);
        });
    }
}
