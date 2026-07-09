package com.akitaattribute.mobfarmblock.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class MobFarmBlockEntityRenderer implements BlockEntityRenderer<MobFarmBlockEntity> {
    private static final int TEXT_WHITE = 0xFFFFFF;
    private static final int TEXT_GREEN = 0x55FF55;
    private static final int TEXT_YELLOW = 0xFFFF55;
    private static final int TEXT_GRAY = 0xC0C0C0;
    private static final int BACKGROUND = 0xB0000000;
    private static final int MAX_ROWS = 7;

    public MobFarmBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MobFarmBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        StoredMob stored = blockEntity.getStored();
        if (stored.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean inspected = isInspected(minecraft, blockEntity.getBlockPos());
        Entity entity = ClientEntityRenderCache.getOrCreate(stored);
        if (entity != null) {
            poseStack.pushPose();
            poseStack.translate(0.5D, 0.58D, 0.5D);
            float scale = 0.32F;
            if (inspected) scale = Math.min(scale, 0.60F / Math.max(0.1F, entity.getBbHeight()));
            poseStack.scale(scale, scale, scale);
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            ClientEntityRenderCache.freezeForRender(entity);
            minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, 0.0F, 0.0F, poseStack, buffer, 0x00F000F0);
            poseStack.popPose();
        }
        if (inspected) renderLookUi(blockEntity, entity, poseStack, buffer, minecraft);
    }

    private static boolean isInspected(Minecraft minecraft, BlockPos pos) {
        if (minecraft.player == null || minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return false;
        if (!((BlockHitResult) minecraft.hitResult).getBlockPos().equals(pos)) return false;
        return minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 36.0D;
    }

    private static void renderLookUi(MobFarmBlockEntity blockEntity, Entity entity, PoseStack poseStack, MultiBufferSource buffer, Minecraft minecraft) {
        StoredMob stored = blockEntity.getStored();
        long now = blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime();
        List<LookRow> rows = lookRows(stored, now);
        if (rows.isEmpty()) return;

        Font font = minecraft.font;
        int rowCount = Math.min(rows.size(), MAX_ROWS);
        int width = font.width(mobLabel(stored));
        for (int i = 0; i < rowCount; i++) width = Math.max(width, rowWidth(font, rows.get(i)));
        width = Math.max(width + 14, 72);
        int height = 12 + rowCount * 13;

        poseStack.pushPose();
        double y = 1.35D + Math.min(0.55D, entity == null ? 0.0D : entity.getBbHeight() * 0.18D);
        poseStack.translate(0.5D, y, 0.5D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.018F, -0.018F, 0.018F);

        int left = -width / 2;
        int top = -height / 2;
        font.drawInBatch(mobLabel(stored), -font.width(mobLabel(stored)) / 2.0F, top + 3, TEXT_WHITE, false, poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, BACKGROUND, LightTexture.FULL_BRIGHT);

        int rowY = top + 15;
        for (int i = 0; i < rowCount; i++) {
            LookRow row = rows.get(i);
            int textX = left + 16;
            if (!row.icon().isEmpty()) renderFlatItem(row.icon(), left + 1, rowY - 2, poseStack, buffer, minecraft);
            font.drawInBatch(row.label(), textX, rowY, row.labelColor(), false, poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, BACKGROUND, LightTexture.FULL_BRIGHT);
            if (!row.value().isBlank()) {
                int valueWidth = font.width(row.value());
                font.drawInBatch(row.value(), left + width - valueWidth - 4, rowY, row.valueColor(), false, poseStack.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH, BACKGROUND, LightTexture.FULL_BRIGHT);
            }
            rowY += 13;
        }
        poseStack.popPose();
    }

    private static void renderFlatItem(ItemStack stack, int x, int y, PoseStack poseStack, MultiBufferSource buffer, Minecraft minecraft) {
        poseStack.pushPose();
        poseStack.translate(x + 8.0D, y + 8.0D, 0.0D);
        poseStack.scale(10.0F, 10.0F, 10.0F);
        minecraft.getItemRenderer().renderStatic(stack, net.minecraft.world.item.ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, 0, poseStack, buffer, minecraft.level, 0);
        poseStack.popPose();
    }

    private static int rowWidth(Font font, LookRow row) {
        int icon = row.icon().isEmpty() ? 0 : 16;
        int value = row.value().isBlank() ? 0 : font.width(row.value()) + 6;
        return icon + font.width(row.label()) + value + 8;
    }

    private static List<LookRow> lookRows(StoredMob stored, long now) {
        List<LookRow> rows = new ArrayList<>();
        int shownDrops = 0;
        for (DropRule rule : stored.dropProfile.drops()) {
            if (shownDrops++ >= 4) break;
            ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId()));
            rows.add(new LookRow(icon, icon.getHoverName().getString(), TEXT_WHITE, chanceText(rule.chance()), TEXT_GREEN));
        }

        if ("minecraft:sheep".equals(stored.mobId.toString())) {
            long readyAt = stored.state.getLong("nextWoolReadyAt");
            DyeColor color = DyeColor.byName(stored.state.getString("sheepColor"), DyeColor.WHITE);
            ItemStack wool = new ItemStack(com.akitaattribute.mobfarmblock.behavior.SheepBehavior.woolForColor(color));
            rows.add(statusRow(wool, wool.getHoverName().getString(), now, readyAt));
        }

        for (InteractionDefinition definition : stored.interactionProfile.definitions()) {
            ResourceLocation method = definition.methodId();
            if (method.equals(MobFarmBlockMod.id("egg"))) {
                rows.add(statusRow(new ItemStack(net.minecraft.world.item.Items.EGG), "Egg", now, stored.readyAtTicks.getOrDefault(method, 0L)));
            }
            if (method.equals(MobFarmBlockMod.id("milk"))) {
                rows.add(new LookRow(new ItemStack(net.minecraft.world.item.Items.MILK_BUCKET), "Milk", TEXT_WHITE, "Ready", TEXT_GREEN));
            }
            if (method.equals(MobFarmBlockMod.id("breed"))) addBreedRow(rows, stored, now);
            Optional<ResourceLocation> output = definition.outputItem();
            output.ifPresent(item -> {
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
                rows.add(statusRow(stack, stack.getHoverName().getString(), now, stored.readyAtTicks.getOrDefault(method, 0L)));
            });
        }

        if (rows.isEmpty()) rows.add(new LookRow(ItemStack.EMPTY, "No outputs configured", TEXT_GRAY, "", TEXT_GRAY));
        return rows;
    }

    private static void addBreedRow(List<LookRow> rows, StoredMob stored, long now) {
        if (!stored.state.getBoolean("breedingCycleActive")) {
            rows.add(new LookRow(new ItemStack(net.minecraft.world.item.Items.WHEAT), "Breeding", TEXT_WHITE, stored.count >= 2 ? "Ready" : "Need 2", stored.count >= 2 ? TEXT_GREEN : TEXT_YELLOW));
            return;
        }
        long base = stored.state.getLong("breedingBaseCount");
        long fed = stored.state.getLong("breedingFedCount");
        long maxFeed = (base / 2L) * 2L;
        long readyAt = stored.state.getLong("breedingReadyAt");
        rows.add(new LookRow(new ItemStack(net.minecraft.world.item.Items.WHEAT), "Breeding " + fed + "/" + maxFeed, TEXT_WHITE, status(now, readyAt), now >= readyAt ? TEXT_GREEN : TEXT_YELLOW));
    }

    private static LookRow statusRow(ItemStack icon, String label, long now, long readyAt) {
        return new LookRow(icon, label, TEXT_WHITE, status(now, readyAt), now >= readyAt ? TEXT_GREEN : TEXT_YELLOW);
    }

    private static String mobLabel(StoredMob stored) {
        String id = stored.speciesId == null ? stored.mobId.toString() : stored.speciesId.toString();
        int colon = id.indexOf(':');
        String name = colon >= 0 ? id.substring(colon + 1) : id;
        name = java.util.Arrays.stream(name.split("[_-]")).filter(part -> !part.isBlank()).map(part -> part.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + part.substring(1)).reduce((a, b) -> a + " " + b).orElse(name);
        return name + " x" + stored.count;
    }

    private static String chanceText(double chance) {
        double percent = Math.max(0.0D, Math.min(1.0D, chance)) * 100.0D;
        if (Math.abs(percent - Math.rint(percent)) < 0.0001D) return Long.toString(Math.round(percent)) + "%";
        return String.format(java.util.Locale.ROOT, "%.2f", percent).replaceAll("0+$", "").replaceAll("\\.$", "") + "%";
    }

    private static String status(long now, long readyAt) {
        long remaining = Math.max(0L, readyAt - now);
        if (remaining <= 0L) return "Ready";
        long seconds = (remaining + 19L) / 20L;
        if (seconds < 60L) return seconds + "s";
        return (seconds / 60L) + "m " + String.format(java.util.Locale.ROOT, "%02d", seconds % 60L) + "s";
    }

    private record LookRow(ItemStack icon, String label, int labelColor, String value, int valueColor) {}
}
