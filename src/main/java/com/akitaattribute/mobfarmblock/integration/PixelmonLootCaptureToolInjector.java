package com.akitaattribute.mobfarmblock.integration;

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
    private PixelmonLootCaptureToolInjector() {}

    public static void appendCaptureTool(Object source, List<Object> drops, ServerPlayer player) {
        try {
            LivingEntity entity = resolveLivingEntity(source);
            if (entity == null || !isPixelmonEntity(entity)) return;

            StoredMob stored = MobProfileFactory.fromEntity(entity);
            if (stored == null || stored.isEmpty()) return;

            ItemStack captureTool = new ItemStack(ModItems.CAPTURE_TOOL.get());
            CaptureToolItem.setStoredMob(captureTool, stored.copyWithCount(1));
            drops.add(captureTool);
        } catch (Throwable error) {
            MobFarmBlockMod.LOGGER.warn("Unable to append Mob Farm capture tool to Pixelmon loot UI", error);
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
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
