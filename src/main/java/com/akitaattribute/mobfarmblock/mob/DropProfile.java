package com.akitaattribute.mobfarmblock.mob;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

public record DropProfile(List<DropRule> drops, XpProfile xp) {
    public static final DropProfile EMPTY = new DropProfile(List.of(), XpProfile.NONE);

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        NbtList dropsNbt = new NbtList();
        for (DropRule drop : drops) {
            dropsNbt.add(drop.toNbt());
        }
        nbt.put("drops", dropsNbt);
        nbt.put("xp", xp.toNbt());
        return nbt;
    }

    public static DropProfile fromNbt(NbtCompound nbt) {
        List<DropRule> drops = new ArrayList<>();
        for (var element : nbt.getList("drops", NbtCompound.COMPOUND_TYPE)) {
            drops.add(DropRule.fromNbt((NbtCompound) element));
        }
        XpProfile xp = nbt.contains("xp") ? XpProfile.fromNbt(nbt.getCompound("xp")) : XpProfile.NONE;
        return new DropProfile(List.copyOf(drops), xp);
    }
}
