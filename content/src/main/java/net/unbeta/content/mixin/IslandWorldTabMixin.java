package net.unbeta.content.mixin;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.GridWidget;
import net.unbeta.content.client.island.IslandOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The World tab of Create New World gets an Island Size row, right under Bonus Chest. */
@Mixin(targets = "net/minecraft/client/gui/screen/world/CreateWorldScreen$WorldTab")
public abstract class IslandWorldTabMixin {

    @Unique private GridWidget.Adder unbeta_rows;

    /** Keep hold of the tab's own column of rows (the first one made, one column wide). */
    @Redirect(method = "<init>", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/widget/GridWidget;createAdder(I)Lnet/minecraft/client/gui/widget/GridWidget$Adder;"))
    private GridWidget.Adder unbeta_keepRows(GridWidget grid, int columns) {
        GridWidget.Adder adder = grid.createAdder(columns);
        if (unbeta_rows == null && columns == 1) unbeta_rows = adder;
        return adder;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void unbeta_addIslandRow(CreateWorldScreen screen, CallbackInfo ci) {
        if (unbeta_rows != null) unbeta_rows.add(IslandOption.row(screen.getWorldCreator()));
    }
}
