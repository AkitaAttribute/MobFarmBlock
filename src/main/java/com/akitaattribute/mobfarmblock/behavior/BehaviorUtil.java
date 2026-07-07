package com.akitaattribute.mobfarmblock.behavior;

import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.XpProfile;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.registry.Registry;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;

final class BehaviorUtil {
    private BehaviorUtil() {
    }

    static AttackResult attack(MobFarmContext context) {
        if (context.stored().isEmpty()) {
            return AttackResult.PASS;
        }

        context.stored().count--;
        int lootingLevel = getLootingLevel(context);
        for (DropRule rule : context.stored().dropProfile.drops()) {
            rollDrop(context, rule, lootingLevel);
        }
        awardXp(context);
        return AttackResult.SUCCESS;
    }

    static void output(MobFarmContext context, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        BlockEntity below = context.level().getBlockEntity(context.pos().down());
        if (below instanceof Inventory inventory) {
            stack = insertIntoInventory(inventory, stack);
        }
        if (!stack.isEmpty()) {
            ItemScatterer.spawn(
                    context.level(),
                    context.pos().getX() + 0.5,
                    context.pos().getY() + 1.0,
                    context.pos().getZ() + 0.5,
                    stack
            );
        }
    }

    private static void rollDrop(MobFarmContext context, DropRule rule, int lootingLevel) {
        double chance = rule.chance();
        if (rule.affectedByLooting()) {
            chance += lootingLevel * rule.lootingChanceBonus();
        }
        chance = Math.clamp(chance, 0.0, 1.0);
        if (context.random().nextDouble() > chance) {
            return;
        }

        int baseCount = randomBetween(context, rule.minCount(), rule.maxCount());
        int bonusCount = 0;
        if (rule.affectedByLooting() && lootingLevel > 0 && rule.lootingMaxBonus() > 0) {
            bonusCount = context.random().nextInt(lootingLevel * rule.lootingMaxBonus() + 1);
        }
        int finalCount = baseCount + bonusCount;
        if (finalCount > 0) {
            output(context, new ItemStack(Registries.ITEM.get(rule.itemId()), finalCount));
        }
    }

    private static int randomBetween(MobFarmContext context, int min, int max) {
        if (max <= min) {
            return min;
        }
        return min + context.random().nextInt(max - min + 1);
    }

    private static ItemStack insertIntoInventory(Inventory inventory, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
            ItemStack current = inventory.getStack(slot);
            if (current.isEmpty()) {
                inventory.setStack(slot, remaining.copy());
                return ItemStack.EMPTY;
            }
            if (ItemStack.areItemsAndComponentsEqual(current, remaining) && current.getCount() < current.getMaxCount()) {
                int moved = Math.min(remaining.getCount(), current.getMaxCount() - current.getCount());
                current.increment(moved);
                remaining.decrement(moved);
            }
        }
        return remaining;
    }

    private static void awardXp(MobFarmContext context) {
        XpProfile xp = context.stored().dropProfile.xp();
        if (xp.maxXp() <= 0 || !(context.level() instanceof ServerWorld serverWorld)) {
            return;
        }
        int amount = randomBetween(context, xp.minXp(), xp.maxXp());
        if (amount > 0) {
            ExperienceOrbEntity.spawn(serverWorld, context.pos().toCenterPos(), amount);
        }
    }

    private static int getLootingLevel(MobFarmContext context) {
        ItemEnchantmentsComponent enchantments = context.heldItem().getOrDefault(
                DataComponentTypes.ENCHANTMENTS,
                ItemEnchantmentsComponent.DEFAULT
        );
        // Avoid live loot tables; use the held stack's enchantment component directly.
        for (RegistryEntry<Enchantment> enchantment : enchantments.getEnchantments()) {
            if (enchantment.matchesKey(Enchantments.LOOTING)) {
                return enchantments.getLevel(enchantment);
            }
        }
        return 0;
    }
}
