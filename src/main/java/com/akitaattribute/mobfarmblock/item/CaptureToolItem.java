package com.akitaattribute.mobfarmblock.item;

import com.akitaattribute.mobfarmblock.mob.MobProfileFactory;
import com.akitaattribute.mobfarmblock.debug.MobFarmDebug;
import com.akitaattribute.mobfarmblock.debug.CobblemonDebugDumper;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class CaptureToolItem extends Item {
    private static final String STORED_MOB_KEY = "StoredMob";

    public CaptureToolItem(Properties properties) { super(properties); }

    @Override public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (!canCapture(target)) {
            player.displayClientMessage(Component.translatable("item.mob_farm_block.capture_tool.invalid"), true);
            MobFarmDebug.captureRejected(player, target, rejectionReason(target));
            return InteractionResult.FAIL;
        }

        StoredMob captured = MobProfileFactory.fromEntity(target);
        StoredMob existing = getStoredMob(stack);
        if (existing != null && !existing.isEmpty()) {
            if (captured.isUnknownCobblemonPokemon() || existing.isUnknownCobblemonPokemon() || !existing.isSameType(captured)) {
                player.displayClientMessage(Component.translatable("item.mob_farm_block.capture_tool.filled"), true);
                MobFarmDebug.captureRejected(player, target, "capture tool contains a different mob");
                return InteractionResult.FAIL;
            }
            existing.count += captured.count;
            setStoredMob(stack, existing);
        } else if (stack.getCount() > 1) {
            stack.shrink(1);
            ItemStack filled = new ItemStack(this);
            setStoredMob(filled, captured);
            if (!player.getInventory().add(filled)) player.drop(filled, false);
        } else {
            setStoredMob(stack, captured);
        }

        target.remove(Entity.RemovalReason.DISCARDED);
        player.displayClientMessage(Component.translatable("item.mob_farm_block.capture_tool.captured"), true);
        CobblemonDebugDumper.writeEntityDump(player, target, captured, "capture");
        if (!"cobblemon:pokemon".equals(captured.mobId.toString())) MobFarmDebug.captureSuccess(player, target, captured);
        return InteractionResult.SUCCESS;
    }

    private static boolean canCapture(LivingEntity entity) {
        return !(entity instanceof Player) && !(entity instanceof EnderDragon) && !(entity instanceof WitherBoss);
    }

    private static String rejectionReason(LivingEntity entity) {
        if (entity instanceof Player) return "players cannot be captured";
        if (entity instanceof EnderDragon || entity instanceof WitherBoss) return "boss-like entity excluded";
        return "unknown";
    }

    public static boolean hasStoredMob(ItemStack stack) {
        CompoundTag tag = getCustomNbt(stack);
        return tag != null && tag.contains(STORED_MOB_KEY);
    }

    public static StoredMob getStoredMob(ItemStack stack) {
        CompoundTag tag = getCustomNbt(stack);
        return tag == null || !tag.contains(STORED_MOB_KEY) ? null : StoredMob.fromNbt(tag.getCompound(STORED_MOB_KEY));
    }

    public static void setStoredMob(ItemStack stack, StoredMob stored) {
        CompoundTag tag = getOrCreateCustomNbt(stack);
        tag.put(STORED_MOB_KEY, stored.toNbt());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void clearStoredMob(ItemStack stack) {
        CompoundTag tag = getOrCreateCustomNbt(stack);
        tag.remove(STORED_MOB_KEY);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static CompoundTag getCustomNbt(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static CompoundTag getOrCreateCustomNbt(ItemStack stack) {
        CompoundTag tag = getCustomNbt(stack);
        return tag == null ? new CompoundTag() : tag;
    }
}
