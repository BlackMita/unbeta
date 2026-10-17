package net.unbeta.content.light;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldView;

/**
 * Ice and snow melt from heat, not from light. Vanilla melts them in any bright block light;
 * here the light must also come from something hot (torch, Jack o'Lantern, fire, lava,
 * furnace...). Cold lights - tag unbeta-content:cold_light: glowstone, glowsand, glow berries
 * and the like - shine just as brightly but melt nothing.
 */
public final class Heat {

    public static final TagKey<Block> COLD_LIGHT = TagKey.of(RegistryKeys.BLOCK, new Identifier("unbeta-content", "cold_light"));
    private static final int REACH = 4;   // light 15 fades below any melt threshold within 4 blocks

    private Heat() {}

    /** True if a hot light source near pos would, on its own, light pos above threshold. */
    public static boolean hotLightAbove(WorldView world, BlockPos pos, int threshold) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int dx = -REACH; dx <= REACH; dx++) {
            for (int dy = -REACH; dy <= REACH; dy++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    int dist = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (dist == 0 || dist > REACH) continue;
                    BlockState s = world.getBlockState(p.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
                    int light = s.getLuminance();
                    if (light - dist > threshold && !s.isIn(COLD_LIGHT)) return true;
                }
            }
        }
        return false;
    }
}
