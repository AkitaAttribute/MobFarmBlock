package com.akitaattribute.mobfarmblock.integration;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;
import com.akitaattribute.mobfarmblock.mob.MobProfileFactory;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.akitaattribute.mobfarmblock.registry.ModItems;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

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

            ItemStack captureTool = new ItemStack(ModItems.CAPTURE_TOOL.get());
            CaptureToolItem.setStoredMob(captureTool, stored.copyWithCount(1));
            Object droppedCaptureTool = createPixelmonDroppedItem(captureTool);
            if (droppedCaptureTool == null) {
                MobFarmBlockMod.LOGGER.warn("Mob Farm Pixelmon loot capture injection skipped: could not create Pixelmon DroppedItem wrapper for species={} entityType={}", stored.speciesId, entityType);
                return;
            }
            drops.add(droppedCaptureTool);
            MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon loot capture injection appended filled Capture Tool wrapper: species={} entityType={} wrapperClass={} dropsBefore={} dropsAfter={} player={}", stored.speciesId, entityType, droppedCaptureTool.getClass().getName(), beforeSize, drops.size(), playerName);
        } catch (Throwable error) {
            MobFarmBlockMod.LOGGER.warn("Unable to append Mob Farm capture tool to Pixelmon loot UI from sourceClass={} player={} dropsBefore={}", sourceClass, playerName, beforeSize, error);
        }
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
}
