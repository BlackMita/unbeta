package net.unbeta.content.corruption;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

/**
 * Inevitable Zombification: a 10-minute countdown that kills at zero.
 *
 * <ul>
 *   <li>Any piece of gold armour freezes the countdown (see ZombificationFreezeMixin) and
 *       blocks new infection from bites (Phase 5b).</li>
 *   <li>Only a golden apple cures it (GoldenAppleCureMixin). Milk, /effect clear and totems
 *       leave it in place (ZombificationMilkMixin).</li>
 *   <li>Being infected again resets it to the full 10 minutes - vanilla keeps the longer
 *       duration when an effect is reapplied.</li>
 * </ul>
 */
public final class Zombification {

    public static final int DURATION = 20 * 60 * 10; // 10 minutes
    public static final RegistryKey<DamageType> DAMAGE_TYPE = RegistryKey.of(
            RegistryKeys.DAMAGE_TYPE, new Identifier("unbeta-content", "zombification"));

    public static StatusEffect EFFECT;

    private Zombification() {}

    public static void register() {
        EFFECT = Registry.register(Registries.STATUS_EFFECT,
                new Identifier("unbeta-content", "inevitable_zombification"),
                new InevitableZombificationEffect());
    }

    /** Full duration; no swirling particles (onlookers can't tell); icon shown to the player. */
    public static void inflict(LivingEntity entity) {
        entity.addStatusEffect(new StatusEffectInstance(EFFECT, DURATION, 0, false, false, true));
    }

    public static boolean isWearingGold(LivingEntity entity) {
        for (ItemStack stack : entity.getArmorItems()) {
            if (stack.getItem() instanceof ArmorItem armor && armor.getMaterial() == ArmorMaterials.GOLD) {
                return true;
            }
        }
        return false;
    }

    public static DamageSource damageSource(World world) {
        return new DamageSource(world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(DAMAGE_TYPE));
    }
}
