package com.akitaattribute.mobfarmblock.mob;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record DropProfile(List<DropRule> drops, XpProfile xp) {
    public static final DropProfile EMPTY = new DropProfile(List.of(), XpProfile.NONE);

    public DropProfile mergeDiscovered(DropProfile other) {
        if (other == null || other.drops().isEmpty() && other.xp().maxXp() <= 0) return this;
        if (drops().isEmpty() && xp().maxXp() <= 0) return other;

        Map<String, DropRule> merged = new LinkedHashMap<>();
        for (DropRule rule : drops()) putMergedRule(merged, rule);
        for (DropRule rule : other.drops()) putMergedRule(merged, rule);

        int minXp = Math.max(xp().minXp(), other.xp().minXp());
        int maxXp = Math.max(xp().maxXp(), other.xp().maxXp());
        return new DropProfile(List.copyOf(merged.values()), new XpProfile(minXp, maxXp));
    }

    private static void putMergedRule(Map<String, DropRule> merged, DropRule incoming) {
        if (incoming == null || incoming.itemId() == null) return;
        String key = incoming.itemId().toString();
        DropRule existing = merged.get(key);
        if (existing == null) {
            merged.put(key, incoming);
            return;
        }
        merged.put(key, new DropRule(
                incoming.itemId(),
                Math.max(existing.chance(), incoming.chance()),
                Math.min(existing.minCount(), incoming.minCount()),
                Math.max(existing.maxCount(), incoming.maxCount()),
                existing.affectedByLooting() || incoming.affectedByLooting(),
                Math.max(existing.lootingChanceBonus(), incoming.lootingChanceBonus()),
                Math.max(existing.lootingMaxBonus(), incoming.lootingMaxBonus())
        ));
    }

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
