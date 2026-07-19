package com.akitaattribute.mobfarmblock.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.block.MobFarmBlock;
import com.akitaattribute.mobfarmblock.block.MobFarmBlockEntity;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;
import com.akitaattribute.mobfarmblock.mob.StoredMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.joml.Matrix4f;

public class MobFarmBlockEntityRenderer implements BlockEntityRenderer<MobFarmBlockEntity> {
    private static final int TEXT_WHITE = 0xFFFFFF;
    private static final int TEXT_GREEN = 0x55FF55;
    private static final int TEXT_YELLOW = 0xFFFF55;
    private static final int TEXT_GRAY = 0xC0C0C0;
    private static final int NO_TEXT_BACKGROUND = 0x00000000;
    private static final int COMPACT_COLUMNS = 4;
    private static final int EXPANDED_COLUMNS = 2;
    private static final int MIN_COMPACT_COLUMN_WIDTH = 30;
    private static final Map<String, Sheep> SHEEP_RENDER_CACHE = new HashMap<>();

    public MobFarmBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MobFarmBlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        StoredMob stored = blockEntity.getStored();
        if (stored.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        boolean inspected = isInspected(minecraft, blockEntity.getBlockPos());
        boolean pixelmon = PixelmonEntityRenderCache.isPixelmonStored(stored);
        Entity entity = renderEntity(stored, minecraft);
        if (entity != null) {
            float yaw = blockEntity.getBlockState().getValue(MobFarmBlock.FACING).toYRot();
            poseStack.pushPose();
            poseStack.translate(0.5D, pixelmon ? 1.05D : 0.58D, 0.5D);
            float scale = placedEntityScale(stored, entity, inspected);
            poseStack.scale(scale, scale, scale);
            if (pixelmon) applyPixelmonFacing(entity, yaw);
            else poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
            ClientEntityRenderCache.freezeForRender(entity);
            try {
                minecraft.getEntityRenderDispatcher().render(entity, 0.0D, 0.0D, 0.0D, pixelmon ? yaw : 0.0F, 0.0F, poseStack, buffer, 0x00F000F0);
            } catch (Throwable error) {
                MobFarmBlockMod.LOGGER.error("Placed pen mob render failed for {}; skipping entity overlay", stored.speciesId != null ? stored.speciesId : stored.mobId, error);
            }
            poseStack.popPose();
        }
        if (inspected) renderLookUi(blockEntity, entity, poseStack, buffer, minecraft);
    }

    private static Entity renderEntity(StoredMob stored, Minecraft minecraft) {
        if ("minecraft:sheep".equals(stored.mobId.toString())) return sheepRenderEntity(stored, minecraft);
        if (PixelmonEntityRenderCache.isPixelmonStored(stored)) return PixelmonEntityRenderCache.getOrCreate(stored);
        return ClientEntityRenderCache.getOrCreate(stored);
    }

    private static void applyPixelmonFacing(Entity entity, float yaw) {
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        if (entity instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yHeadRot = yaw;
        }
    }

    private static float placedEntityScale(StoredMob stored, Entity entity, boolean inspected) {
        if (PixelmonEntityRenderCache.isPixelmonStored(stored)) {
            float displayScale = stored.display.scale() > 0 ? stored.display.scale() : 1.0F;
            float height = Math.max(0.75F, entity.getBbHeight() * displayScale);
            float width = Math.max(0.75F, entity.getBbWidth() * displayScale);
            float maxDimension = Math.max(height, width);
            float scale = 1.20F / Math.max(0.1F, maxDimension);
            scale = Math.max(0.25F, Math.min(2.25F, scale));
            if (inspected) scale = Math.min(scale, 0.60F / Math.max(0.1F, maxDimension));
            return Math.max(0.08F, scale);
        }
        float scale = 0.32F;
        if (inspected) scale = Math.min(scale, 0.60F / Math.max(0.1F, entity.getBbHeight()));
        return scale;
    }

