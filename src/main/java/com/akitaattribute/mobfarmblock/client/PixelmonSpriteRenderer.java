package com.akitaattribute.mobfarmblock.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akitaattribute.mobfarmblock.mob.MobKind;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OverlayTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/** Renders Pixelmon using its captured/registry sprite instead of an unsafe dummy entity. */
public final class PixelmonSpriteRenderer {
    private static final String GENERIC_PIXELMON_TEXTURE = "pixelmon:textures/entity/pixelmon.png";
    private static final Pattern SPRITE_PATTERN = Pattern.compile("\\\"sprite\\\"\\s*:\\s*\\\"(pixelmon:[^\\\"]*sprite\\.png)\\\"");
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    public static boolean isRenderable(StoredMob stored) {
        return spriteTexture(stored).isPresent();
    }

    public static boolean renderGuiSprite(StoredMob stored, PoseStack poseStack, MultiBufferSource buffer, double x, double y, double z, float size) {
        Optional<ResourceLocation> texture = spriteTexture(stored);
        if (texture.isEmpty()) return false;
        poseStack.pushPose();
        try {
            poseStack.translate(x, y, z);
            poseStack.scale(size, -size, size);
            renderQuad(texture.get(), poseStack, buffer, 1.0F);
            return true;
        } catch (Throwable error) {
            warnOnce("renderGuiSprite:" + texture.get(), error);
            return false;
        } finally {
            poseStack.popPose();
        }
    }

    public static boolean renderWorldBillboard(StoredMob stored, Minecraft minecraft, PoseStack poseStack, MultiBufferSource buffer, double x, double y, double z, float size) {
        Optional<ResourceLocation> texture = spriteTexture(stored);
        if (texture.isEmpty()) return false;
        poseStack.pushPose();
        try {
            poseStack.translate(x, y, z);
            poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
            poseStack.scale(size, -size, size);
            renderQuad(texture.get(), poseStack, buffer, 1.0F);
            return true;
        } catch (Throwable error) {
            warnOnce("renderWorldBillboard:" + texture.get(), error);
            return false;
        } finally {
            poseStack.popPose();
        }
    }

