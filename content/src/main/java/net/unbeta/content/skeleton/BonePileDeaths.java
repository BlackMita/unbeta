package net.unbeta.content.skeleton;

import net.minecraft.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gear snapshotted at the instant a skeleton dies, held until its body is removed
 * ~20 ticks later, when the bone pile is placed where the body actually came to rest.
 *
 * <p>Deliberately NOT in the mixin package: Mixin owns that package and refuses to
 * load plain helper classes from it.
 */
public final class BonePileDeaths {
    public static final Map<UUID, List<ItemStack>> PENDING = new ConcurrentHashMap<>();
    private BonePileDeaths() {}
}
