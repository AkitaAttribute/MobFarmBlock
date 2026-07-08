package com.akitaattribute.mobfarmblock.item;

import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class MobFarmBlockItemData {
    private static final String STORED_MOB_KEY = "StoredMob";

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

    private static CompoundTag getCustomNbt(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static CompoundTag getOrCreateCustomNbt(ItemStack stack) {
        CompoundTag tag = getCustomNbt(stack);
        return tag == null ? new CompoundTag() : tag;
    }

    private MobFarmBlockItemData() {}
}
