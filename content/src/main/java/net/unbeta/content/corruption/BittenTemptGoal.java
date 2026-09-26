package net.unbeta.content.corruption;

import net.minecraft.entity.ai.goal.TemptGoal;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;

/**
 * A bitten animal - infected, but not yet corrupted - rushes toward a player holding a
 * golden apple, at twice walking speed. The visible tell of a hidden infection, and it only
 * shows while holding the cure. Stops as soon as the bite is cured.
 */
public class BittenTemptGoal extends TemptGoal {

    public BittenTemptGoal(PathAwareEntity mob) {
        super(mob, 2.0, Ingredient.ofItems(Items.GOLDEN_APPLE), false);
    }

    @Override
    public boolean canStart() {
        return this.mob.getCommandTags().contains(CorruptionMemory.BITTEN_TAG) && super.canStart();
    }
}
