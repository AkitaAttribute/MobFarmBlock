package com.akitaattribute.mobfarmblock.client;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class CaptureToolItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static final java.util.Set<String> WARNED_RENDER_FAILURES = new java.util.HashSet<>();

    public CaptureToolItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) { super(dispatcher, modelSet); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean slotContext = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.FIXED;
        poseStack.pushPose();
        // Render the base at the vanilla item transform for every context.  The
        // overlay has its own GUI-only transform so hand/world transforms cannot
        // pull the base item off-center.
        minecraft.getItemRenderer().renderStatic(new ItemStack(Items.SPAWNER), displayContext, packedLight, packedOverlay, poseStack, buffer, minecraft.level, 0);
        poseStack.popPose();

        if (!CaptureToolItem.hasStoredMob(stack) || !slotContext) return;
        StoredMob stored = CaptureToolItem.getStoredMob(stack);
        Entity entity = safeGetRenderEntity(stored);
        if (entity == null) return;

        poseStack.pushPose();
        float scale = entityScale(entity, stored);
        poseStack.translate(0.5D, 0.62D, 0.95D);
        poseStack.scale(scale, scale, scale);
        poseStack.translate(0.0D, -entity.getBbHeight() * 0.48D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        ClientEntityRenderCache.freezeForRender(entity);
        minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, buffer, 0x00F000F0);
        poseStack.popPose();
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
        float fit = 0.78F / Math.max(0.1F, bounding);
        return Math.max(0.32F, Math.min(0.95F, fit));
    }
}
