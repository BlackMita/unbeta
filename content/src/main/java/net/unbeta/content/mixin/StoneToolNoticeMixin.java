package net.unbeta.content.mixin;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.CraftingResultInventory;
import net.minecraft.inventory.RecipeInputInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.CraftingScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stone tools are gone, so laying one out on a crafting table makes nothing. The first time a
 * player does it, chat tells them that's on purpose (once ever, per player).
 */
@Mixin(CraftingScreenHandler.class)
public abstract class StoneToolNoticeMixin {

    private static final String TOLD = "unbeta.stone_tools_told";
    private static final String[][] SHAPES = {
            {"SSS", ".|.", ".|."},      // pickaxe
            {"SS", "S|", ".|"},         // axe
            {"S", "|", "|"},            // shovel
            {"SS", ".|", ".|"},         // hoe
            {"S", "S", "|"},            // sword
    };

    @Inject(method = "updateResult", at = @At("RETURN"))
    private static void unbeta_stoneToolNotice(ScreenHandler handler, World world, PlayerEntity player,
                                               RecipeInputInventory grid, CraftingResultInventory result, CallbackInfo ci) {
        if (world.isClient || player.getCommandTags().contains(TOLD) || !result.getStack(0).isEmpty()) return;
        if (!isStoneTool(grid)) return;
        player.addCommandTag(TOLD);
        player.sendMessage(Text.literal("Stone tools removed. Iron tier now follows wood.").formatted(Formatting.YELLOW), false);
    }

    private static boolean isStoneTool(RecipeInputInventory grid) {
        int w = grid.getWidth(), h = grid.getHeight();
        int minX = w, minY = h, maxX = -1, maxY = -1;
        char[][] cells = new char[h][w];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            char c = code(grid.getStack(x + y * w));
            if (c == 'X') return false;
            cells[y][x] = c;
            if (c != '.') { minX = Math.min(minX, x); maxX = Math.max(maxX, x); minY = Math.min(minY, y); maxY = Math.max(maxY, y); }
        }
        if (maxX < 0) return false;
        for (String[] shape : SHAPES) {
            if (shape.length != maxY - minY + 1 || shape[0].length() != maxX - minX + 1) continue;
            if (matches(cells, minX, minY, shape, false) || matches(cells, minX, minY, shape, true)) return true;
        }
        return false;
    }

    private static boolean matches(char[][] cells, int ox, int oy, String[] shape, boolean mirror) {
        int sw = shape[0].length();
        for (int y = 0; y < shape.length; y++) for (int x = 0; x < sw; x++) {
            char want = shape[y].charAt(mirror ? sw - 1 - x : x);
            if (cells[oy + y][ox + x] != want) return false;
        }
        return true;
    }

    private static char code(ItemStack s) {
        if (s.isEmpty()) return '.';
        if (s.isOf(Items.STICK)) return '|';
        if (s.isOf(Items.COBBLESTONE) || s.isOf(Items.COBBLED_DEEPSLATE) || s.isOf(Items.BLACKSTONE) || s.isOf(Items.STONE)) return 'S';
        return 'X';
    }
}