    private static Entity sheepRenderEntity(StoredMob stored, Minecraft minecraft) {
        if (minecraft.level == null) return null;
        long now = minecraft.level.getGameTime();
        long readyAt = Math.max(stored.state.getLong("nextWoolReadyAt"), stored.readyAtTicks.getOrDefault(MobFarmBlockMod.id("shear"), 0L));
        boolean sheared = now < readyAt;
        String colorName = stored.state.getString("sheepColor");
        if (colorName.isBlank()) colorName = stored.display.colorKey().isBlank() ? "white" : stored.display.colorKey();
        boolean baby = stored.display.baby();
        String key = colorName + "|" + baby + "|" + sheared;
        Sheep sheep = SHEEP_RENDER_CACHE.computeIfAbsent(key, ignored -> EntityType.SHEEP.create(minecraft.level));
        if (sheep == null) return ClientEntityRenderCache.getOrCreate(stored);
        sheep.setColor(DyeColor.byName(colorName, DyeColor.WHITE));
        sheep.setBaby(baby);
        sheep.setSheared(sheared);
        return sheep;
    }

    private static boolean isInspected(Minecraft minecraft, BlockPos pos) {
        if (minecraft.player == null || minecraft.hitResult == null || minecraft.hitResult.getType() != HitResult.Type.BLOCK) return false;
        if (!((BlockHitResult) minecraft.hitResult).getBlockPos().equals(pos)) return false;
        return minecraft.player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 36.0D;
    }

    private static void renderLookUi(MobFarmBlockEntity blockEntity, Entity entity, PoseStack poseStack, MultiBufferSource buffer, Minecraft minecraft) {
        if (MobFarmConfig.LOOK_UI_STYLE.get() == MobFarmConfig.LookUiStyle.NAMETAG) {
            renderNametagLookUi(blockEntity, entity, poseStack, buffer, minecraft);
        }
    }

    private static void renderNametagLookUi(MobFarmBlockEntity blockEntity, Entity entity, PoseStack poseStack, MultiBufferSource buffer, Minecraft minecraft) {
        StoredMob stored = blockEntity.getStored();
        long now = blockEntity.getLevel() == null ? 0L : blockEntity.getLevel().getGameTime();
        boolean expanded = minecraft.player != null && minecraft.player.isShiftKeyDown();
        List<LookRow> rows = expanded ? expandedRows(stored, now) : compactRows(stored, now);
        if (rows.isEmpty()) return;

        poseStack.pushPose();
        double y = 1.35D + Math.min(0.55D, entity == null ? 0.0D : entity.getBbHeight() * 0.18D);
        poseStack.translate(0.5D, y, 0.5D);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.018F, -0.018F, 0.018F);

        if (expanded) renderExpandedNametag(stored, rows, poseStack, buffer, minecraft);
        else renderCompactColumns(stored, rows, poseStack, buffer, minecraft);

