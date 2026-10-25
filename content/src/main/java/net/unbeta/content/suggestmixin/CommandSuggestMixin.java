package net.unbeta.content.suggestmixin;

import net.minecraft.command.CommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Friendlier command autocomplete (on your own game only - nothing about the items changes):
 * - Unbeta's things are suggested from their name alone, like vanilla's: "heavens_arrow"
 *   offers unbeta-content:heavens_arrow without typing "unbeta-content:" first.
 * - "wood_" finds "wooden_" too: "wood_sw" offers wooden_sword.
 * Kept in its own optional mixin set: if a future game version breaks it, the game still runs
 * and only this nicety is lost.
 */
@Mixin(CommandSource.class)
public interface CommandSuggestMixin {

    @Redirect(method = "forEachMatching(Ljava/lang/Iterable;Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Consumer;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z"), require = 0)
    private static boolean unbeta_ourNamesToo(String namespace, Object other) {
        return namespace.equals(other) || namespace.startsWith("unbeta");
    }

    @Inject(method = "shouldSuggest(Ljava/lang/String;Ljava/lang/String;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private static void unbeta_woodMeansWooden(String remaining, String candidate, CallbackInfoReturnable<Boolean> cir) {
        String r = remaining.toLowerCase(java.util.Locale.ROOT);
        if (r.contains("wood_") && CommandSource.shouldSuggest(r.replace("wood_", "wooden_"), candidate)) {
            cir.setReturnValue(true);
        }
    }
}
