package com.akitaattribute.mobfarmblock.item;

import java.util.List;

import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.MobDisplayNames;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

final class MobFarmItemTooltip {
    static void appendStoredMob(StoredMob stored, List<Component> tooltip) {
        if (stored == null || stored.isEmpty()) return;

        tooltip.add(Component.literal("Mob Count: ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(Long.toString(stored.count)).withStyle(ChatFormatting.AQUA)));

        if (stored.dropProfile == null) return;
        for (DropRule rule : stored.dropProfile.drops()) {
            if (rule == null || rule.itemId() == null) continue;
            ItemStack dropStack = new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId()));
            String itemName = dropStack.isEmpty()
                    ? MobDisplayNames.prettyName(rule.itemId().getPath())
                    : dropStack.getHoverName().getString();

            tooltip.add(Component.literal(itemName)
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(" " + formatCountRange(rule)).withStyle(ChatFormatting.AQUA))
                    .append(Component.literal(" " + formatChance(rule.chance())).withStyle(ChatFormatting.YELLOW)));
        }
    }

    private static String formatCountRange(DropRule rule) {
        int min = Math.max(0, rule.minCount());
        int max = Math.max(min, rule.maxCount());
        return min == max ? Integer.toString(min) : min + "-" + max;
    }

    private static String formatChance(double chance) {
        double clamped = Math.max(0.0D, Math.min(1.0D, chance));
        return Math.round(clamped * 100.0D) + "%";
    }

    private MobFarmItemTooltip() {}
}
