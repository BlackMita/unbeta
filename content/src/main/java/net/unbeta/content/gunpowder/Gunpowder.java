package net.unbeta.content.gunpowder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.AbstractFireBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.unbeta.content.jackolantern.JackOLanternItems;
import net.unbeta.content.torch.TorchItems;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gunpowder laid on the ground is a fuse. Lit, each piece flares up as fire for half a second
 * with a quick hiss, lights the pieces beside it (also one block up or down) after 4 ticks, then
 * pops like a firecracker: the block under it breaks and drops as if mined with an iron tool,
 * and anything close by takes up to 2 hearts. Nothing else is broken, and blast-proof blocks
 * (obsidian, cobblestone, bricks...) never are.
 */
public final class Gunpowder {

    private Gunpowder() {}

    public static GunpowderTrailBlock TRAIL;
    /** Blocks it can break even though they resist blasts as much as cobblestone does. */
    public static final TagKey<Block> BREAKABLE = TagKey.of(RegistryKeys.BLOCK, new Identifier("unbeta-content", "gunpowder_breakable"));
    private static final float BLAST_PROOF = 6.0F;   // cobblestone's blast resistance, and up
    private static final int SPREAD_TICKS = 4, BLAST_TICKS = 10;
    private static final float MAX_DAMAGE = 4.0F, REACH = 2.0F;

    private record Fuse(ServerWorld world, BlockPos pos, long spreadAt, long blastAt) {}

    private static final Map<ServerWorld, Map<BlockPos, Fuse>> FUSES = new HashMap<>();