        poseStack.popPose();
    }

    private static void renderExpandedNametag(StoredMob stored, List<LookRow> rows, PoseStack poseStack, MultiBufferSource buffer, Minecraft minecraft) {
        Font font = minecraft.font;
        int columnCount = Math.min(EXPANDED_COLUMNS, Math.max(1, rows.size()));
        int rowCount = rowsFor(rows.size(), columnCount);
        int cellWidth = 72;
        for (LookRow row : rows) cellWidth = Math.max(cellWidth, rowWidth(font, row, true) + 8);
        int width = Math.max(font.width(mobLabel(stored)) + 8, columnCount * cellWidth);
        int height = 12 + rowCount * 13;
        int left = -width / 2;
        int top = -height / 2;
        int gridLeft = left + (width - columnCount * cellWidth) / 2;

        String title = mobLabel(stored);
        drawLookText(font, title, -font.width(title) / 2.0F, top + 2, TEXT_WHITE, poseStack, buffer);

        int gridTop = top + 15;
        for (int i = 0; i < rows.size(); i++) {
            LookRow row = rows.get(i);
            int column = i % columnCount;
            int gridRow = i / columnCount;
            int cellLeft = gridLeft + column * cellWidth;
            int rowY = gridTop + gridRow * 13;
            if (!row.icon().isEmpty()) renderLookItem(row.icon(), cellLeft + 1, rowY - 3, poseStack, buffer, minecraft);
            int textX = cellLeft + 16;
            drawLookText(font, row.label(), textX, rowY, row.labelColor(), poseStack, buffer);
            if (!row.value().isBlank()) {
                int valueWidth = font.width(row.value());
                drawLookText(font, row.value(), cellLeft + cellWidth - valueWidth - 4, rowY, row.valueColor(), poseStack, buffer);
            }
        }
    }

    private static void renderCompactColumns(StoredMob stored, List<LookRow> rows, PoseStack poseStack, MultiBufferSource buffer, Minecraft minecraft) {
        Font font = minecraft.font;
        int columnCount = Math.min(COMPACT_COLUMNS, Math.max(1, rows.size()));
        int rowCount = rowsFor(rows.size(), columnCount);
        String title = "x" + stored.count;
        int columnWidth = MIN_COMPACT_COLUMN_WIDTH;
        for (LookRow row : rows) columnWidth = Math.max(columnWidth, font.width(compactValue(row)) + 8);
        int width = Math.max(font.width(title) + 8, columnCount * columnWidth);
        int height = 12 + rowCount * 28;
        int left = -width / 2;
        int top = -height / 2;
        int gridLeft = left + (width - columnCount * columnWidth) / 2;

        drawLookText(font, title, -font.width(title) / 2.0F, top, TEXT_WHITE, poseStack, buffer);

        int gridTop = top + 13;
        for (int i = 0; i < rows.size(); i++) {
            LookRow row = rows.get(i);
            int column = i % columnCount;
            int gridRow = i / columnCount;
            int columnLeft = gridLeft + column * columnWidth;
            int centerX = columnLeft + columnWidth / 2;
            int rowTop = gridTop + gridRow * 28;
            if (!row.icon().isEmpty()) renderLookItem(row.icon(), centerX - 8, rowTop, poseStack, buffer, minecraft);
            String value = compactValue(row);
            drawLookText(font, value, centerX - font.width(value) / 2.0F, rowTop + 15, row.valueColor(), poseStack, buffer);
        }
    }

    private static int rowsFor(int itemCount, int columnCount) {
        return (itemCount + columnCount - 1) / columnCount;
    }

    private static String compactValue(LookRow row) {
        return row.value().isBlank() ? row.label() : row.value();
    }

    private static void drawLookText(Font font, String text, float x, float y, int color, PoseStack poseStack, MultiBufferSource buffer) {
        font.drawInBatch(text, x, y, color, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, NO_TEXT_BACKGROUND, LightTexture.FULL_BRIGHT);
    }

    private static void renderLookItem(ItemStack stack, int x, int y, PoseStack poseStack, MultiBufferSource buffer, Minecraft minecraft) {
        try {
            BakedModel model = minecraft.getItemRenderer().getModel(stack, minecraft.level, minecraft.player, 0);
            if (!model.isGui3d()) {
                renderFlatSpriteIcon(model.getParticleIcon(), x, y, poseStack, buffer);
                return;
            }
        } catch (Throwable ignored) {
            // Fall through to the normal renderer when model lookup is not available.
        }

        poseStack.pushPose();
        poseStack.translate(x + 8.0D, y + 8.0D, 0.0D);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(10.0F, 10.0F, 10.0F);
        minecraft.getItemRenderer().renderStatic(stack, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, poseStack, buffer, minecraft.level, 0);
        poseStack.popPose();
    }

    private static void renderFlatSpriteIcon(TextureAtlasSprite sprite, int x, int y, PoseStack poseStack, MultiBufferSource buffer) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0.0D);
        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(RenderType.text(sprite.atlasLocation()));
        vertex(consumer, matrix, 0, 16, sprite.getU0(), sprite.getV1());
        vertex(consumer, matrix, 16, 16, sprite.getU1(), sprite.getV1());
        vertex(consumer, matrix, 16, 0, sprite.getU1(), sprite.getV0());
        vertex(consumer, matrix, 0, 0, sprite.getU0(), sprite.getV0());
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float u, float v) {
        consumer.addVertex(matrix, x, y, 0.0F).setColor(255, 255, 255, 255).setUv(u, v).setLight(LightTexture.FULL_BRIGHT);
    }

    private static List<LookRow> compactRows(StoredMob stored, long now) {
        List<LookRow> rows = new ArrayList<>();
        boolean usesPixelmonDropHarvest = false;
        for (InteractionDefinition definition : stored.interactionProfile.definitions()) {
            if (isPixelmonDropHarvest(definition)) {
                usesPixelmonDropHarvest = true;
                rows.addAll(pixelmonDropRows(stored, now));
                continue;
            }
            LookRow row = compactRow(stored, definition, now);
            if (row != null) rows.add(row);
        }
        if (!usesPixelmonDropHarvest) for (DropRule rule : stored.dropProfile.drops()) rows.add(new LookRow(new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId())), "Drop", formatChance(rule.chance()), TEXT_WHITE, TEXT_YELLOW));
        if (rows.isEmpty()) {
            if (hasNativePixelmonDrops(stored)) rows.add(new LookRow(new ItemStack(Items.CHEST), "Pixelmon Drops", "Native", TEXT_WHITE, TEXT_GREEN));
            else rows.add(new LookRow(new ItemStack(Items.BARRIER), "No Drops", "", TEXT_GRAY, TEXT_GRAY));
        }
        return rows;
    }

    private static LookRow compactRow(StoredMob stored, InteractionDefinition definition, long now) {
        String method = definition.methodId().toString();
        if (method.equals(MobFarmBlockMod.id("breed").toString())) return new LookRow(breedIcon(definition), "Breed", "", TEXT_GREEN, TEXT_GREEN);
        if (method.equals(MobFarmBlockMod.id("milk").toString())) return new LookRow(new ItemStack(Items.MILK_BUCKET), "Milk", "", TEXT_WHITE, TEXT_WHITE);
        if (method.equals(MobFarmBlockMod.id("shear").toString())) return new LookRow(shearIcon(stored, definition), "Wool", readyValue(stored, definition.methodId(), now), TEXT_WHITE, readyColor(stored, definition.methodId(), now));
        if (method.equals(MobFarmBlockMod.id("egg").toString())) return new LookRow(new ItemStack(Items.EGG), "Egg", readyValue(stored, definition.methodId(), now), TEXT_WHITE, readyColor(stored, definition.methodId(), now));
        if (method.equals(MobFarmBlockMod.id("output_item").toString()) || method.equals(MobFarmBlockMod.id("harvest").toString())) return definition.outputItem().map(id -> new LookRow(new ItemStack(BuiltInRegistries.ITEM.get(id)), "Drop", readyValue(stored, definition.methodId(), now), TEXT_WHITE, readyColor(stored, definition.methodId(), now))).orElse(null);
        return null;
    }

    private static List<LookRow> expandedRows(StoredMob stored, long now) {
        List<LookRow> rows = new ArrayList<>();
        boolean usesPixelmonDropHarvest = false;
        for (InteractionDefinition definition : stored.interactionProfile.definitions()) {
            if (isPixelmonDropHarvest(definition)) {
                usesPixelmonDropHarvest = true;
                rows.addAll(pixelmonDropRows(stored, now));
                continue;
            }
            LookRow row = compactRow(stored, definition, now);
            if (row != null) rows.add(row);
        }
        if (!usesPixelmonDropHarvest) for (DropRule rule : stored.dropProfile.drops()) rows.add(new LookRow(new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId())), "Drop", formatChance(rule.chance()), TEXT_WHITE, TEXT_YELLOW));
        if (rows.isEmpty()) {
            if (hasNativePixelmonDrops(stored)) rows.add(new LookRow(new ItemStack(Items.CHEST), "Native Drops", "Pixelmon UI", TEXT_WHITE, TEXT_GREEN));
            else rows.add(new LookRow(new ItemStack(Items.BARRIER), "No Drops", "", TEXT_GRAY, TEXT_GRAY));
        }
        return rows;
    }

    private static boolean isPixelmonDropHarvest(InteractionDefinition definition) {
        return definition.methodId().equals(MobFarmBlockMod.id("pixelmon_drops"));
    }

    private static List<LookRow> pixelmonDropRows(StoredMob stored, long now) {
        List<LookRow> rows = new ArrayList<>();
        String value = readyValue(stored, MobFarmBlockMod.id("pixelmon_drops"), now);
        int color = readyColor(stored, MobFarmBlockMod.id("pixelmon_drops"), now);
        if (stored.dropProfile.drops().isEmpty()) {
            rows.add(new LookRow(new ItemStack(Items.CHEST), "Drops", value, TEXT_WHITE, color));
            return rows;
        }
        for (DropRule rule : stored.dropProfile.drops()) {
            rows.add(new LookRow(new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId())), "Drop", value, TEXT_WHITE, color));
        }
        return rows;
    }

    private static boolean hasNativePixelmonDrops(StoredMob stored) {
        return PixelmonEntityRenderCache.isPixelmonStored(stored) && stored.dropProfile.drops().isEmpty() && stored.dropProfileSource != null && stored.dropProfileSource.startsWith("pixelmon:");
    }

    private static ItemStack breedIcon(InteractionDefinition definition) {
        if (definition.item().isPresent()) return new ItemStack(BuiltInRegistries.ITEM.get(definition.item().get()));
        if (definition.itemTag().isPresent()) {
            String tag = definition.itemTag().get().toString();
            if ("mob_farm_block:chicken_breeding_items".equals(tag)) return new ItemStack(Items.WHEAT_SEEDS);
            if ("mob_farm_block:pig_breeding_items".equals(tag)) return new ItemStack(Items.CARROT);
        }
        return new ItemStack(Items.WHEAT);
    }

    private static ItemStack shearIcon(StoredMob stored, InteractionDefinition definition) {
        if (definition.outputItem().isPresent()) return new ItemStack(BuiltInRegistries.ITEM.get(definition.outputItem().get()));
        DyeColor color = DyeColor.byName(stored.state.getString("sheepColor"), DyeColor.WHITE);
        return new ItemStack(switch (color) {
            case BLACK -> Items.BLACK_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case LIME -> Items.LIME_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case ORANGE -> Items.ORANGE_WOOL;
            case PINK -> Items.PINK_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case RED -> Items.RED_WOOL;
            case WHITE -> Items.WHITE_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
        });
    }

    private static String readyValue(StoredMob stored, ResourceLocation action, long now) {
        long readyAt = stored.readyAtTicks.getOrDefault(action, 0L);
        if (now >= readyAt) return "Ready";
        long seconds = Math.max(1L, (readyAt - now + 19L) / 20L);
        return seconds + "s";
    }
    private static int readyColor(StoredMob stored, ResourceLocation action, long now) { return now >= stored.readyAtTicks.getOrDefault(action, 0L) ? TEXT_GREEN : TEXT_YELLOW; }
    private static String formatChance(double chance) { return Math.round(chance * 100.0D) + "%"; }
    private static int rowWidth(Font font, LookRow row, boolean showValue) { return 16 + font.width(row.label()) + (showValue && !row.value().isBlank() ? 6 + font.width(row.value()) : 0); }
    private static String mobLabel(StoredMob stored) { return stored.speciesId != null ? stored.speciesId.getPath() : stored.mobId.getPath(); }
    private record LookRow(ItemStack icon, String label, String value, int labelColor, int valueColor) {}
}
