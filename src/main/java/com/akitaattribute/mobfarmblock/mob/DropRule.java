package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public record DropRule(
        ResourceLocation itemId,
        double chance,
        int minCount,
        int maxCount,
        boolean affectedByLooting,
        double lootingChanceBonus,
        int lootingMaxBonus
) {
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("itemId", itemId.toString());
        tag.putDouble("chance", chance);
        tag.putInt("minCount", minCount);
        tag.putInt("maxCount", maxCount);
        tag.putBoolean("affectedByLooting", affectedByLooting);
        tag.putDouble("lootingChanceBonus", lootingChanceBonus);
        tag.putInt("lootingMaxBonus", lootingMaxBonus);
        return tag;
    }

    public static DropRule fromNbt(CompoundTag tag) {
        return new DropRule(
                ResourceLocation.parse(tag.getString("itemId")),
                tag.getDouble("chance"),
                tag.getInt("minCount"),
                tag.getInt("maxCount"),
                tag.getBoolean("affectedByLooting"),
                tag.getDouble("lootingChanceBonus"),
                tag.getInt("lootingMaxBonus")
        );
    }
}
