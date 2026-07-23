package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;
import com.akitaattribute.mobfarmblock.mob.MobDisplayNames;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Pixelmon drop chance and cooldown/status in the intended compact/expanded look UI locations. */
@Mixin(targets = "com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer")
public abstract class PixelmonLookUiRowsMixin {
    private static final int TEXT_WHITE = 0xFFFFFF;
    private static final int TEXT_GREEN = 0x55FF55;
    private static final int TEXT_YELLOW = 0xFFFF55;

    @Inject(method = "compactRows", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mobFarmBlock$compactPixelmonDropRows(StoredMob stored, long now, CallbackInfoReturnable<List<?>> callback) {
        if (usesPixelmonDropHarvest(stored)) callback.setReturnValue(pixelmonDropRows(stored, now, false));
    }

    @Inject(method = "expandedRows", at = @At("HEAD"), cancellable = true, remap = false)
    private static void mobFarmBlock$expandedPixelmonDropRows(StoredMob stored, long now, CallbackInfoReturnable<List<?>> callback) {
        if (usesPixelmonDropHarvest(stored)) callback.setReturnValue(pixelmonDropRows(stored, now, true));
    }

    private static boolean usesPixelmonDropHarvest(StoredMob stored) {
        if (stored == null || stored.interactionProfile == null) return false;
        for (InteractionDefinition definition : stored.interactionProfile.definitions()) {
            if (definition.methodId().equals(MobFarmBlockMod.id("pixelmon_drops"))) return true;
        }
        return false;
    }

    private static List<Object> pixelmonDropRows(StoredMob stored, long now, boolean expanded) {
        List<Object> rows = new ArrayList<>();
        String ready = readyValue(stored, now);
        int color = readyColor(stored, now);
        if (stored.dropProfile.drops().isEmpty()) {
            rows.add(lookRow(new ItemStack(Items.CHEST), "Pixelmon Drops", ready, TEXT_WHITE, color));
            return rows;
        }
        for (DropRule rule : stored.dropProfile.drops()) {
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId()));
            String value = expanded ? formatChance(rule.chance()) + " " + ready : ready;
            rows.add(lookRow(stack, itemLabel(stack, rule.itemId().getPath()), value, TEXT_WHITE, color));
        }
        return rows;
    }

    private static Object lookRow(ItemStack icon, String label, String value, int labelColor, int valueColor) {
        try {
            Class<?> type = Class.forName("com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer$LookRow");
            Constructor<?> constructor = type.getDeclaredConstructor(ItemStack.class, String.class, String.class, int.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(icon, label, value, labelColor, valueColor);
        } catch (Throwable error) {
            throw new IllegalStateException("Failed to create Pixelmon look UI row", error);
        }
    }

    private static String readyValue(StoredMob stored, long now) {
        long readyAt = stored.readyAtTicks.getOrDefault(MobFarmBlockMod.id("pixelmon_drops"), 0L);
        if (now >= readyAt) return "Ready";
        long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
        return seconds + "s";
    }

    private static int readyColor(StoredMob stored, long now) {
        return now >= stored.readyAtTicks.getOrDefault(MobFarmBlockMod.id("pixelmon_drops"), 0L) ? TEXT_GREEN : TEXT_YELLOW;
    }

    private static String formatChance(double chance) {
        return Math.round(chance * 100.0D) + "%";
    }

    private static String itemLabel(ItemStack stack, String fallbackPath) {
        String label = stack.getHoverName().getString();
        return label == null || label.isBlank() ? MobDisplayNames.prettyName(fallbackPath) : label;
    }
}
