package com.akitaattribute.mobfarmblock.client;

import java.util.ArrayList;
import java.util.List;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.block.MobFarmBlockEntity;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;

public class MobFarmBlockEntityRenderer implements BlockEntityRenderer<MobFarmBlockEntity> {
    public MobFarmBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MobFarmBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        StoredMob stored = blockEntity.getStored();
        if (stored.isEmpty()) return;
        Entity entity = ClientEntityRenderCache.getOrCreate(stored);
        if (entity == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean inspected = isInspected(minecraft, blockEntity.getBlockPos());

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.58D, 0.5D);
        float scale = 0.32F;
        if (inspected) scale = Math.min(scale, 0.60F / Math.max(0.1F, entity.getBbHeight()));
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        ClientEntityRenderCache.freezeForRender(entity);
        minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, buffer, 0x00F000F0);
        poseStack.popPose();

        if (inspected) renderHoverUi(blockEntity, poseStack, buffer, minecraft);
    }

    private static boolean isInspected(Minecraft minecraft, BlockPos pos) {
        if (minecraft.player == null || minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return false;
        if (!((BlockHitResult) minecraft.hitResult).getBlockPos().equals(pos)) return false;
        return minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 36.0D;
    }

    private static void renderHoverUi(MobFarmBlockEntity blockEntity, PoseStack poseStack, MultiBufferSource buffer, Minecraft minecraft) {
        List<HoverLine> lines = hoverLines(blockEntity.getStored(), blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime());
        if (lines.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.30D, 0.5D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.translate(0.0D, 0.0D, 0.03D);
        poseStack.scale(-0.010F, -0.010F, 0.010F);
        Font font = minecraft.font;
        int y = 0;
        for (HoverLine line : lines.subList(0, Math.min(lines.size(), 4))) {
            poseStack.pushPose();
            poseStack.translate(-62.0F, y - 3.0F, 0.0F);
            poseStack.scale(12.0F, 12.0F, 12.0F);
            minecraft.getItemRenderer().renderStatic(line.icon(), ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, 0, poseStack, buffer, minecraft.level, 0);
            poseStack.popPose();
            font.drawInBatch(line.text(), -42.0F, y, line.color(), false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0x99000000, LightTexture.FULL_BRIGHT);
            y += 14;
        }
        poseStack.popPose();
    }

    private static List<HoverLine> hoverLines(StoredMob stored, long now) {
        List<HoverLine> lines = new ArrayList<>();
        int shownDrops = 0;
        for (DropRule rule : stored.dropProfile.drops()) {
            if (shownDrops++ >= 3) break;
            lines.add(new HoverLine(new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId())), chanceText(rule.chance()), 0x00FF00));
        }
        if ("minecraft:sheep".equals(stored.mobId.toString())) {
            long readyAt = stored.state.getLong("nextWoolReadyAt");
            DyeColor color = DyeColor.byName(stored.state.getString("sheepColor"), DyeColor.WHITE);
            lines.add(new HoverLine(new ItemStack(com.akitaattribute.mobfarmblock.behavior.SheepBehavior.woolForColor(color)), status(now, readyAt), now >= readyAt ? 0x00FF00 : 0xFFFF55));
        }
        for (InteractionDefinition definition : stored.interactionProfile.definitions()) {
            ResourceLocation method = definition.methodId();
            if (method.equals(MobFarmBlockMod.id("egg"))) lines.add(new HoverLine(new ItemStack(Items.EGG), status(now, stored.readyAtTicks.getOrDefault(method, 0L)), now >= stored.readyAtTicks.getOrDefault(method, 0L) ? 0x00FF00 : 0xFFFF55));
            if (method.equals(MobFarmBlockMod.id("milk"))) lines.add(new HoverLine(new ItemStack(Items.MILK_BUCKET), "Ready!", 0x00FF00));
            if (method.equals(MobFarmBlockMod.id("breed")) && stored.state.getBoolean("breedingCycleActive")) {
                long base = stored.state.getLong("breedingBaseCount");
                long fed = stored.state.getLong("breedingFedCount");
                long maxFeed = (base / 2L) * 2L;
                long readyAt = stored.state.getLong("breedingReadyAt");
                lines.add(new HoverLine(breedingIcon(definition), "Breeding: " + fed + "/" + maxFeed + " fed, " + status(now, readyAt), now >= readyAt ? 0x00FF00 : 0xFFFF55));
            }
            definition.outputItem().ifPresent(item -> lines.add(new HoverLine(new ItemStack(BuiltInRegistries.ITEM.get(item)), status(now, stored.readyAtTicks.getOrDefault(method, 0L)), now >= stored.readyAtTicks.getOrDefault(method, 0L) ? 0x00FF00 : 0xFFFF55)));
        }
        return lines;
    }

    private static ItemStack breedingIcon(InteractionDefinition definition) {
        if (definition.item().isPresent()) return new ItemStack(BuiltInRegistries.ITEM.get(definition.item().get()));
        return new ItemStack(Items.WHEAT);
    }

    private static String chanceText(double chance) {
        double percent = Math.max(0.0D, Math.min(1.0D, chance)) * 100.0D;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001D) return Long.toString(Math.round(percent)) + "%";
        return String.format(java.util.Locale.ROOT, "%.2f", percent).replaceAll("0+$", "").replaceAll("\\.$", "") + "%";
    }

    private static String status(long now, long readyAt) {
        long remaining = Math.max(0L, readyAt - now);
        if (remaining <= 0L) return "Ready!";
        long seconds = (remaining + 19L) / 20L;
        if (seconds < 60L) return seconds + "s";
        return (seconds / 60L) + "m " + (seconds % 60L) + "s";
    }

    private record HoverLine(ItemStack icon, String text, int color) {}
}
