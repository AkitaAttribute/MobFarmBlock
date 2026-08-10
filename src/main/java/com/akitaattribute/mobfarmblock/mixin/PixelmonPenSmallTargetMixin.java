package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer;
import com.akitaattribute.mobfarmblock.client.PixelmonEntityRenderCache;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.world.entity.Entity;

@Mixin(value = MobFarmBlockEntityRenderer.class, remap = false)
public abstract class PixelmonPenSmallTargetMixin {
    private static final float PIXELMON_RENDER_BASE_Y = 0.58F;
    private static final float MAX_PIXELMON_TOP_Y = 0.98F;
    private static final float TARGET_PIXELMON_PEN_HEIGHT = MAX_PIXELMON_TOP_Y - PIXELMON_RENDER_BASE_Y;
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    @Inject(method = "placedEntityScale", at = @At("HEAD"), cancellable = true)
    private static void mobFarmBlock$enforcePixelmonPenSmallScale(StoredMob stored, Entity entity, boolean inspected, CallbackInfoReturnable<Float> callback) {
        if (!PixelmonEntityRenderCache.isPixelmonStored(stored)) return;
        boolean shrink = inspected || MobFarmConfig.PENS_ALWAYS_SHOW_SMALL.get();
        if (!shrink) return;

        float effectiveHeight = effectivePixelmonHeight(stored, entity);
        callback.setReturnValue(effectiveHeight > TARGET_PIXELMON_PEN_HEIGHT
                ? TARGET_PIXELMON_PEN_HEIGHT / Math.max(0.1F, effectiveHeight)
                : 1.0F);
    }

    private static float effectivePixelmonHeight(StoredMob stored, Entity entity) {
        float height = entity == null ? 0.35F : Math.max(entity.getBbHeight(), 0.35F);
        PixelmonRenderSnapshot snapshot = stored == null ? null : stored.pixelmonRenderSnapshot;
        if (snapshot != null) {
            if (snapshot.capturedHeight() > 0.05F) height = Math.max(height, snapshot.capturedHeight());
            if (snapshot.sizeCentimeters() > 0.0F) height = Math.max(height, snapshot.sizeCentimeters() / 100.0F);
        }
        height = Math.max(height, variantSizeMeters(stored).orElse(0.0F));
        height = Math.max(height, pixelmonEyeHeight(entity).orElse(0.0F));
        height = Math.max(height, pixelmonScaledBoundingHeight(entity).orElse(0.0F));
        height = Math.max(height, highScaleVisualHeight(entity).orElse(0.0F));
        return height;
    }

    private static Optional<Float> variantSizeMeters(StoredMob stored) {
        if (stored == null || stored.display == null) return Optional.empty();
        String variant = stored.display.variantKey();
        String size = parseVariantValue(variant, "size");
        String source = size.isBlank() ? variant : size;
        Matcher matcher = NUMBER.matcher(source);
        if (!matcher.find()) return Optional.empty();
        try {
            float value = Float.parseFloat(matcher.group());
            if (value <= 0.0F) return Optional.empty();
            String lower = source.toLowerCase(java.util.Locale.ROOT);
            float centimeters = lower.contains("cm") || value > 10.0F ? value : value * 100.0F;
            return Optional.of(centimeters / 100.0F);
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static String parseVariantValue(String variantKey, String key) {
        if (variantKey == null) return "";
        for (String part : variantKey.split("\\|")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals(key)) return part.substring(equals + 1);
        }
        return "";
    }

    private static Optional<Float> pixelmonEyeHeight(Entity entity) {
        return invoke(entity, "getEyeHeight").flatMap(PixelmonPenSmallTargetMixin::positiveFiniteNumber);
    }

    private static Optional<Float> pixelmonScaledBoundingHeight(Entity entity) {
        if (entity == null) return Optional.empty();
        Optional<Float> scaleFactor = invoke(entity, "getScaleFactor").flatMap(PixelmonPenSmallTargetMixin::positiveFiniteNumber);
        if (scaleFactor.isEmpty()) return Optional.empty();
        float height = Math.max(entity.getBbHeight(), 0.35F) * scaleFactor.get();
        return Float.isFinite(height) && height > 0.0F ? Optional.of(height) : Optional.empty();
    }

    private static Optional<Float> highScaleVisualHeight(Entity entity) {
        if (entity == null) return Optional.empty();
        float width = entity.getBbWidth();
        float height = entity.getBbHeight();
        boolean flatHighScale = height <= 0.50F && width >= height * 1.40F;
        return pixelmonModelScale(entity)
                .filter(scale -> scale >= 9.0F || (flatHighScale && scale >= 3.0F))
                .map(scale -> scale >= 9.0F ? scale * 0.50F : scale * 0.20F)
                .filter(value -> Float.isFinite(value) && value > 0.0F);
    }

    private static Optional<Float> pixelmonModelScale(Entity entity) {
        if (entity == null) return Optional.empty();
        Object models = invoke(entity, "getModel").orElse(null);
        Object firstModel = first(models).orElse(models);
        Object scale = invoke(firstModel, "scale").or(() -> readField(firstModel, "scale")).orElse(null);
        return maxVectorComponent(scale);
    }

    private static Optional<Object> first(Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) return Optional.ofNullable(item);
        }
        if (value != null && value.getClass().isArray() && java.lang.reflect.Array.getLength(value) > 0) {
            return Optional.ofNullable(java.lang.reflect.Array.get(value, 0));
        }
        return Optional.empty();
    }

    private static Optional<Float> maxVectorComponent(Object value) {
        if (value == null) return Optional.empty();
        float max = Float.NEGATIVE_INFINITY;
        for (String method : new String[] {"x", "y", "z", "getX", "getY", "getZ"}) {
            Optional<Float> component = invoke(value, method).flatMap(PixelmonPenSmallTargetMixin::number);
            if (component.isPresent()) max = Math.max(max, Math.abs(component.get()));
        }
        return Float.isFinite(max) && max > 0.0F ? Optional.of(max) : Optional.empty();
    }

    private static Optional<Float> positiveFiniteNumber(Object value) {
        return number(value).filter(result -> Float.isFinite(result) && result > 0.0F);
    }

    private static Optional<Float> number(Object value) {
        if (value instanceof Number number) return Optional.of(number.floatValue());
        try {
            return Optional.of(Float.parseFloat(String.valueOf(value)));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> invoke(Object target, String methodName) {
        if (target == null) return Optional.empty();
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> readField(Object target, String fieldName) {
        if (target == null) return Optional.empty();
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                java.lang.reflect.Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.ofNullable(field.get(target));
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return Optional.empty();
    }
}
