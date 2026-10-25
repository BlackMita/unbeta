package net.unbeta.content.metal;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Right-click a Block of Diamond with a damaged diamond tool or piece of diamond armour and it
 * mends a little (one point of durability per click: one block mined, one hit, one heart of damage taken), with a quiet, high
 * anvil ring. Every repair makes that block 0.2% slower to mine, with no limit; it resets once
 * the block is mined.
 */
public final class DiamondRepair {

    private DiamondRepair() {}

    public static final Identifier SYNC = new Identifier("unbeta-content", "diamond_uses");
    private static final float PER_USE = 0.002F;
    private static final int COOLDOWN = 3;

    private static final Set<Item> DIAMOND = Set.of(Items.DIAMOND_SWORD, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL,
            Items.DIAMOND_HOE, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
    private static final java.util.Map<UUID, Long> LAST = new java.util.HashMap<>();
    /** What a player's game has been told (only the blocks they started mining). */
    public static final Long2IntOpenHashMap CLIENT = new Long2IntOpenHashMap();

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            BlockPos pos = hit.getBlockPos();
            if (!world.getBlockState(pos).isOf(Blocks.DIAMOND_BLOCK)) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (!DIAMOND.contains(stack.getItem()) || !stack.isDamaged()) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            long now = world.getTime();
            Long last = LAST.get(player.getUuid());
            if (last != null && now - last < COOLDOWN) return ActionResult.SUCCESS;
            LAST.put(player.getUuid(), now);
            stack.setDamage(stack.getDamage() - 1);     // one click undoes one block mined / one hit / one heart worn
            world.playSound(null, pos, SoundEvents.BLOCK_ANVIL_USE, SoundCategory.BLOCKS, 0.25F, 1.9F + world.random.nextFloat() * 0.1F);
            BlockMemory mem = BlockMemory.get((ServerWorld) world);
            mem.diamondUses.addTo(pos.asLong(), 1);
            mem.markDirty();
            return ActionResult.SUCCESS;
        });
        // When someone starts mining a diamond block, tell their game how tough it has become,
        // so the cracks match what the server allows.
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp && world.getBlockState(pos).isOf(Blocks.DIAMOND_BLOCK)) {
                PacketByteBuf buf = PacketByteBufs.create();
                buf.writeBlockPos(pos);
                buf.writeVarInt(BlockMemory.get((ServerWorld) world).diamondUses.get(pos.asLong()));
                if (ServerPlayNetworking.canSend(sp, SYNC)) ServerPlayNetworking.send(sp, SYNC, buf);
            }
            return ActionResult.PASS;
        });
    }

    /** How many times slower than normal this diamond block is to mine. */
    public static float slowdown(BlockView view, BlockPos pos) {
        int uses;
        if (view instanceof ServerWorld sw) uses = BlockMemory.get(sw).diamondUses.get(pos.asLong());
        else if (view instanceof World w && w.isClient) uses = CLIENT.get(pos.asLong());
        else uses = 0;
        return 1.0F + PER_USE * uses;
    }

    /** The block changed: a gold ward appears or goes, a diamond block forgets its repairs. */
    public static void changed(World world, BlockPos pos, BlockState oldState, BlockState newState) {
        if (!(world instanceof ServerWorld sw) || oldState.getBlock() == newState.getBlock()) return;
        BlockMemory mem = BlockMemory.get(sw);
        long p = pos.asLong();
        if (newState.isOf(Blocks.GOLD_BLOCK)) { mem.gold.add(p); mem.markDirty(); }
        if (oldState.isOf(Blocks.GOLD_BLOCK) && mem.gold.remove(p)) mem.markDirty();
        if (oldState.isOf(Blocks.DIAMOND_BLOCK) && mem.diamondUses.containsKey(p)) { mem.diamondUses.remove(p); mem.markDirty(); }
    }
}
