package com.akitaattribute.mobfarmblock.client;

import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public class CaptureToolItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final float PIXELMON_CAPTURE_PREVIEW_HEIGHT = 0.72F;
    private static final Pattern NUMBER = Pattern.compile("-?\\d+(?:\\.\\d+)?");
    private static final java.util.Set<String> WARNED_RENDER_FAILURES = new java.util.HashSet<>();
    private static final java.util.Set<String> LOGGED_PIXELMON_METRICS = new java.util.HashSet<>();

    public CaptureToolItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) { super(dispatcher, modelSet); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean slotContext = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.FIXED;
        boolean filled = CaptureToolItem.hasStoredMob(stack);

        poseStack.pushPose();
        if (slotContext) applyGuiToolTransform(poseStack, filled);
        minecraft.getBlockRenderer().renderSingleBlock(Blocks.SPAWNER.defaultBlockState(), poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();

        if (!filled || !slotContext) return;
        StoredMob stored = CaptureToolItem.getStoredMob(stack);
        Entity entity = safeGetRenderEntity(stored);
        if (entity == null) return;

        poseStack.pushPose();
        boolean pixelmon = PixelmonEntityRenderCache.isPixelmonStored(stored);
        if (pixelmon) {
            float scale = pixelmonPreviewScale(stored, entity, PIXELMON_CAPTURE_PREVIEW_HEIGHT);
            logPixelmonMetrics("capture_tool", stored, entity, minecraft, scale);
            poseStack.translate(0.50D, 0.62D, 1.22D);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(0.0D, -pixelmonCenterY(entity), 0.0D);
        } else {
            float scale = entityScale(entity, stored);
            poseStack.translate(0.66D, 0.84D, 0.08D);
            poseStack.scale(scale, scale, scale);
            poseStack.translate(0.0D, -entity.getBbHeight() * 0.50D, 0.0D);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        ClientEntityRenderCache.freezeForRender(entity);
        try {
            minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, buffer, LightTexture.FULL_BRIGHT);
        } catch (Throwable error) {
            warnRenderFailure(stored, error);
        }
        poseStack.popPose();
    }

    private static void applyGuiToolTransform(PoseStack poseStack, boolean filled) {
        poseStack.translate(filled ? 0.36D : 0.5D, filled ? 0.42D : 0.50D, 0.5D);
        float scale = filled ? 0.92F : 0.82F;
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(28.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(225.0F));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    private static Entity safeGetRenderEntity(StoredMob stored) {
        try {
            Entity pixelmon = PixelmonEntityRenderCache.getOrCreate(stored);
            if (pixelmon != null) return pixelmon;
            return ClientEntityRenderCache.getOrCreate(stored);
        } catch (Throwable error) {
            warnRenderFailure(stored, error);
            return null;
        }
    }

    private static void warnRenderFailure(StoredMob stored, Throwable error) {
        String key = stored == null ? "unknown" : stored.mobId + "|" + stored.speciesId + "|" + stored.display.variantKey();
        if (WARNED_RENDER_FAILURES.add(key)) {
            MobFarmBlockMod.LOGGER.error("Capture Tool mob overlay render failed for {}; skipping overlay so item rendering cannot crash", key, error);
        }
    }

    private static float entityScale(Entity entity, StoredMob stored) {
        float height = Math.max(entity.getBbHeight(), 0.35F) * (stored.display.scale() > 0 ? stored.display.scale() : 1.0F);
        float width = Math.max(entity.getBbWidth(), 0.35F) * (stored.display.scale() > 0 ? stored.display.scale() : 1.0F);
        float bounding = Math.max(height, width);
        float fit = 0.98F / Math.max(0.1F, bounding);
        return Math.max(0.36F, Math.min(1.08F, fit));
    }

    private static float pixelmonPreviewScale(StoredMob stored, Entity entity, float targetHeight) {
        float visual = pixelmonVisualDimension(stored, entity);
        return visual > targetHeight ? targetHeight / Math.max(0.1F, visual) : 1.0F;
    }

    private static float pixelmonVisualDimension(StoredMob stored, Entity entity) {
        float dimension = Math.max(entity.getBbHeight(), entity.getBbWidth());
        PixelmonRenderSnapshot snapshot = stored == null ? null : stored.pixelmonRenderSnapshot;
        if (snapshot != null) {
            dimension = Math.max(dimension, Math.max(snapshot.capturedHeight(), snapshot.capturedWidth()));
            if (snapshot.sizeCentimeters() > 0.0F) dimension = Math.max(dimension, snapshot.sizeCentimeters() / 100.0F);
        }
        float variantSize = pixelmonSizeMeters(stored);
        if (variantSize > 0.0F) dimension = Math.max(dimension, variantSize);
        return Math.max(0.35F, dimension);
    }

    private static float pixelmonSizeMeters(StoredMob stored) {
        String variant = stored == null || stored.display == null ? "" : stored.display.variantKey();
        String size = parseVariantValue(variant, "size");
        String source = size.isBlank() ? variant : size;
        Matcher matcher = NUMBER.matcher(source);
        if (!matcher.find()) return 0.0F;
        try {
            float value = Float.parseFloat(matcher.group());
            if (value <= 0.0F) return 0.0F;
            String lower = source.toLowerCase(java.util.Locale.ROOT);
            float centimeters = lower.contains("cm") || value > 10.0F ? value : value * 100.0F;
            return centimeters / 100.0F;
        } catch (Throwable ignored) {
            return 0.0F;
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

    private static float pixelmonCenterY(Entity entity) {
        try {
            Method method = entity.getClass().getMethod("getYCentre");
            Object value = method.invoke(entity);
            if (value instanceof Number number && number.floatValue() > 0.0F) return number.floatValue();
        } catch (Throwable ignored) {
        }
        return Math.max(0.35F, entity.getBbHeight()) * 0.50F;
    }

    private static void logPixelmonMetrics(String context, StoredMob stored, Entity entity, Minecraft minecraft, float scale) {
        String key = context + "|" + stored.speciesId + "|" + stored.display.variantKey();
        if (!LOGGED_PIXELMON_METRICS.add(key)) return;
        String renderer = "unknown";
        try {
            renderer = minecraft.getEntityRenderDispatcher().getRenderer(entity).getClass().getName();
        } catch (Throwable ignored) {
        }
        MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon render metrics: context={} species={} variant={} bbWidth={} bbHeight={} yCentre={} displayScale={} renderer={} snapshotWidth={} snapshotHeight={} visualDimension={} appliedScale={}",
                context, stored.speciesId, stored.display.variantKey(), entity.getBbWidth(), entity.getBbHeight(), pixelmonCenterY(entity), stored.display.scale(), renderer,
                stored.pixelmonRenderSnapshot == null ? 0.0F : stored.pixelmonRenderSnapshot.capturedWidth(),
                stored.pixelmonRenderSnapshot == null ? 0.0F : stored.pixelmonRenderSnapshot.capturedHeight(), pixelmonVisualDimension(stored, entity), scale);
    }
}
