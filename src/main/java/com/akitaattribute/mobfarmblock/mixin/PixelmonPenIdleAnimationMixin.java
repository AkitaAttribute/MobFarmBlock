package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.akitaattribute.mobfarmblock.client.ClientEntityRenderCache;
import com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Lets in-world Pixelmon pen renders advance their renderer animation clock without
 * allowing the cached display entity to move or think like a normal world entity.
 */
@Mixin(value = MobFarmBlockEntityRenderer.class, remap = false)
public abstract class PixelmonPenIdleAnimationMixin {
    private static final Map<String, Optional<Method>> mobFarmBlock$METHOD_CACHE = new HashMap<>();
    private static final Map<String, Optional<Field>> mobFarmBlock$FIELD_CACHE = new HashMap<>();
    private static final Map<Entity, Long> mobFarmBlock$LAST_ANIMATION_TICK = new WeakHashMap<>();

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lcom/akitaattribute/mobfarmblock/client/ClientEntityRenderCache;freezeForRender(Lnet/minecraft/world/entity/Entity;)V"
        )
    )
    private void mobFarmBlock$animatePixelmonPenEntity(Entity entity) {
        if (!mobFarmBlock$isPixelmonEntity(entity) || !MobFarmConfig.PIXELMON_PEN_IDLE_ANIMATION.get()) {
            ClientEntityRenderCache.freezeForRender(entity);
            return;
        }
        mobFarmBlock$applyPixelmonIdleClock(entity);
    }

    private static boolean mobFarmBlock$isPixelmonEntity(Entity entity) {
        return entity != null && entity.getClass().getName().startsWith("com.pixelmonmod.pixelmon.");
    }

    private static void mobFarmBlock$applyPixelmonIdleClock(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        long gameTime = minecraft.level.getGameTime();
        int tick = (int) (gameTime & 0x3FFFFFFFL);
        entity.tickCount = tick;
        mobFarmBlock$setInt(entity, "tickCount", tick);
        mobFarmBlock$setInt(entity, "ticksExisted", tick);

        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
            living.yRotO = living.getYRot();
            living.xRotO = living.getXRot();
        }

        Long lastAnimatedTick = mobFarmBlock$LAST_ANIMATION_TICK.get(entity);
        if (lastAnimatedTick != null && lastAnimatedTick == gameTime) return;
        mobFarmBlock$LAST_ANIMATION_TICK.put(entity, gameTime);
        mobFarmBlock$advancePixelmonIdleAnimation(entity);
    }

    private static void mobFarmBlock$advancePixelmonIdleAnimation(Entity entity) {
        mobFarmBlock$invoke(entity, "setAnimated", true);
        mobFarmBlock$invokeNoArg(entity, "setAnimated");
        mobFarmBlock$setBoolean(entity, "animated", true);
        mobFarmBlock$invokeNoArg(entity, "initAnimation");
        mobFarmBlock$invokeNoArg(entity, "checkAnimation");
        mobFarmBlock$invokeNoArg(entity, "handleAnimation");
        mobFarmBlock$invokeNoArg(entity, "animationTime");
        mobFarmBlock$invokeNoArg(entity, "tickAnimation");
        mobFarmBlock$invokeNoArg(entity, "tickEvolveAnimation");
    }

    private static void mobFarmBlock$setInt(Object target, String fieldName, int value) {
        Optional<Field> field = mobFarmBlock$field(target, fieldName);
        if (field.isEmpty()) return;
        try {
            if (field.get().getType() == int.class) field.get().setInt(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static boolean mobFarmBlock$setBoolean(Object target, String fieldName, boolean value) {
        Optional<Field> field = mobFarmBlock$field(target, fieldName);
        if (field.isEmpty()) return false;
        try {
            if (field.get().getType() == boolean.class) {
                field.get().setBoolean(target, value);
                return true;
            }
            if (field.get().getType() == Boolean.class) {
                field.get().set(target, value);
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static Optional<Object> mobFarmBlock$invokeNoArg(Object target, String methodName) {
        return mobFarmBlock$invoke(target, methodName);
    }

    private static Optional<Object> mobFarmBlock$invoke(Object target, String methodName, Object... args) {
        if (target == null) return Optional.empty();
        Optional<Method> method = mobFarmBlock$method(target.getClass(), methodName, args);
        if (method.isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable(method.get().invoke(target, args));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Method> mobFarmBlock$method(Class<?> startType, String methodName, Object... args) {
        String key = mobFarmBlock$methodKey(startType, methodName, args);
        Optional<Method> cached = mobFarmBlock$METHOD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> type = startType;
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) continue;
                if (!mobFarmBlock$parametersMatch(method.getParameterTypes(), args)) continue;
                try {
                    method.setAccessible(true);
                    Optional<Method> found = Optional.of(method);
                    mobFarmBlock$METHOD_CACHE.put(key, found);
                    return found;
                } catch (Throwable ignored) {
                    Optional<Method> missing = Optional.empty();
                    mobFarmBlock$METHOD_CACHE.put(key, missing);
                    return missing;
                }
            }
            type = type.getSuperclass();
        }
        Optional<Method> missing = Optional.empty();
        mobFarmBlock$METHOD_CACHE.put(key, missing);
        return missing;
    }

    private static String mobFarmBlock$methodKey(Class<?> type, String methodName, Object... args) {
        StringBuilder key = new StringBuilder(type.getName()).append('#').append(methodName).append('(');
        for (Object arg : args) key.append(arg == null ? "null" : arg.getClass().getName()).append(',');
        return key.append(')').toString();
    }

    private static boolean mobFarmBlock$parametersMatch(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) continue;
            Class<?> type = types[i].isPrimitive() ? mobFarmBlock$boxed(types[i]) : types[i];
            if (!type.isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> mobFarmBlock$boxed(Class<?> type) {
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Optional<Field> mobFarmBlock$field(Object target, String fieldName) {
        if (target == null) return Optional.empty();
        String key = target.getClass().getName() + '#' + fieldName;
        Optional<Field> cached = mobFarmBlock$FIELD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Optional<Field> found = Optional.of(field);
                mobFarmBlock$FIELD_CACHE.put(key, found);
                return found;
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        Optional<Field> missing = Optional.empty();
        mobFarmBlock$FIELD_CACHE.put(key, missing);
        return missing;
    }
}
