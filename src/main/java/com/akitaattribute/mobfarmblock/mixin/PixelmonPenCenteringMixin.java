package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.world.entity.Entity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Uses Pixelmon model metadata as an elongated-render hint for pen centering. */
@Mixin(targets = "com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer", remap = false)
public abstract class PixelmonPenCenteringMixin {
    @Redirect(
            method = "pixelmonPenCentering",
            at = @At(value = "INVOKE", target = "Lcom/akitaattribute/mobfarmblock/client/MobFarmBlockEntityRenderer;renderWidth(Lnet/minecraft/world/entity/Entity;Lcom/akitaattribute/mobfarmblock/mob/StoredMob;)F")
    )
    private static float mobfarmblock$pixelmonModelScaleWidth(Entity entity, StoredMob stored) {
        float baseWidth = renderWidthFallback(entity, stored);
        if (baseWidth < 2.0F) return baseWidth;
        float modelScale = pixelmonModelScale(entity).orElse(0.0F);
        if (modelScale <= baseWidth * 1.25F) return baseWidth;
        return modelScale;
    }

    private static float renderWidthFallback(Entity entity, StoredMob stored) {
        float width = Math.max(entity.getBbWidth(), 0.35F);
        PixelmonRenderSnapshot snapshot = stored == null ? null : stored.pixelmonRenderSnapshot;
        if (snapshot != null && snapshot.capturedWidth() > 0.05F) width = Math.max(width, snapshot.capturedWidth());
        return width;
    }

    private static Optional<Float> pixelmonModelScale(Entity entity) {
        Object modelData = invoke(entity, "getModel").or(() -> readField(entity, "model")).orElse(null);
        Object first = firstModelData(modelData).orElse(null);
        if (first == null) return Optional.empty();
        Object scale = invoke(first, "scale").or(() -> readField(first, "scale")).orElse(null);
        return vectorMax(scale);
    }

    private static Optional<Object> firstModelData(Object value) {
        if (value == null) return Optional.empty();
        if (value instanceof Collection<?> collection) return collection.stream().findFirst().map(Object.class::cast);
        if (value.getClass().isArray() && java.lang.reflect.Array.getLength(value) > 0) return Optional.ofNullable(java.lang.reflect.Array.get(value, 0));
        return Optional.of(value);
    }

    private static Optional<Float> vectorMax(Object value) {
        if (value instanceof Vector3f vector) {
            float max = Math.max(Math.abs(vector.x()), Math.max(Math.abs(vector.y()), Math.abs(vector.z())));
            return Float.isFinite(max) && max > 0.0F ? Optional.of(max) : Optional.empty();
        }
        if (value instanceof Number number) {
            float raw = number.floatValue();
            return Float.isFinite(raw) && raw > 0.0F ? Optional.of(raw) : Optional.empty();
        }
        if (value == null) return Optional.empty();
        String text = String.valueOf(value);
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?(?:E[+-]?\\d+)?", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        float max = 0.0F;
        while (matcher.find()) {
            try {
                max = Math.max(max, Math.abs(Float.parseFloat(matcher.group())));
            } catch (Throwable ignored) {
            }
        }
        return max > 0.0F ? Optional.of(max) : Optional.empty();
    }

    private static Optional<Object> invoke(Object target, String name) {
        if (target == null) return Optional.empty();
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(target));
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> readField(Object target, String name) {
        if (target == null) return Optional.empty();
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return Optional.ofNullable(field.get(target));
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return Optional.empty();
    }
}
