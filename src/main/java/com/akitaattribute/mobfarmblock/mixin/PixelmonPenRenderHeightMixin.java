package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer;
import com.akitaattribute.mobfarmblock.client.PixelmonEntityRenderCache;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.world.entity.Entity;

@Mixin(value = MobFarmBlockEntityRenderer.class, remap = false)
public abstract class PixelmonPenRenderHeightMixin {
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final float MIN_PIXELMON_SMALL_VISUAL_HEIGHT = 0.90F;

    @ModifyVariable(method = "renderHeight", at = @At("STORE"), ordinal = 0)
    private static float mobFarmBlock$includePixelmonVisualHeight(float height, Entity entity, StoredMob stored) {
        if (!PixelmonEntityRenderCache.isPixelmonStored(stored)) return height;

        float visualHeight = Math.max(height, pixelmonSizeMeters(stored).orElse(0.0F));
        visualHeight = Math.max(visualHeight, MIN_PIXELMON_SMALL_VISUAL_HEIGHT);

        Optional<Float> modelScale = pixelmonModelScale(entity);
        if (modelScale.isPresent()) {
            float scale = modelScale.get();
            if (scale >= 3.0F) {
                // Some Pixelmon models use tiny entity/form dimensions and a huge ModelData scale.
                // Treat the high model scale as a hidden visual-height hint, but damp it heavily so
                // small Pokemon are shrunk enough to clear the Look UI without becoming microscopic.
                visualHeight = Math.max(visualHeight, scale * 0.20F);
            }
        }

        return Math.max(height, visualHeight);
    }

    private static Optional<Float> pixelmonSizeMeters(StoredMob stored) {
        if (stored == null) return Optional.empty();
        PixelmonRenderSnapshot snapshot = stored.pixelmonRenderSnapshot;
        if (snapshot != null && snapshot.sizeCentimeters() > 0.0F) return Optional.of(snapshot.sizeCentimeters() / 100.0F);
        String variant = stored.display == null ? "" : stored.display.variantKey();
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
            Optional<Float> component = invoke(value, method).flatMap(PixelmonPenRenderHeightMixin::number);
            if (component.isPresent()) max = Math.max(max, Math.abs(component.get()));
        }
        return Float.isFinite(max) && max > 0.0F ? Optional.of(max) : Optional.empty();
    }

    private static Optional<Float> number(Object value) {
        if (value instanceof Number number) return Optional.of(number.floatValue());
        try { return Optional.of(Float.parseFloat(String.valueOf(value))); }
        catch (Throwable ignored) { return Optional.empty(); }
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
