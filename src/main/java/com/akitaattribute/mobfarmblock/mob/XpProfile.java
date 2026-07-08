package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.nbt.CompoundTag;

public record XpProfile(int minXp, int maxXp) {
    public static final XpProfile NONE = new XpProfile(0, 0);

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minXp", minXp);
        tag.putInt("maxXp", maxXp);
        return tag;
    }

    public static XpProfile fromNbt(CompoundTag tag) {
        return new XpProfile(tag.getInt("minXp"), tag.getInt("maxXp"));
    }
}
