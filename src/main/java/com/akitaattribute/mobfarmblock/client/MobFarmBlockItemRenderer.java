package com.akitaattribute.mobfarmblock.client;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.item.MobFarmBlockItemData;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;
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

public class MobFarmBlockItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final java.util.Set<String> WARNED_RENDER_FAILURES = new java.util.HashSet<>();

    public MobFarmBlockItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) { super(dispatcher, modelSet); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean slotContext = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.FIXED;

        poseStack.pushPose();
        if (slotContext) applyGuiBlockTransform(poseStack);
        minecraft.getBlockRenderer().renderSingleBlock(ModBlocks.MOB_FARM_BLOCK.get().defaultBlockState(), poseStack, buffer, packedLight, packedOverlay);
        poseStack.popPose();

        if (!MobFarmBlockItemData.hasStoredMob(stack) || !slotContext) return;
        StoredMob stored = MobFarmBlockItemData.getStoredMob(stack);
        Entity entity = safeGetRenderEntity(stored);
        if (entity == null) return;

        poseStack.pushPose();
        boolean pixelmon = PixelmonEntityRenderCache.isPixelmonStored(stored);
        float scale = entityScale(entity, stored);
        poseStack.translate(0.5D, pixelmon ? 0.60D : 0.64D, 0.18D);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0D, -(pixelmon ? renderHeight(entity, stored) : entity.getBbHeight()) * 0.50D, 0.0D);
        ClientEntityRenderCache.freezeForRender(entity);
        try {
            minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0F, 0.0F, 0.0F, poseStack, buffer, LightTexture.FULL_BRIGHT);
        } catch (Throwable error) {
            warnRenderFailure(stored, error);
        }
        poseStack.popPose();
    }

    private static void applyGuiBlockTransform(PoseStack poseStack) {
        poseStack.translate(0.5D, 0.48D, 0.5D);
        poseStack.scale(0.82F, 0.82F, 0.82F);
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
            MobFarmBlockMod.LOGGER.error("Mob Farm Block item mob overlay render failed for {}; skipping overlay so item rendering cannot crash", key, error);
        }
    }

    private static float entityScale(Entity entity, StoredMob stored) {
        if (PixelmonEntityRenderCache.isPixelmonStored(stored)) {
            float bounding = Math.max(renderHeight(entity, stored), renderWidth(entity, stored));
            float fit = 0.62F / Math.max(0.1F, bounding);
            return Math.max(0.14F, Math.min(0.74F, fit));
        }
        float displayScale = stored.display.scale() > 0 ? stored.display.scale() : 1.0F;
        float height = Math.max(entity.getBbHeight(), 0.35F) * displayScale;
        float width = Math.max(entity.getBbWidth(), 0.35F) * displayScale;
        float bounding = Math.max(height, width);
        float fit = 0.62F / Math.max(0.1F, bounding);
        return Math.max(0.24F, Math.min(0.74F, fit));
    }

    private static float renderHeight(Entity entity, StoredMob stored) {
        PixelmonRenderSnapshot snapshot = stored == null ? null : stored.pixelmonRenderSnapshot;
        if (snapshot != null && snapshot.capturedHeight() > 0.05F) return snapshot.capturedHeight();
        return Math.max(entity.getBbHeight(), 0.35F);
    }

    private static float renderWidth(Entity entity, StoredMob stored) {
        PixelmonRenderSnapshot snapshot = stored == null ? null : stored.pixelmonRenderSnapshot;
        if (snapshot != null && snapshot.capturedWidth() > 0.05F) return snapshot.capturedWidth();
        return Math.max(entity.getBbWidth(), 0.35F);
    }
}
