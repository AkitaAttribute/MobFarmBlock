package com.akitaattribute.mobfarmblock.client;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
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
import com.akitaattribute.mobfarmblock.block.MobFarmBlock;
import com.akitaattribute.mobfarmblock.block.MobFarmBlockEntity;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Debug-only client scanner for Pixelmon pen render/model metrics. */
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
        String key = "extended|mesh-v2|" + dimension + "|" + blockEntity.getBlockPos().asLong() + "|" + species + "|" + MobFarmConfig.PIXELMON_RENDER_REPLAY_MODE.get();
        if (!LOGGED.add(key)) return;

        Object pokemon = invoke(entity, "getPokemon").or(() -> readField(entity, "pokemon")).orElse(null);
        Object delegate = readField(entity, "delegate").orElse(null);
        Object delegatePokemon = delegate == null ? null : invoke(delegate, "getPokemon").or(() -> readField(delegate, "pokemon")).orElse(null);
        Object effectivePokemon = pokemon != null ? pokemon : delegatePokemon;
        Object speciesObject = value(effectivePokemon, "getSpecies", "species");
        Object formObject = value(effectivePokemon, "getForm", "form");
        Object paletteObject = value(effectivePokemon, "getPalette", "palette");
        Object renderer = renderer(entity);
        Object formDimensions = firstValue(formObject, speciesObject, effectivePokemon, entity, delegate, null, null, List.of("getDimensions", "dimensions"));
        Object modelData = firstValue(renderer, entity, effectivePokemon, speciesObject, formObject, paletteObject, delegate, List.of(
                "getModelData", "modelData", "getRenderData", "renderData", "getModel", "model", "getBaseModel", "baseModel",
                "getBakedModel", "bakedModel", "getDimensions", "dimensions", "getBounds", "bounds"
        ));
        Object modelDataFirst = firstIterable(modelData);
        Object nestedModelData = firstValue(modelDataFirst, modelData, renderer, entity, effectivePokemon, formObject, speciesObject, List.of(
                "getModel", "model", "getBaseModel", "baseModel", "getRenderData", "renderData", "getDimensions", "dimensions", "getBounds", "bounds"
        ));
        MeshBounds meshBounds = measureMesh(blockEntity, stored, entity);
        PixelmonRenderSnapshot snapshot = stored.pixelmonRenderSnapshot;

        try {
            Files.createDirectories(LOG_FILE.getParent());
            StringBuilder out = new StringBuilder(8192);
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
            json(out, "formDimensionsClass", className(formDimensions)).append(',');
            json(out, "modelDataClass", className(modelData)).append(',');
            json(out, "modelDataFirstClass", className(modelDataFirst)).append(',');
            json(out, "nestedModelDataClass", className(nestedModelData)).append(',');
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
            json(out, "formDimensionsObjectHints", inspect(formDimensions)).append(',');
            json(out, "paletteDimensionHints", inspect(paletteObject)).append(',');
            json(out, "rendererDimensionHints", inspect(renderer)).append(',');
            json(out, "modelDimensionHints", inspect(modelData)).append(',');
            json(out, "modelDataFirstHints", inspect(modelDataFirst)).append(',');
            json(out, "nestedModelDataHints", inspect(nestedModelData)).append(',');
            appendMeshBounds(out, meshBounds);
            out.append('}').append(System.lineSeparator());
            Files.writeString(LOG_FILE, out.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException error) {
            MobFarmBlockMod.LOGGER.warn("Failed to write extended Pixelmon pen render metrics", error);
        }
    }

    private static MeshBounds measureMesh(MobFarmBlockEntity blockEntity, StoredMob stored, Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        MeshBounds bounds = new MeshBounds();
        try {
            PoseStack poseStack = new PoseStack();
            poseStack.translate(0.5D, 0.58D, 0.5D);
            float scale = placedEntityScale(stored, entity, false);
            PixelmonPenCentering centering = pixelmonPenCentering(entity, stored);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(blockEntity.getBlockState().getValue(MobFarmBlock.FACING).toYRot()));
            applyPixelmonFacing(entity, 0.0F);
            poseStack.translate(centering.xOffset(), 0.0D, centering.zOffset());
            minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, bounds.buffer(), 0x00F000F0);
        } catch (Throwable error) {
            bounds.error(error);
        }
        return bounds;
    }

    private static PixelmonPenCentering pixelmonPenCentering(Entity entity, StoredMob stored) {
        AABB box = entity.getBoundingBox();
        double xCenter = ((box.minX + box.maxX) * 0.5D) - entity.getX();
        double zCenter = ((box.minZ + box.maxZ) * 0.5D) - entity.getZ();
        double visualLength = Math.max(renderWidth(entity, stored), pixelmonRenderHeightMeters(stored).orElse(0.0F));
        double elongatedOffset = Math.max(0.0D, visualLength - 1.0D) * 0.50D;
        double xOffset = Double.isFinite(xCenter) && Math.abs(xCenter) > 0.001D ? -xCenter : 0.0D;
        double zOffset = Double.isFinite(zCenter) && Math.abs(zCenter) > 0.001D ? -zCenter : 0.0D;
        if (Double.isFinite(elongatedOffset) && elongatedOffset > 0.001D) zOffset += elongatedOffset;
        return new PixelmonPenCentering(xOffset, zOffset, xCenter, zCenter, visualLength, elongatedOffset);
    }

    private static float placedEntityScale(StoredMob stored, Entity entity, boolean inspected) {
        boolean shrink = inspected || MobFarmConfig.PENS_ALWAYS_SHOW_SMALL.get();
        if (!shrink) return 1.0F;
        float renderedHeight = renderHeight(entity, stored);
        return renderedHeight > 0.60F ? 0.60F / Math.max(0.1F, renderedHeight) : 1.0F;
    }

    private static float renderHeight(Entity entity, StoredMob stored) {
        float height = Math.max(entity.getBbHeight(), 0.35F);
        PixelmonRenderSnapshot snapshot = stored == null ? null : stored.pixelmonRenderSnapshot;
        if (snapshot != null && snapshot.capturedHeight() > 0.05F) height = Math.max(height, snapshot.capturedHeight());
        return height;
    }

    private static float renderWidth(Entity entity, StoredMob stored) {
        float width = Math.max(entity.getBbWidth(), 0.35F);
        PixelmonRenderSnapshot snapshot = stored == null ? null : stored.pixelmonRenderSnapshot;
        if (snapshot != null && snapshot.capturedWidth() > 0.05F) width = Math.max(width, snapshot.capturedWidth());
        return width;
    }

    private static Optional<Float> pixelmonRenderHeightMeters(StoredMob stored) {
        if (stored == null) return Optional.empty();
        PixelmonRenderSnapshot snapshot = stored.pixelmonRenderSnapshot;
        if (snapshot != null && snapshot.sizeCentimeters() > 0.0F) return Optional.of(snapshot.sizeCentimeters() / 100.0F);
        return Optional.empty();
    }

    private static void applyPixelmonFacing(Entity entity, float yaw) {
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yHeadRot = yaw;
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
                if (parts.size() >= 160) return;
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
                if (parts.size() >= 160) return;
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
                || lower.equals("x") || lower.equals("y") || lower.equals("z") || lower.contains("min") || lower.contains("max");
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

    private static void appendMeshBounds(StringBuilder out, MeshBounds bounds) {
        json(out, "meshMeasureError", bounds.errorMessage()).append(',');
        num(out, "meshVertexCount", bounds.vertexCount()).append(',');
        num(out, "meshMinX", bounds.minX()).append(',');
        num(out, "meshMinY", bounds.minY()).append(',');
        num(out, "meshMinZ", bounds.minZ()).append(',');
        num(out, "meshMaxX", bounds.maxX()).append(',');
        num(out, "meshMaxY", bounds.maxY()).append(',');
        num(out, "meshMaxZ", bounds.maxZ()).append(',');
        num(out, "meshWidth", bounds.width()).append(',');
        num(out, "meshHeight", bounds.height()).append(',');
        num(out, "meshDepth", bounds.depth()).append(',');
        num(out, "meshCenterX", bounds.centerX()).append(',');
        num(out, "meshCenterY", bounds.centerY()).append(',');
        num(out, "meshCenterZ", bounds.centerZ());
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

    private record PixelmonPenCentering(double xOffset, double zOffset, double xCenter, double zCenter, double visualLength, double elongatedDepthOffset) {}

    private static final class MeshBounds {
        private double minX = Double.POSITIVE_INFINITY;
        private double minY = Double.POSITIVE_INFINITY;
        private double minZ = Double.POSITIVE_INFINITY;
        private double maxX = Double.NEGATIVE_INFINITY;
        private double maxY = Double.NEGATIVE_INFINITY;
        private double maxZ = Double.NEGATIVE_INFINITY;
        private int vertexCount;
        private String errorMessage = "";
        private VertexConsumer proxy;

        private MultiBufferSource buffer() {
            return new MultiBufferSource() {
                @Override
                public VertexConsumer getBuffer(RenderType renderType) {
                    return vertexConsumer();
                }
            };
        }

        private VertexConsumer vertexConsumer() {
            if (proxy == null) {
                proxy = (VertexConsumer) Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(), new Class<?>[] { VertexConsumer.class }, (object, method, args) -> {
                    if ("toString".equals(method.getName())) return "MobFarmBlockMeshMeasureVertexConsumer";
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(object);
                    if ("equals".equals(method.getName())) return object == (args == null ? null : args[0]);
                    if ("addVertex".equals(method.getName()) || "vertex".equals(method.getName())) recordVertex(args);
                    if (VertexConsumer.class.isAssignableFrom(method.getReturnType())) return object;
                    return defaultValue(method.getReturnType());
                });
            }
            return proxy;
        }

        private void recordVertex(Object[] args) {
            if (args == null) return;
            try {
                if (args.length >= 4 && args[0] instanceof Matrix4f matrix && args[1] instanceof Number x && args[2] instanceof Number y && args[3] instanceof Number z) {
                    Vector3f transformed = matrix.transformPosition(x.floatValue(), y.floatValue(), z.floatValue(), new Vector3f());
                    include(transformed.x(), transformed.y(), transformed.z());
                    return;
                }
                if (args.length >= 2 && args[1] instanceof Vector3f vector) {
                    Matrix4f matrix = poseMatrix(args[0]);
                    if (matrix != null) {
                        Vector3f transformed = matrix.transformPosition(vector.x(), vector.y(), vector.z(), new Vector3f());
                        include(transformed.x(), transformed.y(), transformed.z());
                    } else include(vector.x(), vector.y(), vector.z());
                    return;
                }
                if (args.length >= 3 && args[0] instanceof Number x && args[1] instanceof Number y && args[2] instanceof Number z) {
                    include(x.doubleValue(), y.doubleValue(), z.doubleValue());
                    return;
                }
                if (args.length >= 1 && args[0] instanceof Vector3f vector) {
                    include(vector.x(), vector.y(), vector.z());
                }
            } catch (Throwable error) {
                error(error);
            }
        }

        private Matrix4f poseMatrix(Object pose) {
            if (pose instanceof Matrix4f matrix) return matrix;
            if (pose == null) return null;
            try {
                Method method = pose.getClass().getDeclaredMethod("pose");
                method.setAccessible(true);
                Object value = method.invoke(pose);
                return value instanceof Matrix4f matrix ? matrix : null;
            } catch (Throwable ignored) {
                return null;
            }
        }

        private void include(double x, double y, double z) {
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return;
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            vertexCount++;
        }

        private void error(Throwable error) {
            if (errorMessage.isBlank() && error != null) errorMessage = error.getClass().getName() + ":" + String.valueOf(error.getMessage());
        }

        private Object defaultValue(Class<?> type) {
            if (type == Boolean.TYPE) return false;
            if (type == Byte.TYPE) return (byte) 0;
            if (type == Short.TYPE) return (short) 0;
            if (type == Integer.TYPE) return 0;
            if (type == Long.TYPE) return 0L;
            if (type == Float.TYPE) return 0.0F;
            if (type == Double.TYPE) return 0.0D;
            if (type == Character.TYPE) return (char) 0;
            return null;
        }

        private boolean valid() { return vertexCount > 0; }
        private String errorMessage() { return errorMessage; }
        private int vertexCount() { return vertexCount; }
        private double minX() { return valid() ? minX : 0.0D; }
        private double minY() { return valid() ? minY : 0.0D; }
        private double minZ() { return valid() ? minZ : 0.0D; }
        private double maxX() { return valid() ? maxX : 0.0D; }
        private double maxY() { return valid() ? maxY : 0.0D; }
        private double maxZ() { return valid() ? maxZ : 0.0D; }
        private double width() { return valid() ? maxX - minX : 0.0D; }
        private double height() { return valid() ? maxY - minY : 0.0D; }
        private double depth() { return valid() ? maxZ - minZ : 0.0D; }
        private double centerX() { return valid() ? (minX + maxX) * 0.5D : 0.0D; }
        private double centerY() { return valid() ? (minY + maxY) * 0.5D : 0.0D; }
        private double centerZ() { return valid() ? (minZ + maxZ) * 0.5D : 0.0D; }
    }

    private PixelmonPenRenderMetricsDumper() {}
}
