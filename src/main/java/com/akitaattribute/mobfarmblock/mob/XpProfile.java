package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.nbt.NbtCompound;

public record XpProfile(int minXp, int maxXp) {
    public static final XpProfile NONE = new XpProfile(0, 0);

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("minXp", minXp);
        nbt.putInt("maxXp", maxXp);
        return nbt;
    }

    public static XpProfile fromNbt(NbtCompound nbt) {
        return new XpProfile(nbt.getInt("minXp"), nbt.getInt("maxXp"));
    }
}
