package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;

public record DropRule(
        Identifier itemId,
        double chance,
        int minCount,
        int maxCount,
        boolean affectedByLooting,
        double lootingChanceBonus,
        int lootingMaxBonus
) {
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("itemId", itemId.toString());
        nbt.putDouble("chance", chance);
        nbt.putInt("minCount", minCount);
        nbt.putInt("maxCount", maxCount);
        nbt.putBoolean("affectedByLooting", affectedByLooting);
        nbt.putDouble("lootingChanceBonus", lootingChanceBonus);
        nbt.putInt("lootingMaxBonus", lootingMaxBonus);
        return nbt;
    }

    public static DropRule fromNbt(NbtCompound nbt) {
        return new DropRule(
                Identifier.of(nbt.getString("itemId")),
                nbt.getDouble("chance"),
                nbt.getInt("minCount"),
                nbt.getInt("maxCount"),
                nbt.getBoolean("affectedByLooting"),
                nbt.getDouble("lootingChanceBonus"),
                nbt.getInt("lootingMaxBonus")
        );
    }
}
