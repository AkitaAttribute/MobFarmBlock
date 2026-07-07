package com.akitaattribute.mobfarmblock.item;

import com.akitaattribute.mobfarmblock.mob.MobProfileFactory;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class CaptureToolItem extends Item {
    private static final String STORED_MOB_KEY = "StoredMob";

    public CaptureToolItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user, LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient) {
            return ActionResult.SUCCESS;
        }
        if (hasStoredMob(stack)) {
            user.sendMessage(Text.translatable("item.mob_farm_block.capture_tool.filled"), true);
            return ActionResult.FAIL;
        }
        if (!canCapture(entity)) {
            user.sendMessage(Text.translatable("item.mob_farm_block.capture_tool.invalid"), true);
            return ActionResult.FAIL;
        }

        StoredMob stored = MobProfileFactory.fromEntity(entity);
        setStoredMob(stack, stored);
        entity.remove(Entity.RemovalReason.DISCARDED);
        user.sendMessage(Text.translatable("item.mob_farm_block.capture_tool.captured"), true);
        return ActionResult.SUCCESS;
    }

    private static boolean canCapture(LivingEntity entity) {
        if (entity instanceof PlayerEntity) {
            return false;
        }
        if (!(entity instanceof MobEntity)) {
            return false;
        }
        return !(entity instanceof EnderDragonEntity);
    }

    public static boolean hasStoredMob(ItemStack stack) {
        NbtCompound nbt = getCustomNbt(stack);
        return nbt != null && nbt.contains(STORED_MOB_KEY);
    }

    public static StoredMob getStoredMob(ItemStack stack) {
        NbtCompound nbt = getCustomNbt(stack);
        if (nbt == null || !nbt.contains(STORED_MOB_KEY)) {
            return null;
        }
        return StoredMob.fromNbt(nbt.getCompound(STORED_MOB_KEY));
    }

    public static void setStoredMob(ItemStack stack, StoredMob stored) {
        NbtCompound nbt = getOrCreateCustomNbt(stack);
        nbt.put(STORED_MOB_KEY, stored.toNbt());
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    public static void clearStoredMob(ItemStack stack) {
        NbtCompound nbt = getOrCreateCustomNbt(stack);
        nbt.remove(STORED_MOB_KEY);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static NbtCompound getCustomNbt(ItemStack stack) {
        NbtComponent component = stack.get(DataComponentTypes.CUSTOM_DATA);
        return component == null ? null : component.copyNbt();
    }

    private static NbtCompound getOrCreateCustomNbt(ItemStack stack) {
        NbtCompound nbt = getCustomNbt(stack);
        return nbt == null ? new NbtCompound() : nbt;
    }
}
