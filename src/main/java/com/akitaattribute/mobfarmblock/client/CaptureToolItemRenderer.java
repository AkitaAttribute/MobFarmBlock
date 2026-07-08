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
    public CaptureToolItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet modelSet) {
        super(dispatcher, modelSet);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getItemRenderer().renderStatic(new ItemStack(Items.SPAWNER), displayContext, packedLight, packedOverlay, poseStack, buffer, minecraft.level, 0);
        if (!CaptureToolItem.hasStoredMob(stack)) return;
        StoredMob stored = CaptureToolItem.getStoredMob(stack);
        Entity entity = ClientEntityRenderCache.getOrCreate(stored);
        if (entity == null) return;

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.62D, 0.5D);
        float scale = stored.display.scale() > 0 ? 0.28F / Math.max(0.75F, stored.display.scale()) : 0.28F;
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(25.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
