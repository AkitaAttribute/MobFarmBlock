package com.akitaattribute.mobfarmblock.client;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;
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
    private static final java.util.Set<String> WARNED_RENDER_FAILURES = new java.util.HashSet<>();

    public CaptureToolItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) { super(dispatcher, modelSet); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean slotContext = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.FIXED;

        poseStack.pushPose();
        if (slotContext) applyGuiToolTransform(poseStack);
        minecraft.getBlockRenderer().renderSingleBlock(Blocks.SPAWNER.defaultBlockState(), poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();

        if (!CaptureToolItem.hasStoredMob(stack) || !slotContext) return;
        StoredMob stored = CaptureToolItem.getStoredMob(stack);
        Entity entity = safeGetRenderEntity(stored);
        if (entity == null) return;

        poseStack.pushPose();
        float scale = entityScale(entity, stored);
        poseStack.translate(0.5D, 0.68D, 0.18D);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0D, -entity.getBbHeight() * 0.46D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        ClientEntityRenderCache.freezeForRender(entity);
        minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, buffer, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }

    private static void applyGuiToolTransform(PoseStack poseStack) {
        poseStack.translate(0.5D, 0.50D, 0.5D);
        poseStack.scale(0.78F, 0.78F, 0.78F);
        poseStack.mulPose(Axis.XP.rotationDegrees(28.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(225.0F));
        poseStack.translate(-0.5D, -0.5D, -0.5D);
    }

    private static Entity safeGetRenderEntity(StoredMob stored) {
        try {
            return ClientEntityRenderCache.getOrCreate(stored);
        } catch (Throwable error) {
            String key = stored == null ? "unknown" : stored.mobId + "|" + stored.speciesId + "|" + stored.display.variantKey();
            if (WARNED_RENDER_FAILURES.add(key)) {
                MobFarmBlockMod.LOGGER.error("Capture Tool mob overlay render failed for {}; skipping overlay so item rendering cannot crash", key, error);
            }
            return null;
        }
    }

    private static float entityScale(Entity entity, StoredMob stored) {
        float displayScale = stored.display.scale() > 0 ? stored.display.scale() : 1.0F;
        float height = Math.max(entity.getBbHeight(), 0.35F) * displayScale;
        float width = Math.max(entity.getBbWidth(), 0.35F) * displayScale;
        float bounding = Math.max(height, width);
        float fit = 0.60F / Math.max(0.1F, bounding);
        return Math.max(0.24F, Math.min(0.72F, fit));
    }
}
