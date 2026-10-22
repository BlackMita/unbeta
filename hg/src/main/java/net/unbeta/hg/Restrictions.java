package net.unbeta.hg;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;

/**
 * The restricted mode (lobby, countdown, the end, and players who are out): you can walk, jump,
 * look and chat. You can't break, place, use, open, attack, pick up or be hurt. An operator
 * in Creative is left alone, for setting things up.
 */
public final class Restrictions {

    private Restrictions() {}

    /** Held back right now? (Creative operators never are.) */
    public static boolean held(PlayerEntity player) {
        if (!UnbetaHG.ACTIVE) return false;
        if (player.isCreative() && player.hasPermissionLevel(2)) return false;
        return Round.restricted(player);
    }

    public static void register() {
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> held(player) ? ActionResult.FAIL : ActionResult.PASS);
        UseItemCallback.EVENT.register((player, world, hand) ->
                held(player) ? TypedActionResult.fail(player.getStackInHand(hand)) : TypedActionResult.pass(player.getStackInHand(hand)));
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> held(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> held(player) ? ActionResult.FAIL : ActionResult.PASS);
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> held(player) ? ActionResult.FAIL : ActionResult.PASS);
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> !held(player));

        // Nobody is hurt while restricted or in the grace minute, and a restricted player hurts no one.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof PlayerEntity p && !p.isCreative() && Round.unhurtable(p)) return false;
            if (source.getAttacker() instanceof PlayerEntity attacker && held(attacker)) return false;
            return true;
        });
    }
}
