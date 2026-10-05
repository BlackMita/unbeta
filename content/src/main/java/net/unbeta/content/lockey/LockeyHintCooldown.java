package net.unbeta.content.lockey;

import net.minecraft.entity.player.PlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The bound Lockey's "Chest at..." hint, at most once per chat-line fade (~10 s) per player. */
public final class LockeyHintCooldown {

    private static final long COOLDOWN_TICKS = 200; // a vanilla chat line shows ~10 s before fading
    private static final Map<UUID, Long> LAST = new ConcurrentHashMap<>();

    private LockeyHintCooldown() {}

    /** True if the hint may show now (and records that it did). */
    public static boolean ready(PlayerEntity player, long now) {
        Long last = LAST.get(player.getUuid());
        if (last != null && now - last < COOLDOWN_TICKS) return false;
        LAST.put(player.getUuid(), now);
        return true;
    }
}
