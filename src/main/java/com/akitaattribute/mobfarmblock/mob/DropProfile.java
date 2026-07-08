package com.akitaattribute.mobfarmblock.mob;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record DropProfile(List<DropRule> drops, XpProfile xp) {
    public static final DropProfile EMPTY = new DropProfile(List.of(), XpProfile.NONE);

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        drops.forEach(drop -> list.add(drop.toNbt()));
        tag.put("drops", list);
        tag.put("xp", xp.toNbt());
        return tag;
    }

    public static DropProfile fromNbt(CompoundTag tag) {
        List<DropRule> drops = new ArrayList<>();
        for (Tag element : tag.getList("drops", Tag.TAG_COMPOUND)) {
            drops.add(DropRule.fromNbt((CompoundTag) element));
        }
        XpProfile xp = tag.contains("xp") ? XpProfile.fromNbt(tag.getCompound("xp")) : XpProfile.NONE;
        return new DropProfile(List.copyOf(drops), xp);
    }
}
