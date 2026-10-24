package net.unbeta.content.arrow;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.dispenser.ProjectileDispenserBehavior;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Position;
import net.minecraft.world.World;

/**
 * Heaven's Arrow: an arrow fletched with a Goldreath's golden feather. Same damage as an arrow,
 * catches and carries fire like one (FireArrows), and whatever it hits floats up: Levitation IV
 * for 1.5 seconds, the Goldreath bullet's lift in a short burst (see HeavenArrowMixin).
 * Picked back up, it is still a Heaven's Arrow.
 */
public final class HeavenArrows {

    public static final int LEVITATION_TICKS = 30;   // 1.5 s
    public static final int LEVITATION_LEVEL = 3;    // IV, like the Goldreath's bullet

    public static Item HEAVENS_ARROW;

    private HeavenArrows() {}

    public static final class HeavenArrowItem extends ArrowItem {
        public HeavenArrowItem(Item.Settings settings) {
            super(settings);
        }

        @Override
        public PersistentProjectileEntity createArrow(World world, ItemStack stack, LivingEntity shooter) {
            ArrowEntity arrow = new ArrowEntity(world, shooter);
            ((HeavenArrowAccess) arrow).unbeta$setHeaven(true);
            return arrow;
        }
    }

    public static void register() {
        HEAVENS_ARROW = Registry.register(Registries.ITEM,
                new Identifier("unbeta-content", "heavens_arrow"), new HeavenArrowItem(new Item.Settings()));
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.addAfter(Items.ARROW, HEAVENS_ARROW));
        DispenserBlock.registerBehavior(HEAVENS_ARROW, new ProjectileDispenserBehavior() {
            @Override
            protected ProjectileEntity createProjectile(World world, Position position, ItemStack stack) {
                ArrowEntity arrow = new ArrowEntity(world, position.getX(), position.getY(), position.getZ());
                arrow.pickupType = PersistentProjectileEntity.PickupPermission.ALLOWED;
                ((HeavenArrowAccess) arrow).unbeta$setHeaven(true);
                return arrow;
            }
        });
    }
}
