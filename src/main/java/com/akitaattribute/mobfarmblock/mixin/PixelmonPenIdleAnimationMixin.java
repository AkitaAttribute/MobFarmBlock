package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.client.ClientEntityRenderCache;
import com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Lets in-world Pixelmon pen renders advance their renderer animation clock without
 * allowing the cached display entity to move or think like a normal world entity.
 */
@Mixin(value = MobFarmBlockEntityRenderer.class, remap = false)
public abstract class PixelmonPenIdleAnimationMixin {
    private static final Path mobFarmBlock$PIXELMON_IDLE_LOG = Path.of("config", "mob_farm_block", "debug", "pixelmon_pen_idle_animation.jsonl").toAbsolutePath();
    private static final Map<String, Long> mobFarmBlock$LAST_IDLE_LOG_TICK = new HashMap<>();

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lcom/akitaattribute/mobfarmblock/client/ClientEntityRenderCache;freezeForRender(Lnet/minecraft/world/entity/Entity;)V"
        )
    )
    private void mobFarmBlock$animatePixelmonPenEntity(Entity entity) {
        if (!mobFarmBlock$isPixelmonEntity(entity)) {
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
        if (minecraft.level == null) {
            mobFarmBlock$logIdleState(entity, 0L, 0, 0, "no_level");
            return;
        }

        long gameTime = minecraft.level.getGameTime();
        int beforeTick = entity.tickCount;
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

        mobFarmBlock$logIdleState(entity, gameTime, beforeTick, tick, "applied_clock");
    }

    private static void mobFarmBlock$logIdleState(Entity entity, long gameTime, int beforeTick, int afterTick, String stage) {
        if (entity == null) return;
        String key = entity.getClass().getName() + "|" + mobFarmBlock$species(entity).orElse("");
        long last = mobFarmBlock$LAST_IDLE_LOG_TICK.getOrDefault(key, Long.MIN_VALUE);
        if (gameTime != 0L && last != Long.MIN_VALUE && gameTime - last < 40L) return;
        mobFarmBlock$LAST_IDLE_LOG_TICK.put(key, gameTime);

        try {
            Files.createDirectories(mobFarmBlock$PIXELMON_IDLE_LOG.getParent());
            StringBuilder out = new StringBuilder(1024);
            out.append('{');
            mobFarmBlock$json(out, "stage", stage).append(',');
            mobFarmBlock$num(out, "gameTime", gameTime).append(',');
            mobFarmBlock$json(out, "entityClass", entity.getClass().getName()).append(',');
            mobFarmBlock$json(out, "species", mobFarmBlock$species(entity).orElse("")).append(',');
            mobFarmBlock$num(out, "tickBefore", beforeTick).append(',');
            mobFarmBlock$num(out, "tickAfter", afterTick).append(',');
            mobFarmBlock$num(out, "entityTickCount", entity.tickCount).append(',');
            mobFarmBlock$json(out, "ticksExisted", mobFarmBlock$fieldOrMethod(entity, "ticksExisted").orElse("")).append(',');
            mobFarmBlock$json(out, "animationFields", mobFarmBlock$memberNames(entity, "anim")).append(',');
            mobFarmBlock$json(out, "animationMethods", mobFarmBlock$methodNames(entity, "anim")).append(',');
            mobFarmBlock$json(out, "stateFields", mobFarmBlock$memberNames(entity, "state")).append(',');
            mobFarmBlock$json(out, "tickMethods", mobFarmBlock$methodNames(entity, "tick")).append(',');
            mobFarmBlock$json(out, "pokemonHints", mobFarmBlock$pokemonHints(entity));
            out.append('}').append(System.lineSeparator());
            Files.writeString(mobFarmBlock$PIXELMON_IDLE_LOG, out.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Throwable error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write Pixelmon pen idle animation metrics", error);
        }
    }

    private static Optional<String> mobFarmBlock$species(Entity entity) {
        Object pokemon = mobFarmBlock$invoke(entity, "getPokemon").orElse(null);
        Optional<Object> species = mobFarmBlock$invoke(pokemon, "getSpecies");
        if (species.isPresent()) return Optional.of(String.valueOf(species.get()));
        return mobFarmBlock$invoke(entity, "getSpecies").map(String::valueOf);
    }

    private static String mobFarmBlock$pokemonHints(Entity entity) {
        Object pokemon = mobFarmBlock$invoke(entity, "getPokemon").orElse(null);
        StringBuilder out = new StringBuilder();
        mobFarmBlock$appendHint(out, "pokemonClass", pokemon == null ? "" : pokemon.getClass().getName());
        mobFarmBlock$appendHint(out, "pokemonAnimationFields", mobFarmBlock$memberNames(pokemon, "anim"));
        mobFarmBlock$appendHint(out, "pokemonAnimationMethods", mobFarmBlock$methodNames(pokemon, "anim"));
        mobFarmBlock$appendHint(out, "pokemonStateFields", mobFarmBlock$memberNames(pokemon, "state"));
        return out.toString();
    }

    private static String mobFarmBlock$memberNames(Object target, String contains) {
        if (target == null) return "";
        String lower = contains.toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder();
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Field field : type.getDeclaredFields()) {
                String name = field.getName();
                if (name.toLowerCase(java.util.Locale.ROOT).contains(lower)) mobFarmBlock$appendHint(out, name, type.getSimpleName());
            }
            type = type.getSuperclass();
        }
        return out.toString();
    }

    private static String mobFarmBlock$methodNames(Object target, String contains) {
        if (target == null) return "";
        String lower = contains.toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder();
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            for (Method method : type.getDeclaredMethods()) {
                String name = method.getName();
                if (name.toLowerCase(java.util.Locale.ROOT).contains(lower)) mobFarmBlock$appendHint(out, name + "()", type.getSimpleName());
            }
            type = type.getSuperclass();
        }
        return out.toString();
    }

    private static Optional<String> mobFarmBlock$fieldOrMethod(Object target, String name) {
        Optional<Object> method = mobFarmBlock$invoke(target, name);
        if (method.isPresent()) return method.map(String::valueOf);
        return mobFarmBlock$readField(target, name).map(String::valueOf);
    }

    private static void mobFarmBlock$appendHint(StringBuilder out, String name, String value) {
        if (out.length() > 0) out.append(';');
        out.append(name).append('=').append(value == null ? "" : value);
    }

    private static void mobFarmBlock$setInt(Object target, String fieldName, int value) {
        Optional<Field> field = mobFarmBlock$field(target, fieldName);
        if (field.isEmpty()) return;
        try {
            if (field.get().getType() == int.class) field.get().setInt(target, value);
        } catch (Throwable ignored) {
        }
    }

    private static Optional<Object> mobFarmBlock$invoke(Object target, String methodName) {
        if (target == null) return Optional.empty();
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> mobFarmBlock$readField(Object target, String fieldName) {
        Optional<Field> field = mobFarmBlock$field(target, fieldName);
        if (field.isEmpty()) return Optional.empty();
        try {
            return Optional.ofNullable(field.get().get(target));
        } catch (Throwable ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Field> mobFarmBlock$field(Object target, String fieldName) {
        if (target == null) return Optional.empty();
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.of(field);
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
        return Optional.empty();
    }

    private static StringBuilder mobFarmBlock$json(StringBuilder out, String name, String value) {
        out.append('"').append(name).append("\":\"");
        String safe = value == null ? "" : value;
        for (int i = 0; i < safe.length(); i++) {
            char c = safe.charAt(i);
            if (c == '\\' || c == '"') out.append('\\');
            if (c == '\n') out.append("\\n");
            else if (c == '\r') out.append("\\r");
            else out.append(c);
        }
        return out.append('"');
    }

    private static StringBuilder mobFarmBlock$num(StringBuilder out, String name, long value) {
        return out.append('"').append(name).append("\":").append(value);
    }
}
