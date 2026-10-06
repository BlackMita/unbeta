package net.unbeta.content.lockey;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/** The locked-chest refusal (deny sound + where the key is), shared by chests and locked mimics. */
public final class LockeyMessages {

    private LockeyMessages() {}

    public static void deny(ServerWorld world, PlayerEntity player, BlockPos at, UUID owner) {
        world.playSound(null, at, LockeyRegistry.CHEST_DENY, SoundCategory.BLOCKS, 0.8F, 1.0F);
        BlockPos keyPos = LockeyLocator.find(world, owner);
        Text msg;
        if (keyPos != null) {
            msg = Text.literal("Key is at " + keyPos.getX() + ", " + keyPos.getY() + ", " + keyPos.getZ())
                    .formatted(Formatting.GOLD);
        } else {
            BlockPos seen = LockeyState.lastSeen(world, owner);
            if (LockeyState.isRevoked(world, owner)) {
                msg = Text.literal("Will never unlock.").formatted(Formatting.RED);
            } else if (seen != null) {
                msg = Text.literal("Key last seen at " + seen.getX() + ", " + seen.getY() + ", " + seen.getZ())
                        .formatted(Formatting.GOLD);
            } else {
                msg = Text.literal("Key location unknown.").formatted(Formatting.GOLD);
            }
        }
        player.sendMessage(msg, false);
    }
}
