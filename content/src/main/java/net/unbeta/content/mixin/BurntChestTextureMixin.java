package net.unbeta.content.mixin;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.util.SpriteIdentifier;
import net.unbeta.content.burntchest.BurntChestBlockEntity;
import net.unbeta.content.client.burntchest.BurntChestsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla picks normal / trapped / christmas chest textures here; Burnt Chests get their own. */
@Mixin(TexturedRenderLayers.class)
public abstract class BurntChestTextureMixin {

    @Inject(method = "getChestTextureId(Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/block/enums/ChestType;Z)Lnet/minecraft/client/util/SpriteIdentifier;",
            at = @At("HEAD"), cancellable = true)
    private static void unbeta_burntTexture(BlockEntity blockEntity, ChestType type, boolean christmas,
                                            CallbackInfoReturnable<SpriteIdentifier> cir) {
        if (blockEntity instanceof BurntChestBlockEntity) cir.setReturnValue(BurntChestsClient.forType(type));
    }
}
