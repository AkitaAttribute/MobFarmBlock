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
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Debug-only client scanner that appends deeper Pixelmon model/render metadata for loaded Mob Farm pens.
 *
 * <p>The placed renderer already logs the values it directly uses. This companion log probes Pixelmon's
 * entity, Pokemon, delegate, species/form/palette/model objects for likely base model dimensions and scale
 * fields without changing render behavior.</p>
 */
@EventBusSubscriber(modid = MobFarmBlockMod.MOD_ID, value = Dist.CLIENT)
public final class PixelmonPenRenderMetricsDumper {
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
                MobFarmBlockMod.LOGGER.warn("Skipping Pixelmon pen render metrics for {} after diagnostic probe failed", pos, error);
            }
        }
    }

    private static void log(MobFarmBlockEntity blockEntity, StoredMob stored, Entity entity) {
        String dimension = blockEntity.getLevel() == null ? "unknown" : blockEntity.getLevel().dimension().location().toString();
        String species = stored.speciesId == null ? stored.mobId.toString() : stored.speciesId.toString();
        String key = "extended|" + dimension + "|" + blockEntity.getBlockPos().asLong() + "|" + species + "|" + MobFarmConfig.PIXELMON_RENDER_REPLAY_MODE.get();
        if (!LOGGED.add(key)) return;

        Object pokemon = invoke(entity, "getPokemon").or(() -> readField(entity, "pokemon")).orElse(null);
        Object delegate = readField(entity, "delegate").orElse(null);
        Object delegatePokemon = delegate == null ? null : invoke(delegate, "getPokemon").or(() -> readField(delegate, "pokemon")).orElse(null);
        Object effectivePokemon = pokemon != null ? pokemon : delegatePokemon;
        Object speciesObject = value(effectivePokemon, "getSpecies", "species");
        Object formObject = value(effectivePokemon, "getForm", "form");
        Object paletteObject = value(effectivePokemon, "getPalette", "palette");
        Object renderer = renderer(entity);
        Object modelData = firstValue(renderer, entity, effectivePokemon, speciesObject, formObject, paletteObject, delegate, List.of(
                "getModelData", "modelData", "getRenderData", "renderData", "getModel", "model", "getBaseModel", "baseModel",
                "getBakedModel", "bakedModel", "getDimensions", "dimensions", "getBounds", "bounds"
        ));

        PixelmonRenderSnapshot snapshot = stored.pixelmonRenderSnapshot;
        try {
            Files.createDirectories(LOG_FILE.getParent());
            StringBuilder out = new StringBuilder(4096);
            out.append('{');
            json(out, "source", "client_scan_extended").append(',');
            json(out, "dimension", dimension).append(',');
            json(out, "pos", blockEntity.getBlockPos().getX() + "," + blockEntity.getBlockPos().getY() + "," + blockEntity.getBlockPos().getZ()).append(',');
            json(out, "species", species).append(',');
            json(out, "variant", stored.display == null ? "" : stored.display.variantKey()).append(',');
            json(out, "replayMode", MobFarmConfig.PIXELMON_RENDER_REPLAY_MODE.get().name()).append(',');
            json(out, "entityClass", className(entity)).append(',');
            json(out, "pokemonClass", className(effectivePokemon)).append(',');
            json(out, "delegateClass", className(delegate)).append(',');
            json(out, "rendererClass", className(renderer)).append(',');
            json(out, "speciesObjectClass", className(speciesObject)).append(',');
            json(out, "formObjectClass", className(formObject)).append(',');
            json(out, "paletteObjectClass", className(paletteObject)).append(',');
            json(out, "modelDataClass", className(modelData)).append(',');
            num(out, "entityBbWidth", entity.getBbWidth()).append(',');
            num(out, "entityBbHeight", entity.getBbHeight()).append(',');
            num(out, "capturedWidth", snapshot == null ? 0.0F : snapshot.capturedWidth()).append(',');
            num(out, "capturedHeight", snapshot == null ? 0.0F : snapshot.capturedHeight()).append(',');
            num(out, "sizeCentimeters", snapshot == null ? 0.0F : snapshot.sizeCentimeters()).append(',');
            json(out, "entityDimensionHints", inspect(entity)).append(',');
            json(out, "pokemonDimensionHints", inspect(effectivePokemon)).append(',');
            json(out, "delegateDimensionHints", inspect(delegate)).append(',');
            json(out, "speciesDimensionHints", inspect(speciesObject)).append(',');
            json(out, "formDimensionHints", inspect(formObject)).append(',');
            json(out, "paletteDimensionHints", inspect(paletteObject)).append(',');
            json(out, "rendererDimensionHints", inspect(renderer)).append(',');
            json(out, "modelDimensionHints", inspect(modelData));
            out.append('}').append(System.lineSeparator());
            Files.writeString(LOG_FILE, out.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write extended Pixelmon pen render metrics", error);
        }
    }

    private static Object renderer(Entity entity) {
        try {
            return Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object firstValue(Object a, Object b, Object c, Object d, Object e, Object f, Object g, List<String> names) {
        Object[] sources = new Object[] { a, b, c, d, e, f, g };
        for (Object source : sources) {
            if (source == null) continue;
            for (String name : names) {
                Optional<Object> value = name.startsWith("get") ? invoke(source, name) : readField(source, name);
                if (value.isPresent()) return value.get();
            }
        }
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
                if (parts.size() >= 120) return;
                if (method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) continue;
                String name = method.getName();
                if (!interesting(name)) continue;
                try {
                    method.setAccessible(true);
                    Object value = method.invoke(target);
                    append(parts, name, value);
                } catch (Throwable ignored) {
                    // Keep probing other members.
                }
            }
        }
    }

    private static void inspectFields(Object target, List<String> parts) {
        for (Class<?> type = target.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (parts.size() >= 120) return;
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
                || lower.contains("model") || lower.contains("offset") || lower.contains("center") || lower.contains("radius");
    }

    private static void append(List<String> parts, String name, Object value) {
        if (value == null) return;
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence || value.getClass().isEnum()) {
            parts.add(name + "=" + value);
            return;
        }
        String text = String.valueOf(value);
        if (text.length() > 160) text = text.substring(0, 160);
        parts.add(name + "=" + value.getClass().getName() + ":" + text);
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

    private static StringBuilder num(StringBuilder out, String name, double value) {
        return out.append('"').append(name).append("\":").append(Double.isFinite(value) ? value : 0.0D);
    }

    private PixelmonPenRenderMetricsDumper() {}
}