    public static void register() {
        TRAIL = Registry.register(Registries.BLOCK, new Identifier("unbeta-content", "gunpowder_trail"),
                new GunpowderTrailBlock(AbstractBlock.Settings.create().noCollision().breakInstantly().nonOpaque()
                        .sounds(BlockSoundGroup.SAND).pistonBehavior(PistonBehavior.DESTROY)));

        // Right-click the top of a block with gunpowder to lay it (sneak to lay it on a chest,
        // furnace or anything else that does something when clicked).
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.GUNPOWDER) || player.isSpectator()) return ActionResult.PASS;
            if (!player.isSneaking()) {
                ActionResult r = world.getBlockState(hit.getBlockPos()).onUse(world, player, hand, hit);
                if (r.isAccepted()) return r;
            }
            ItemPlacementContext ctx = new ItemPlacementContext(player, hand, stack, hit);
            BlockPos at = ctx.getBlockPos();
            if (!ctx.canPlace() || !TRAIL.canPlaceAt(TRAIL.getDefaultState(), world, at)) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            world.setBlockState(at, TRAIL.connected(world, at), Block.NOTIFY_ALL);
            world.playSound(null, at, SoundEvents.BLOCK_SAND_PLACE, SoundCategory.BLOCKS, 0.6F, 1.3F);
            if (!player.getAbilities().creativeMode) stack.decrement(1);
            return ActionResult.SUCCESS;
        });

        ServerTickEvents.END_SERVER_TICK.register(Gunpowder::tick);
    }

    /** Held things that light it with a right-click. */
    public static boolean lights(ItemStack s) {
        return s.isOf(Items.FLINT_AND_STEEL) || s.isOf(Items.FIRE_CHARGE) || TorchItems.isLitTorch(s) || JackOLanternItems.isLit(s);
    }

    /** Fire or lava right beside it (but not the fuse's own flames). */
    public static boolean touchesFlame(ServerWorld world, BlockPos pos) {
        Map<BlockPos, Fuse> own = FUSES.getOrDefault(world, Map.of());
        for (Direction d : Direction.values()) {
            BlockPos n = pos.offset(d);
            if (own.containsKey(n)) continue;
            if (world.getFluidState(n).isIn(FluidTags.LAVA) || isFire(world.getBlockState(n))) return true;
        }
        return false;
    }

    private static boolean isFire(BlockState s) {
        if (s.getBlock() instanceof AbstractFireBlock) return true;
        Identifier id = Registries.BLOCK.getId(s.getBlock());
        return id.getNamespace().equals("burnt") && (id.getPath().contains("fire") || id.getPath().contains("flame")
                || id.getPath().startsWith("blazing_"));
    }

    /** Lights the piece of fuse at pos (it's already gone if an explosion caught it). */
    public static void ignite(ServerWorld world, BlockPos pos) {
        Map<BlockPos, Fuse> fuses = FUSES.computeIfAbsent(world, w -> new HashMap<>());
        BlockPos at = pos.toImmutable();
        if (fuses.containsKey(at)) return;
        long now = world.getTime();
        fuses.put(at, new Fuse(world, at, now + SPREAD_TICKS, now + BLAST_TICKS));
        BlockState fire = AbstractFireBlock.getState(world, at);
        boolean trailHere = world.getBlockState(at).isOf(TRAIL);
        if (trailHere || world.getBlockState(at).isAir()) {
            world.setBlockState(at, fire.canPlaceAt(world, at) ? fire : Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }
        // A creeper's hiss, sped up.
        world.playSound(null, at, SoundEvents.ENTITY_CREEPER_PRIMED, SoundCategory.BLOCKS, 0.7F, 2.0F);
    }

    private static void tick(MinecraftServer server) {
        if (FUSES.isEmpty()) return;
        List<Fuse> spread = new ArrayList<>(), blast = new ArrayList<>();
        for (Map<BlockPos, Fuse> fuses : FUSES.values()) {
            for (Fuse f : fuses.values()) {
                long now = f.world.getTime();
                if (now == f.spreadAt) spread.add(f);
                if (now >= f.blastAt) blast.add(f);
            }
        }
        for (Fuse f : spread) {
            for (Direction d : Direction.Type.HORIZONTAL) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos n = f.pos.offset(d).up(dy);
                    if (f.world.getBlockState(n).isOf(TRAIL)) ignite(f.world, n);
                }
            }
        }
        for (Fuse f : blast) {
            Map<BlockPos, Fuse> fuses = FUSES.get(f.world);
            if (fuses != null) {
                fuses.remove(f.pos);
                if (fuses.isEmpty()) FUSES.remove(f.world);
            }
            pop(f.world, f.pos);
        }
    }

    // ------------------------------------------------------------------ the pop

    private static void pop(ServerWorld world, BlockPos pos) {
        if (isFire(world.getBlockState(pos))) world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        Vec3d c = Vec3d.ofBottomCenter(pos).add(0, 0.1, 0);
        world.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        world.spawnParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 8, 0.25, 0.1, 0.25, 0.02);
        world.playSound(null, pos, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.BLOCKS, 2.0F, 0.9F + world.random.nextFloat() * 0.3F);
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.BLOCKS, 0.35F, 1.9F);
        breakBelow(world, pos.down());
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(c, c).expand(REACH), LivingEntity::isAlive)) {
            Vec3d to = e.getBoundingBox().getCenter();
            double d = to.distanceTo(c);
            if (d >= REACH) continue;
            float k = (float) (1.0 - d / REACH);
            e.damage(world.getDamageSources().explosion(null, null), MAX_DAMAGE * k);
            Vec3d push = to.subtract(c).normalize().multiply(0.4 * k);
            e.addVelocity(push.x, push.y * 0.5 + 0.1 * k, push.z);
            e.velocityModified = true;
        }
    }

    /** Breaks the block a piece of fuse sat on, dropping what an iron tool would get. */
    private static void breakBelow(ServerWorld world, BlockPos pos) {
        BlockState s = world.getBlockState(pos);
        if (s.isAir() || !s.getFluidState().isEmpty() && s.getBlock() instanceof net.minecraft.block.FluidBlock) return;
        if (s.getHardness(world, pos) < 0) return;
        if (s.getBlock().getBlastResistance() >= BLAST_PROOF && !s.isIn(BREAKABLE)) return;
        ItemStack tool = new ItemStack(s.isIn(BlockTags.AXE_MINEABLE) ? Items.IRON_AXE
                : s.isIn(BlockTags.SHOVEL_MINEABLE) ? Items.IRON_SHOVEL
                : s.isIn(BlockTags.HOE_MINEABLE) ? Items.IRON_HOE : Items.IRON_PICKAXE);
        BlockEntity be = world.getBlockEntity(pos);
        Block.dropStacks(s, world, pos, be, null, tool);
        world.breakBlock(pos, false);
    }
}
