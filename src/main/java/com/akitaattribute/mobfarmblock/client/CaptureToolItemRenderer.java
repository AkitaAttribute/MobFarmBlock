package com.akitaattribute.mobfarmblock.client;

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
    public CaptureToolItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) { super(dispatcher, modelSet); }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean slotContext = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.FIXED;
        boolean handContext = displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND || displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        poseStack.pushPose();
        if (slotContext) {
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.scale(0.78F, 0.78F, 0.78F);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
        } else if (handContext) {
            poseStack.translate(0.5D, 0.5D, 0.5D);
            poseStack.scale(1.15F, 1.15F, 1.15F);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
        }
        minecraft.getItemRenderer().renderStatic(new ItemStack(Items.SPAWNER), displayContext, packedLight, packedOverlay, poseStack, buffer, minecraft.level, 0);
        poseStack.popPose();

        if (!CaptureToolItem.hasStoredMob(stack) || handContext) return;
        StoredMob stored = CaptureToolItem.getStoredMob(stack);
        Entity entity = ClientEntityRenderCache.getOrCreate(stored);
        if (entity == null) return;

        poseStack.pushPose();
        if (slotContext) {
            poseStack.translate(0.5D, 0.48D, 0.78D);
            float scale = entityScale(entity, stored, 0.34F, 0.82F);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.XP.rotationDegrees(18.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        } else {
            poseStack.translate(0.5D, 0.62D, 0.5D);
            float scale = entityScale(entity, stored, 0.28F, 1.0F);
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.XP.rotationDegrees(25.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
        ClientEntityRenderCache.freezeForRender(entity);
        minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, buffer, 0x00F000F0);
        poseStack.popPose();
    }

    private static float entityScale(Entity entity, StoredMob stored, float base, float maxHeight) {
        float displayScale = stored.display.scale() > 0 ? stored.display.scale() : 1.0F;
        float height = Math.max(entity.getBbHeight(), 0.75F) * displayScale;
        return Math.min(base / Math.max(0.75F, displayScale), base * maxHeight / height);
    }
}
