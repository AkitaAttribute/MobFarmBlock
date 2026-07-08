package com.akitaattribute.mobfarmblock.behavior;

import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.debug.MobFarmDebug;
import com.akitaattribute.mobfarmblock.mob.XpProfile;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

final class BehaviorUtil {
    private BehaviorUtil() {}

    static AttackResult attack(MobFarmContext context) {
        if (context.stored().isEmpty()) return AttackResult.PASS;
        context.stored().count--;
        int lootingLevel = getLootingLevel(context);
        StringBuilder rolled = new StringBuilder();
        for (DropRule rule : context.stored().dropProfile.drops()) rollDrop(context, rule, lootingLevel, rolled);
        int xp = awardXp(context);
        MobFarmDebug.send(context.player(), net.minecraft.network.chat.Component.literal("Mob Farm Block Debug:\nProcessed mob"
                + "\n- mob id: " + context.stored().mobId
                + "\n- kind: " + context.stored().kind
                + "\n- species id: " + (context.stored().speciesId == null ? "unavailable" : context.stored().speciesId)
                + "\n- drop profile source: " + context.stored().dropProfileSource
                + "\n- drop rule count: " + context.stored().dropProfile.drops().size()
                + "\n- xp range: " + context.stored().dropProfile.xp().minXp() + "-" + context.stored().dropProfile.xp().maxXp()
                + "\n- xp rolled: " + xp
                + "\n- rolled drops: " + (rolled.isEmpty() ? "none" : rolled)));
        return AttackResult.SUCCESS;
    }

    static void output(MobFarmContext context, ItemStack stack) {
        if (stack.isEmpty()) return;
        BlockEntity below = context.level().getBlockEntity(context.pos().below());
        if (below != null) {
            IItemHandler handler = context.level().getCapability(Capabilities.ItemHandler.BLOCK, context.pos().below(), null);
            if (handler != null) stack = insert(handler, stack);
        }
        if (!stack.isEmpty()) Block.popResource(context.level(), context.pos().above(), stack);
    }

    private static ItemStack insert(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    private static void rollDrop(MobFarmContext context, DropRule rule, int lootingLevel, StringBuilder rolled) {
        double chance = rule.chance();
        if (rule.affectedByLooting()) chance += lootingLevel * rule.lootingChanceBonus();
        chance = Math.max(0.0, Math.min(1.0, chance));
        if (context.random().nextDouble() > chance) return;
        int count = randomBetween(context, rule.minCount(), rule.maxCount());
        if (rule.affectedByLooting() && lootingLevel > 0 && rule.lootingMaxBonus() > 0) {
            count += context.random().nextInt(lootingLevel * rule.lootingMaxBonus() + 1);
        }
        if (count > 0) {
            if (!rolled.isEmpty()) rolled.append(", ");
            rolled.append(rule.itemId()).append(" x").append(count);
            output(context, new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId()), count));
        }
    }

    private static int randomBetween(MobFarmContext context, int min, int max) {
        return max <= min ? min : min + context.random().nextInt(max - min + 1);
    }

    private static int awardXp(MobFarmContext context) {
        XpProfile xp = context.stored().dropProfile.xp();
        if (xp.maxXp() <= 0 || !(context.level() instanceof ServerLevel serverLevel)) return 0;
        int amount = randomBetween(context, xp.minXp(), xp.maxXp());
        if (amount > 0) ExperienceOrb.award(serverLevel, Vec3.atCenterOf(context.pos()), amount);
        return amount;
    }

    private static int getLootingLevel(MobFarmContext context) {
        var registry = context.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder.Reference<Enchantment> looting = registry.getHolderOrThrow(Enchantments.LOOTING);
        return context.heldItem().getEnchantmentLevel(looting);
    }
}
