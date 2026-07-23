package com.akitaattribute.mobfarmblock.client;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.block.MobFarmBlockEntity;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Debug-only client scanner that follows Pixelmon ModelData into its modelType renderer. */
@EventBusSubscriber(modid = MobFarmBlockMod.MOD_ID, value = Dist.CLIENT)
public final class PixelmonModelTypeMetricsDumper {
    private static final Path LOG_FILE = Path.of("config", "mob_farm_block", "debug", "pixelmon_pen_render_metrics.jsonl").toAbsolutePath();
    private static final Set<String> LOGGED = new HashSet<>();
    private static int ticks;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) return;
        if (++ticks % 100 != 0) return;

        BlockPos center = minecraft.player.blockPosition();
        int radius = 16;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -4, -radius), center.offset(radius, 4, radius))) {
            try {
                if (!(minecraft.level.getBlockEntity(pos) instanceof MobFarmBlockEntity blockEntity)) continue;
                StoredMob stored = blockEntity.getStored();
                if (!PixelmonEntityRenderCache.isPixelmonStored(stored)) continue;
                Entity entity = PixelmonEntityRenderCache.getOrCreate(stored);
                if (entity == null) continue;
                log(blockEntity, stored, entity);
            } catch (Throwable error) {
                MobFarmBlockMod.LOGGER.warn("Skipping Pixelmon model type metrics for {} after diagnostic probe failed", pos, error);
            }
        }
    }

    private static void log(MobFarmBlockEntity blockEntity, StoredMob stored, Entity entity) {
        String dimension = blockEntity.getLevel() == null ? "unknown" : blockEntity.getLevel().dimension().location().toString();
        String species = stored.speciesId == null ? stored.mobId.toString() : stored.speciesId.toString();
        String key = "model-type-v2|" + dimension + "|" + blockEntity.getBlockPos().asLong() + "|" + species + "|" + MobFarmConfig.PIXELMON_RENDER_REPLAY_MODE.get();
        if (!LOGGED.add(key)) return;

        Object pokemon = invoke(entity, "getPokemon").or(() -> readField(entity, "pokemon")).orElse(null);
        Object delegate = readField(entity, "delegate").orElse(null);
        Object delegatePokemon = delegate == null ? null : invoke(delegate, "getPokemon").or(() -> readField(delegate, "pokemon")).orElse(null);
        Object effectivePokemon = pokemon != null ? pokemon : delegatePokemon;
        Object speciesObject = value(effectivePokemon, "getSpecies", "species");
        Object formObject = value(effectivePokemon, "getForm", "form");
        Object paletteObject = value(effectivePokemon, "getPalette", "palette");
        Object renderer = renderer(entity);
        Object modelData = firstValue(new Object[] { renderer, entity, effectivePokemon, speciesObject, formObject, paletteObject, delegate },
                "getModelData", "modelData", "getRenderData", "renderData", "getModel", "model", "getBaseModel", "baseModel");
        Object modelDataFirst = firstIterable(modelData);
        Object modelType = firstValue(new Object[] { modelDataFirst, modelData, renderer, entity, effectivePokemon },
                "getModelType", "modelType", "getRenderer", "renderer", "getSmdRenderer", "smdRenderer");
        Object modelTypeModel = firstValue(new Object[] { modelType, modelDataFirst, modelData, renderer },
                "getModel", "model", "getSmd", "smd", "getBmd", "bmd", "getValveStudioModel", "valveStudioModel", "getLoadedModel", "loadedModel", "getBounds", "bounds", "getDimensions", "dimensions");
        Object modelTypeModelFirst = firstIterable(modelTypeModel);
        Object modelTypeBounds = firstValue(new Object[] { modelTypeModel, modelTypeModelFirst, modelType, modelDataFirst },
                "getBounds", "bounds", "getBoundingBox", "boundingBox", "getDimensions", "dimensions", "getExtents", "extents", "getMesh", "mesh", "getMeshes", "meshes", "getParts", "parts");
        Object modelTypeBoundsFirst = firstIterable(modelTypeBounds);

        try {
            Files.createDirectories(LOG_FILE.getParent());
            StringBuilder out = new StringBuilder(20000);
            out.append('{');
            json(out, "source", "client_scan_model_type").append(',');
            json(out, "dimension", dimension).append(',');
            json(out, "pos", blockEntity.getBlockPos().getX() + "," + blockEntity.getBlockPos().getY() + "," + blockEntity.getBlockPos().getZ()).append(',');
            json(out, "species", species).append(',');
            json(out, "variant", stored.display == null ? "" : stored.display.variantKey()).append(',');
            json(out, "replayMode", MobFarmConfig.PIXELMON_RENDER_REPLAY_MODE.get().name()).append(',');
            json(out, "entityClass", className(entity)).append(',');
            json(out, "rendererClass", className(renderer)).append(',');
            json(out, "modelDataClass", className(modelData)).append(',');
            json(out, "modelDataFirstClass", className(modelDataFirst)).append(',');
            json(out, "modelTypeClass", className(modelType)).append(',');
            json(out, "modelTypeModelClass", className(modelTypeModel)).append(',');
            json(out, "modelTypeModelFirstClass", className(modelTypeModelFirst)).append(',');
            json(out, "modelTypeBoundsClass", className(modelTypeBounds)).append(',');
            json(out, "modelTypeBoundsFirstClass", className(modelTypeBoundsFirst)).append(',');
            json(out, "modelDataHints", inspect(modelData)).append(',');
            json(out, "modelDataFirstHints", inspect(modelDataFirst)).append(',');
            json(out, "modelTypeHints", inspect(modelType)).append(',');
            json(out, "modelTypeModelHints", inspect(modelTypeModel)).append(',');
            json(out, "modelTypeModelFirstHints", inspect(modelTypeModelFirst)).append(',');
            json(out, "modelTypeBoundsHints", inspect(modelTypeBounds)).append(',');
            json(out, "modelTypeBoundsFirstHints", inspect(modelTypeBoundsFirst)).append(',');
            json(out, "modelDataFirstMembers", members(modelDataFirst)).append(',');
            json(out, "modelTypeMembers", members(modelType)).append(',');
            json(out, "modelTypeModelMembers", members(modelTypeModel)).append(',');
            json(out, "modelTypeModelFirstMembers", members(modelTypeModelFirst)).append(',');
            json(out, "modelTypeBoundsMembers", members(modelTypeBounds)).append(',');
            json(out, "modelTypeBoundsFirstMembers", members(modelTypeBoundsFirst));
            out.append('}').append(System.lineSeparator());
            Files.writeString(LOG_FILE, out.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write Pixelmon model type metrics", error);
        }
    }

    private static Object renderer(Entity entity) {
        try {
            return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object firstValue(Object[] sources, String... names) {
        for (Object source : sources) {
            if (source == null) continue;
            for (String name : names) {
                Optional<Object> value = name.startsWith("get") ? invoke(source, name) : readField(source, name);
                if (value.isPresent()) return value.get();
            }
        }
        return null;
    }

    private static Object firstIterable(Object value) {
        if (value == null) return null;
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) return item;
            return null;
        }
        if (value.getClass().isArray() && java.lang.reflect.Array.getLength(value) > 0) return java.lang.reflect.Array.get(value, 0);
        return null;
    }

    private static Object value(Object target, String getter, String field) {
        if (target == null) return null;
        return invoke(target, getter).or(() -> readField(target, field)).orElse(null);
    }

    private static String inspect(Object target) {
        if (target == null) return "";
        List<String> parts = new ArrayList<>();
        inspectMethods(target, parts);
        inspectFields(target, parts);
        return String.join(";", parts);
    }

    private static void inspectMethods(Object target, List<String> parts) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (parts.size() >= 220) return;
                if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) continue;
                String name = method.getName();
                if (!interesting(name)) continue;
                try {
                    method.setAccessible(true);
                    append(parts, name, method.invoke(target));
                } catch (Throwable ignored) {
                    // Keep probing other members.
                }
            }
        }
    }

    private static void inspectFields(Object target, List<String> parts) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (parts.size() >= 220) return;
                if (Modifier.isStatic(field.getModifiers())) continue;
                String name = field.getName();
                if (!interesting(name)) continue;
                try {
                    field.setAccessible(true);
                    append(parts, name, field.get(target));
                } catch (Throwable ignored) {
                    // Keep probing other members.
                }
            }
        }
    }

    private static boolean interesting(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("dimension") || lower.contains("width") || lower.contains("height") || lower.contains("length")
                || lower.contains("depth") || lower.contains("scale") || lower.contains("size") || lower.contains("bounds")
                || lower.contains("model") || lower.contains("offset") || lower.contains("center") || lower.contains("radius")
                || lower.contains("mesh") || lower.contains("part") || lower.contains("bone") || lower.contains("vertex")
                || lower.contains("min") || lower.contains("max") || lower.equals("x") || lower.equals("y") || lower.equals("z");
    }

    private static String members(Object target) {
        if (target == null) return "";
        List<String> parts = new ArrayList<>();
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            parts.add("class=" + type.getName());
            for (Field field : type.getDeclaredFields()) {
                if (parts.size() >= 260) return String.join(";", parts);
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(target);
                    parts.add("field " + field.getType().getName() + " " + field.getName() + "=" + valueSummary(value));
                } catch (Throwable error) {
                    parts.add("field " + field.getType().getName() + " " + field.getName() + "=<" + error.getClass().getSimpleName() + ">");
                }
            }
            for (Method method : type.getDeclaredMethods()) {
                if (parts.size() >= 260) return String.join(";", parts);
                parts.add("method " + method.getReturnType().getName() + " " + method.getName() + "(" + parameterTypes(method) + ")");
            }
        }
        return String.join(";", parts);
    }

    private static String parameterTypes(Method method) {
        Class<?>[] types = method.getParameterTypes();
        if (types.length == 0) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) out.append(',');
            out.append(types[i].getName());
        }
        return out.toString();
    }

    private static String valueSummary(Object value) {
        if (value == null) return "null";
        String text = value.getClass().getName() + ":" + String.valueOf(value);
        return trim(text, 220);
    }

    private static void append(List<String> parts, String name, Object value) {
        if (value == null) return;
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence || value.getClass().isEnum()) {
            parts.add(name + "=" + value);
            return;
        }
        Object first = firstIterable(value);
        String text = trim(String.valueOf(value), 180);
        String suffix = first == null ? "" : ";" + name + "FirstClass=" + first.getClass().getName() + ":" + trim(String.valueOf(first), 120);
        parts.add(name + "=" + value.getClass().getName() + ":" + text + suffix);
    }

    private static String trim(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private static Optional<Object> invoke(Object target, String methodName) {
        if (target == null) return Optional.empty();
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Method method = type.getDeclaredMethod(methodName);
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(target));
            } catch (Throwable ignored) {
                // Try the next superclass.
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> readField(Object target, String fieldName) {
        if (target == null) return Optional.empty();
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                return Optional.ofNullable(field.get(target));
            } catch (Throwable ignored) {
                // Try the next superclass.
            }
        }
        return Optional.empty();
    }

    private static String className(Object object) {
        return object == null ? "" : object.getClass().getName();
    }

    private static StringBuilder json(StringBuilder out, String name, String value) {
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

    private PixelmonModelTypeMetricsDumper() {}
}
