package net.unbeta.content.corruption;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * What rises where a player died of Inevitable Zombification: a zombie wearing their armour,
 * holding what was in their hands, and carrying the rest of their inventory. Nothing is
 * lost - killing it returns every item exactly as it was (Curse of Vanishing excepted, as on
 * any death). XP still drops as orbs where the player died, like vanilla.
 *
 * <p>It's a real zombie to every other system (hunting, bites, chunk memory, seared flesh),
 * and is called "Zombie" everywhere - the player has to recognise their own gear. Guarded so
 * it can't lose the inventory: never despawns (even in Peaceful), never turns drowned, never
 * picks up items. With keepInventory on, the player keeps everything and nothing rises.
 */
public class RevenantEntity extends ZombieEntity {

    /** Everything the player carried that isn't worn or held. */
    private final List<ItemStack> carried = new ArrayList<>();

    public RevenantEntity(EntityType<? extends ZombieEntity> type, World world) {
        super(type, world);
        this.setCanPickUpLoot(false);
    }

    /** Hooked on death itself, so it only fires when the player really dies of zombification. */
    public static void registerDeathHook() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity player && source.isOf(Zombification.DAMAGE_TYPE)) {
                riseFrom(player);
            }
            return true; // never prevents the death
        });
    }

    private static void riseFrom(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        if (world.getGameRules().getBoolean(GameRules.KEEP_INVENTORY)) return;
        RevenantEntity revenant = CorruptionRegistry.REVENANT.create(world);
        if (revenant == null) return;

        revenant.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0.0F);
        revenant.setPersistent();

        PlayerInventory inv = player.getInventory();
        // PlayerInventory.armor is feet, legs, chest, head
        revenant.equipStack(EquipmentSlot.FEET,  inv.armor.get(0).copy());
        revenant.equipStack(EquipmentSlot.LEGS,  inv.armor.get(1).copy());
        revenant.equipStack(EquipmentSlot.CHEST, inv.armor.get(2).copy());
        revenant.equipStack(EquipmentSlot.HEAD,  inv.armor.get(3).copy());
        revenant.equipStack(EquipmentSlot.MAINHAND, inv.main.get(inv.selectedSlot).copy());
        revenant.equipStack(EquipmentSlot.OFFHAND, inv.offHand.get(0).copy());
        // Above 1.0 = always dropped, and dropped as-is (no random durability loss).
        for (EquipmentSlot slot : EquipmentSlot.values()) revenant.setEquipmentDropChance(slot, 2.0F);

        for (int i = 0; i < inv.main.size(); i++) {
            if (i == inv.selectedSlot) continue;
            ItemStack stack = inv.main.get(i);
            if (!stack.isEmpty()) revenant.carried.add(stack.copy());
        }

        inv.clear(); // vanilla now drops nothing but XP
        world.spawnEntity(revenant);
    }

    @Override
    protected void dropEquipment(DamageSource source, int lootingMultiplier, boolean allowDrops) {
        super.dropEquipment(source, lootingMultiplier, allowDrops);
        for (ItemStack stack : this.carried) {
            if (!stack.isEmpty() && !EnchantmentHelper.hasVanishingCurse(stack)) this.dropStack(stack);
        }
        this.carried.clear();
    }

    /** Would drop the stored inventory in the conversion. */
    @Override
    protected boolean canConvertInWater() {
        return false;
    }

    /** Zombies are deleted on switching to Peaceful; this one is carrying someone's life. */
    @Override
    protected boolean isDisallowedInPeaceful() {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        NbtList list = new NbtList();
        for (ItemStack stack : this.carried) {
            if (!stack.isEmpty()) list.add(stack.writeNbt(new NbtCompound()));
        }
        nbt.put("UnbetaCarried", list);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.carried.clear();
        NbtList list = nbt.getList("UnbetaCarried", 10); // 10 = compound
        for (int i = 0; i < list.size(); i++) {
            this.carried.add(ItemStack.fromNbt(list.getCompound(i)));
        }
    }
}
