package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akitaattribute.mobfarmblock.client.ClientEntityRenderCache;
import com.akitaattribute.mobfarmblock.client.PixelmonEntityRenderCache;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.mob.MobKind;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

@Mixin(value = PixelmonEntityRenderCache.class, remap = false)
public abstract class PixelmonRenderLoadBudgetMixin {
    private static final int MAX_PIXELMON_RENDER_CREATIONS_PER_TICK = 5;
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static Field mobFarmBlock$cacheField;
    private static long mobFarmBlock$budgetTick = Long.MIN_VALUE;
    private static int mobFarmBlock$creationsThisTick = 0;

    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true)
    private static void mobFarmBlock$budgetPixelmonRenderCreation(StoredMob stored, CallbackInfoReturnable<Entity> callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (stored == null || stored.isEmpty() || minecraft.level == null || stored.speciesId == null) return;
        if (stored.kind != MobKind.PIXELMON || !"pixelmon:pixelmon".equals(stored.mobId.toString())) return;

        String key = mobFarmBlock$cacheKey(stored);
        Optional<Entity> cached = mobFarmBlock$cachedEntity(key);
        if (cached.isPresent()) {
            ClientEntityRenderCache.freezeForRender(cached.get());
            callback.setReturnValue(cached.get());
            return;
        }

        if (!mobFarmBlock$claimCreationSlot(minecraft.level.getGameTime())) callback.setReturnValue(null);
    }

    private static boolean mobFarmBlock$claimCreationSlot(long gameTime) {
        if (mobFarmBlock$budgetTick != gameTime) {
            mobFarmBlock$budgetTick = gameTime;
            mobFarmBlock$creationsThisTick = 0;
        }
        if (mobFarmBlock$creationsThisTick >= MAX_PIXELMON_RENDER_CREATIONS_PER_TICK) return false;
        mobFarmBlock$creationsThisTick++;
        return true;
    }

    private static Optional<Entity> mobFarmBlock$cachedEntity(String key) {
        try {
            if (mobFarmBlock$cacheField == null) {
                mobFarmBlock$cacheField = PixelmonEntityRenderCache.class.getDeclaredField("CACHE");
                mobFarmBlock$cacheField.setAccessible(true);
            }
            Object value = mobFarmBlock$cacheField.get(null);
            if (value instanceof Map<?, ?> cache) {
                Object cached = cache.get(key);
                if (cached instanceof Entity entity) return Optional.of(entity);
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static String mobFarmBlock$cacheKey(StoredMob stored) {
        MobFarmConfig.PixelmonRenderReplayMode mode = MobFarmConfig.PIXELMON_RENDER_REPLAY_MODE.get();
        return mode + "|" + stored.mobId + "|" + stored.speciesId + "|" + stored.display.variantKey()
                + "|cm=" + mobFarmBlock$pixelmonSizeCentimeters(stored).orElse(0.0F)
                + "|" + mobFarmBlock$payloadHash(stored.pixelmonRenderSnapshot);
    }

    private static Optional<Float> mobFarmBlock$pixelmonSizeCentimeters(StoredMob stored) {
        if (stored.pixelmonRenderSnapshot != null && stored.pixelmonRenderSnapshot.sizeCentimeters() > 0.0F) return Optional.of(stored.pixelmonRenderSnapshot.sizeCentimeters());
        return mobFarmBlock$parseCentimetersFromText(stored.display == null ? "" : stored.display.variantKey());
    }

    private static Optional<Float> mobFarmBlock$parseCentimetersFromText(String text) {
        if (text == null || text.isBlank()) return Optional.empty();
        String sizeValue = mobFarmBlock$parseVariantValue(text, "size");
        String source = sizeValue.isBlank() ? text : sizeValue;
        Matcher matcher = NUMBER.matcher(source);
        if (!matcher.find()) return Optional.empty();
        try {
            float number = Float.parseFloat(matcher.group());
            String lower = source.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("cm")) return Optional.of(number);
            return Optional.of(number <= 10.0F ? number * 100.0F : number);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static String mobFarmBlock$parseVariantValue(String variantKey, String key) {
        if (variantKey == null) return "";
        for (String part : variantKey.split("\\|")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals(key)) return part.substring(equals + 1);
        }
        return "";
    }

    private static int mobFarmBlock$payloadHash(PixelmonRenderSnapshot snapshot) {
        return snapshot == null || snapshot.payload() == null ? 0 : java.util.Objects.hash(snapshot.payload(), snapshot.sizeCentimeters());
    }
}
