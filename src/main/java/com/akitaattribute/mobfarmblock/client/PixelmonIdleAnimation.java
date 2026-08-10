package com.akitaattribute.mobfarmblock.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public final class PixelmonIdleAnimation {
    private static final Map<String, Optional<Method>> METHOD_CACHE = new HashMap<>();
    private static final Map<String, Optional<Field>> FIELD_CACHE = new HashMap<>();
    private static final Map<Entity, Long> LAST_ANIMATION_TICK = new WeakHashMap<>();

    private PixelmonIdleAnimation() {}

    public static boolean isPixelmonEntity(Entity entity) {
        return entity != null && entity.getClass().getName().startsWith("com.pixelmonmod.pixelmon.");
    }

    public static void applyPixelmonIdleClock(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || entity == null) return;

        long gameTime = minecraft.level.getGameTime();
        int tick = (int) (gameTime & 0x3FFFFFFFL);
        entity.tickCount = tick;
        setInt(entity, "tickCount", tick);
        setInt(entity, "ticksExisted", tick);

        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
            living.yRotO = living.getYRot();
            living.xRotO = living.getXRot();
        }

        Long lastAnimatedTick = LAST_ANIMATION_TICK.get(entity);
        if (lastAnimatedTick != null && lastAnimatedTick == gameTime) return;
        LAST_ANIMATION_TICK.put(entity, gameTime);
        advancePixelmonIdleAnimation(entity);
    }

    private static void advancePixelmonIdleAnimation(Entity entity) {
        invoke(entity, "setAnimated", true);
        invokeNoArg(entity, "setAnimated");
        setBoolean(entity, "animated", true);
        invokeNoArg(entity, "initAnimation");
        invokeNoArg(entity, "checkAnimation");
        invokeNoArg(entity, "handleAnimation");
        invokeNoArg(entity, "animationTime");
        invokeNoArg(entity, "tickAnimation");
        invokeNoArg(entity, "tickEvolveAnimation");
    }

    private static void setInt(Object target, String fieldName, int value) {
        Optional<Field> field = field(target, fieldName);
        if (field.isEmpty()) return;
        try {
            if (field.get().getType() == int.class) field.get().setInt(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static boolean setBoolean(Object target, String fieldName, boolean value) {
        Optional<Field> field = field(target, fieldName);
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

    private static Optional<Object> invokeNoArg(Object target, String methodName) {
        return invoke(target, methodName);
    }

    private static Optional<Object> invoke(Object target, String methodName, Object... args) {
        if (target == null) return Optional.empty();
        Optional<Method> method = method(target.getClass(), methodName, args);
        if (method.isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable(method.get().invoke(target, args));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Method> method(Class<?> startType, String methodName, Object... args) {
        String key = methodKey(startType, methodName, args);
        Optional<Method> cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> type = startType;
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != args.length) continue;
                if (!parametersMatch(method.getParameterTypes(), args)) continue;
                try {
                    method.setAccessible(true);
                    Optional<Method> found = Optional.of(method);
                    METHOD_CACHE.put(key, found);
                    return found;
                } catch (Throwable ignored) {
                    Optional<Method> missing = Optional.empty();
                    METHOD_CACHE.put(key, missing);
                    return missing;
                }
            }
            type = type.getSuperclass();
        }
        Optional<Method> missing = Optional.empty();
        METHOD_CACHE.put(key, missing);
        return missing;
    }

    private static String methodKey(Class<?> type, String methodName, Object... args) {
        StringBuilder key = new StringBuilder(type.getName()).append('#').append(methodName).append('(');
        for (Object arg : args) key.append(arg == null ? "null" : arg.getClass().getName()).append(',');
        return key.append(')').toString();
    }

    private static boolean parametersMatch(Class<?>[] types, Object[] args) {
        for (int i = 0; i < types.length; i++) {
            if (args[i] == null) continue;
            Class<?> type = types[i].isPrimitive() ? boxed(types[i]) : types[i];
            if (!type.isInstance(args[i])) return false;
        }
        return true;
    }

    private static Class<?> boxed(Class<?> type) {
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

    private static Optional<Field> field(Object target, String fieldName) {
        if (target == null) return Optional.empty();
        String key = target.getClass().getName() + '#' + fieldName;
        Optional<Field> cached = FIELD_CACHE.get(key);
        if (cached != null) return cached;

        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                Optional<Field> found = Optional.of(field);
                FIELD_CACHE.put(key, found);
                return found;
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        Optional<Field> missing = Optional.empty();
        FIELD_CACHE.put(key, missing);
        return missing;
    }
}