    private static void renderQuad(ResourceLocation texture, PoseStack poseStack, MultiBufferSource buffer, float size) {
        float half = size / 2.0F;
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
        vertex(consumer, matrix, -half, -half, 0.0F, 0.0F, 1.0F);
        vertex(consumer, matrix, half, -half, 0.0F, 1.0F, 1.0F);
        vertex(consumer, matrix, half, half, 0.0F, 1.0F, 0.0F);
        vertex(consumer, matrix, -half, half, 0.0F, 0.0F, 0.0F);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float u, float v) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0F, 0.0F, 1.0F);
    }

    private static Optional<ResourceLocation> spriteTexture(StoredMob stored) {
        if (stored == null || stored.kind != MobKind.PIXELMON) return Optional.empty();
        ResourceLocation captured = stored.display.textureId();
        if (captured != null && !GENERIC_PIXELMON_TEXTURE.equals(captured.toString())) return Optional.of(normalizeSpriteLocation(captured));
        return resolveSpriteFromSpecies(stored.speciesId, stored.display.variantKey());
    }

    private static Optional<ResourceLocation> resolveSpriteFromSpecies(ResourceLocation speciesId, String variantKey) {
        if (speciesId == null) return Optional.empty();
        Optional<Object> species = findPixelmonSpeciesObject(speciesId);
        Optional<String> json = species.flatMap(value -> invoke(value, "getJson").map(String::valueOf));
        if (json.isEmpty()) {
            Optional<Object> form = species.flatMap(value -> invoke(value, "getDefaultForm").or(() -> invoke(value, "getFirstForm")));
            json = form.flatMap(value -> invoke(value, "getJson").map(String::valueOf));
        }
        return json.flatMap(text -> spriteFromJson(text, variantKey));
    }

    private static Optional<ResourceLocation> spriteFromJson(String json, String variantKey) {
        if (json == null || json.isBlank()) return Optional.empty();
        String form = variantValue(variantKey, "form").orElse("base");
        String palette = variantValue(variantKey, "palette").orElse("none");
        ResourceLocation first = null;
        Matcher matcher = SPRITE_PATTERN.matcher(json);
        while (matcher.find()) {
            ResourceLocation texture = normalizeSpriteLocation(matcher.group(1));
            if (first == null) first = texture;
            String path = texture.getPath();
            if (path.contains("/" + form + "/" + palette + "/")) return Optional.of(texture);
            if (path.contains("/" + form + "/none/")) first = texture;
        }
        return Optional.ofNullable(first);
    }

    private static ResourceLocation normalizeSpriteLocation(String raw) {
        return normalizeSpriteLocation(ResourceLocation.parse(raw));
    }

    private static ResourceLocation normalizeSpriteLocation(ResourceLocation id) {
        String path = id.getPath();
        if (!path.startsWith("textures/")) path = "textures/" + path;
        return ResourceLocation.fromNamespaceAndPath(id.getNamespace(), path);
    }

    private static Optional<String> variantValue(String variantKey, String key) {
        if (variantKey == null || variantKey.isBlank()) return Optional.empty();
        for (String part : variantKey.split("\\|")) {
            int equals = part.indexOf('=');
            if (equals > 0 && part.substring(0, equals).equals(key)) return Optional.of(part.substring(equals + 1));
        }
        return Optional.empty();
    }

    private static Optional<Object> findPixelmonSpeciesObject(ResourceLocation speciesId) {
        for (String className : java.util.List.of(
                "com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies",
                "com.pixelmonmod.pixelmon.api.pokemon.species.Species",
                "com.pixelmonmod.pixelmon.api.pokemon.Species"
        )) {
            try {
                Class<?> type = Class.forName(className);
                for (String method : new String[] {"get", "getFromName", "fromName", "getSpecies", "getByName", "getById", "getByIdentifier", "getValue"}) {
                    Optional<Object> species = invokeStaticOrSingleton(type, method, titleCase(speciesId.getPath()))
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId.getPath()))
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId.toString()))
                            .or(() -> invokeStaticOrSingleton(type, method, speciesId));
                    if (species.isPresent()) return unwrapSpecies(species.get());
                }
                Optional<Object> fieldSpecies = readStaticField(type, speciesId.getPath().toUpperCase(java.util.Locale.ROOT))
                        .or(() -> readStaticField(type, titleCase(speciesId.getPath()).toUpperCase(java.util.Locale.ROOT)));
                if (fieldSpecies.isPresent()) return unwrapSpecies(fieldSpecies.get());
            } catch (Throwable error) {
                warnOnce("Pixelmon species sprite lookup failed: " + className, error);
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> unwrapSpecies(Object value) {
        if (value == null) return Optional.empty();
        return invoke(value, "getValue").or(() -> invoke(value, "getSpecies")).or(() -> Optional.of(value));
    }

    private static Optional<Object> invoke(Object target, String name, Object... args) {
        if (target == null) return Optional.empty();
        try {
            Method method = findMethod(target.getClass(), name, args);
            if (method == null) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target, args));
        } catch (Throwable error) {
            warnOnce("invoke:" + target.getClass().getName() + "." + name, error);
            return Optional.empty();
        }
    }

    private static Optional<Object> invokeStatic(Class<?> type, String name, Object... args) {
        try {
            Method method = findMethod(type, name, args);
            if (method == null || !java.lang.reflect.Modifier.isStatic(method.getModifiers())) return Optional.empty();
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(null, args));
        } catch (Throwable error) {
            warnOnce("invokeStatic:" + type.getName() + "." + name, error);
            return Optional.empty();
        }
    }

    private static Optional<Object> invokeStaticOrSingleton(Class<?> type, String name, Object... args) {
        Optional<Object> direct = invokeStatic(type, name, args);
        if (direct.isPresent()) return direct;
        for (Object singleton : singletonObjects(type)) {
            Optional<Object> value = invoke(singleton, name, args);
            if (value.isPresent()) return value;
        }
        return Optional.empty();
    }

    private static java.util.List<Object> singletonObjects(Class<?> type) {
        java.util.List<Object> singletons = new java.util.ArrayList<>();
        for (String fieldName : java.util.List.of("INSTANCE", "Companion")) {
            try { Field field = type.getDeclaredField(fieldName); field.setAccessible(true); Object value = field.get(null); if (value != null) singletons.add(value); } catch (Throwable ignored) {}
        }
        return singletons;
    }

    private static Optional<Object> readStaticField(Class<?> type, String name) {
        try { Field field = findField(type, name); if (field == null || !java.lang.reflect.Modifier.isStatic(field.getModifiers())) return Optional.empty(); field.setAccessible(true); return Optional.ofNullable(field.get(null)); }
        catch (Throwable error) { warnOnce("staticField:" + type.getName() + "." + name, error); return Optional.empty(); }
    }

    private static Method findMethod(Class<?> type, String name, Object... args) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == args.length && parametersCompatible(method.getParameterTypes(), args)) return method;
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) { for (Class<?> c = type; c != null; c = c.getSuperclass()) try { return c.getDeclaredField(name); } catch (NoSuchFieldException ignored) {} return null; }

    private static boolean parametersCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null) continue;
            Class<?> parameter = wrapPrimitive(parameterTypes[i]);
            Class<?> actual = wrapPrimitive(args[i].getClass());
            if (!parameter.isAssignableFrom(actual)) return false;
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static String titleCase(String text) { if (text == null || text.isBlank()) return text; return Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(java.util.Locale.ROOT); }
    private static void warnOnce(String message, Throwable error) { if (WARNED.add(message)) com.akitaattribute.mobfarmblock.MobFarmBlockMod.LOGGER.debug("Pixelmon sprite render fallback: {}: {}", message, error.toString()); }
    private PixelmonSpriteRenderer() {}
}
