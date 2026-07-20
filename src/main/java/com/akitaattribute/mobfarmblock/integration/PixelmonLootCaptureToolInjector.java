package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;
import com.akitaattribute.mobfarmblock.mob.DropProfile;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.MobProfileFactory;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.akitaattribute.mobfarmblock.mob.XpProfile;
import com.akitaattribute.mobfarmblock.registry.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PixelmonLootCaptureToolInjector {
    private static final String DROPPED_ITEM_CLASS = "com.pixelmonmod.pixelmon.entities.pixelmon.drops.DroppedItem";

    private PixelmonLootCaptureToolInjector() {}

    public static void appendCaptureTool(Object source, List<Object> drops, ServerPlayer player) {
        int beforeSize = drops == null ? -1 : drops.size();
        String playerName = player == null ? "unknown" : player.getGameProfile().getName();
        String sourceClass = source == null ? "null" : source.getClass().getName();
        MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection attempt: sourceClass={} player={} dropsBefore={}", sourceClass, playerName, beforeSize);
        try {
            if (drops == null) {
                MobFarmBlockMod.LOGGER.warn("Mob Farm Pixelmon loot capture injection skipped: drops list was null for sourceClass={} player={}", sourceClass, playerName);
                return;
            }
            double configuredChance = MobFarmConfig.PIXELMON_CAPTURE_TOOL_DROP_CHANCE.get();
            if (configuredChance <= 0.0D) {
                MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection skipped: configured chance is {}", configuredChance);
                return;
            }

            LivingEntity entity = resolveLivingEntity(source);
            if (entity == null) {
                MobFarmBlockMod.LOGGER.warn("Mob Farm Pixelmon loot capture injection skipped: could not resolve LivingEntity from sourceClass={} player={} dropsBefore={}", sourceClass, playerName, beforeSize);
                return;
            }

            ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (!isPixelmonEntity(entity)) {
                MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection skipped: resolved entity type {} from sourceClass={} is not pixelmon:pixelmon", entityType, sourceClass);
                return;
            }

            StoredMob stored = MobProfileFactory.fromEntity(entity);
            if (stored == null || stored.isEmpty()) {
                MobFarmBlockMod.LOGGER.warn("Mob Farm Pixelmon loot capture injection skipped: MobProfileFactory returned empty profile for entityType={} sourceClass={}", entityType, sourceClass);
                return;
            }

            DropDecision decision = shouldDropCaptureTool(player, configuredChance);
            if (!decision.drop()) {
                if (player != null) PixelmonCaptureToolDropData.get(player).recordMiss(player);
                MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection skipped by chance: roll={} chance={} missesBefore={} guaranteeThreshold={}", decision.roll(), configuredChance, decision.missesBefore(), decision.guaranteeThreshold());
                return;
            }
            stored.dropProfileSource = "pixelmon:loot_ui_pending";

            ItemStack captureTool = new ItemStack(ModItems.CAPTURE_TOOL.get());
            CaptureToolItem.setStoredMob(captureTool, stored.copyWithCount(1));
            CaptureToolItem.setDiscardOnDeposit(captureTool, true);
            Object droppedCaptureTool = createPixelmonDroppedItem(captureTool);
            if (droppedCaptureTool == null) {
                MobFarmBlockMod.LOGGER.warn("Mob Farm Pixelmon loot capture injection skipped: could not create Pixelmon DroppedItem wrapper for species={} entityType={}", stored.speciesId, entityType);
                return;
            }
            drops.add(droppedCaptureTool);
            if (player != null) PixelmonCaptureToolDropData.get(player).reset(player);
            MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection appended pending filled Capture Tool wrapper: species={} entityType={} chance={} roll={} guaranteed={} missesBefore={} guaranteeThreshold={} wrapperClass={} dropsBefore={} dropsAfter={} player={}", stored.speciesId, entityType, configuredChance, decision.roll(), decision.guaranteed(), decision.missesBefore(), decision.guaranteeThreshold(), droppedCaptureTool.getClass().getName(), beforeSize, drops.size(), playerName);
        } catch (Throwable error) {
            MobFarmBlockMod.LOGGER.warn("Unable to append Mob Farm capture tool to Pixelmon loot UI from sourceClass={} player={} dropsBefore={}", sourceClass, playerName, beforeSize, error);
        }
    }

    public static void updateCaptureToolObservedLoot(Object source, List<Object> drops, ServerPlayer player) {
        int listSize = drops == null ? -1 : drops.size();
        String playerName = player == null ? "unknown" : player.getGameProfile().getName();
        try {
            if (drops == null) return;
            DropProfile observed = observedLootProfile(drops, XpProfile.NONE);
            int updated = 0;
            for (Object drop : drops) {
                ItemStack stack = extractItemStack(drop);
                if (stack == null || stack.isEmpty() || !stack.is(ModItems.CAPTURE_TOOL.get()) || !CaptureToolItem.hasStoredMob(stack)) continue;
                StoredMob stored = CaptureToolItem.getStoredMob(stack);
                if (stored == null || stored.isEmpty()) continue;
                stored.dropProfile = withXp(observed, stored.dropProfile.xp());
                stored.dropProfileSource = "pixelmon:loot_ui_observed";
                CaptureToolItem.setStoredMob(stack, stored.copyWithCount(1));
                CaptureToolItem.setDiscardOnDeposit(stack, true);
                updated++;
            }
            MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection observed-loot update: sourceClass={} player={} listSize={} observedRules={} updatedTools={}", source == null ? "null" : source.getClass().getName(), playerName, listSize, observed.drops().size(), updated);
        } catch (Throwable error) {
            MobFarmBlockMod.LOGGER.warn("Unable to update Mob Farm capture tool observed loot from Pixelmon list player={} listSize={}", playerName, listSize, error);
        }
    }

    private static DropDecision shouldDropCaptureTool(ServerPlayer player, double configuredChance) {
        double chance = Math.max(0.0D, Math.min(1.0D, configuredChance));
        double roll = player == null ? Math.random() : player.level().random.nextDouble();
        long missesBefore = player == null ? 0L : PixelmonCaptureToolDropData.get(player).getMisses(player);
        long guaranteeThreshold = guaranteeThreshold(chance);
        boolean randomDrop = roll < chance;
        boolean guaranteed = player != null && guaranteeThreshold > 0L && missesBefore + 1L >= guaranteeThreshold;
        return new DropDecision(randomDrop || guaranteed, randomDrop, guaranteed, roll, missesBefore, guaranteeThreshold);
    }

    private static long guaranteeThreshold(double chance) {
        if (chance <= 0.0D) return Long.MAX_VALUE;
        if (chance >= 1.0D) return 1L;
        double inverse = Math.ceil(1.0D / chance);
        if (!Double.isFinite(inverse) || inverse >= Long.MAX_VALUE) return Long.MAX_VALUE;
        return Math.max(1L, (long) inverse);
    }

    private static DropProfile withXp(DropProfile profile, XpProfile xp) {
        return new DropProfile(profile.drops(), xp, profile.observations(), profile.observedDropCounts(), profile.observedDropPrototypes());
    }

    private static DropProfile observedLootProfile(List<Object> drops, XpProfile xp) {
        Map<ResourceLocation, ObservedLootRule> observed = new LinkedHashMap<>();
        for (Object drop : drops) {
            ItemStack stack = extractItemStack(drop);
            if (stack == null || stack.isEmpty() || stack.is(Items.AIR) || stack.is(ModItems.CAPTURE_TOOL.get())) continue;
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (itemId == null) continue;
            int count = Math.max(1, stack.getCount());
            observed.merge(itemId, new ObservedLootRule(count, count), ObservedLootRule::merge);
        }

        List<DropRule> rules = new ArrayList<>();
        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        Map<ResourceLocation, DropRule> prototypes = new LinkedHashMap<>();
        observed.forEach((itemId, observedRule) -> {
            DropRule rule = new DropRule(itemId, 1.0D, observedRule.minCount(), observedRule.maxCount(), false, 0.0D, 0);
            rules.add(rule);
            counts.put(itemId, 1);
            prototypes.put(itemId, rule);
        });
        return new DropProfile(rules, xp, 1, counts, prototypes);
    }

    private static ItemStack extractItemStack(Object drop) {
        if (drop instanceof ItemStack stack) return stack;
        ItemStack fromMethod = extractItemStackFromMethod(drop);
        if (fromMethod != null) return fromMethod;
        return extractItemStackFromField(drop);
    }

    private static ItemStack extractItemStackFromMethod(Object drop) {
        if (drop == null) return null;
        for (String methodName : List.of("getItemStack", "getStack", "getItem", "stack", "item")) {
            try {
                Method method = drop.getClass().getMethod(methodName);
                if (!ItemStack.class.isAssignableFrom(method.getReturnType())) continue;
                method.setAccessible(true);
                Object result = method.invoke(drop);
                if (result instanceof ItemStack stack) return stack;
            } catch (ReflectiveOperationException ignored) {
                // Try the next common accessor name.
            }
        }
        return null;
    }

    private static ItemStack extractItemStackFromField(Object drop) {
        if (drop == null) return null;
        Class<?> type = drop.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                if (!ItemStack.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(drop);
                    if (value instanceof ItemStack stack) return stack;
                } catch (IllegalAccessException ignored) {
                    // Try the next ItemStack field.
                }
            }
            type = type.getSuperclass();
        }
        return null;
    }

    private static Object createPixelmonDroppedItem(ItemStack stack) {
        try {
            Class<?> droppedItemClass = Class.forName(DROPPED_ITEM_CLASS);
            Constructor<?> constructor = droppedItemClass.getConstructor(ItemStack.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(stack, stack.getCount());
        } catch (Throwable error) {
            MobFarmBlockMod.LOGGER.warn("Mob Farm Pixelmon loot capture injection failed to wrap ItemStack as Pixelmon DroppedItem", error);
            return null;
        }
    }

    private static boolean isPixelmonEntity(LivingEntity entity) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return "pixelmon".equals(id.getNamespace()) && "pixelmon".equals(id.getPath());
    }

    private static LivingEntity resolveLivingEntity(Object source) {
        if (source instanceof LivingEntity living) return living;
        Object entity = invokeNoArg(source, "getEntity");
        if (entity instanceof LivingEntity living) return living;
        entity = invokeNoArg(source, "getOrCreatePixelmon");
        if (entity instanceof LivingEntity living) return living;
        entity = invokeNoArg(source, "getOrSpawnPixelmon");
        return entity instanceof LivingEntity living ? living : null;
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            Object result = method.invoke(target);
            MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection reflection: {}#{} -> {}", target.getClass().getName(), methodName, result == null ? "null" : result.getClass().getName());
            return result;
        } catch (ReflectiveOperationException ignored) {
            MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection reflection: {}#{} unavailable", target.getClass().getName(), methodName);
            return null;
        }
    }

    private record DropDecision(boolean drop, boolean randomDrop, boolean guaranteed, double roll, long missesBefore, long guaranteeThreshold) {}

    private record ObservedLootRule(int minCount, int maxCount) {
        private static ObservedLootRule merge(ObservedLootRule existing, ObservedLootRule incoming) {
            return new ObservedLootRule(
                    Math.min(existing.minCount(), incoming.minCount()),
                    Math.max(existing.maxCount(), incoming.maxCount())
            );
        }
    }
}
