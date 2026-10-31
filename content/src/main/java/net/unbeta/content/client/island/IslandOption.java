package net.unbeta.content.client.island;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.client.gui.widget.GridWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;
import net.minecraft.world.GameRules;
import net.unbeta.content.island.Island;

import java.util.ArrayList;
import java.util.List;

/** "Island Size: Off / 12 / 24 / 48 / 96 / 192", laid out like the Generate Structures row. */
public final class IslandOption {

    private IslandOption() {}

    private static final List<Integer> SIZES = List.of(0, 12, 24, 48, 96, 192);
    private static final int WIDTH = 310, BUTTON = 44;

    public static GridWidget row(WorldCreator creator) {
        int current = read(creator);
        List<Integer> values = new ArrayList<>(SIZES);
        if (!values.contains(current)) {          // a size typed into the game rule by hand
            values.add(current);
            values.sort(Integer::compare);
        }
        CyclingButtonWidget<Integer> button = CyclingButtonWidget.<Integer>builder(v -> v == 0 ? Text.translatable("options.off") : Text.literal(String.valueOf(v)))
                .values(values)
                .initially(current)
                .omitKeyText()
                .tooltip(v -> Tooltip.of(Text.translatable(v == 0 ? "unbeta-content.island.tooltip.off" : "unbeta-content.island.tooltip", v, v * 16)))
                .build(0, 0, BUTTON, 20, Text.translatable("unbeta-content.island"), (b, v) -> write(creator, v));
        creator.addListener(c -> {
            int now = read(c);
            if (button.getValue() != now && values.contains(now)) button.setValue(now);
        });
        TextWidget label = new TextWidget(WIDTH - BUTTON, 20, Text.translatable("unbeta-content.island"), MinecraftClient.getInstance().textRenderer).alignLeft();
        GridWidget row = new GridWidget();
        row.add(label, 0, 0, row.copyPositioner().marginLeft(1));
        row.add(button, 0, 1);
        return row;
    }

    private static int read(WorldCreator creator) {
        return creator.getGameRules().getInt(Island.RULE);
    }

    private static void write(WorldCreator creator, int chunks) {
        GameRules rules = creator.getGameRules();
        rules.get(Island.RULE).set(chunks, null);
        creator.setGameRules(rules);
    }
}
