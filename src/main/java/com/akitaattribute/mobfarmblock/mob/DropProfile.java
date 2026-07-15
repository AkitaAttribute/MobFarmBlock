package com.akitaattribute.mobfarmblock.mob;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

public record DropProfile(List<DropRule> drops, XpProfile xp, int observations,
                          Map<ResourceLocation, Integer> observedDropCounts,
                          Map<ResourceLocation, DropRule> observedDropPrototypes) {
    public static final DropProfile EMPTY = new DropProfile(List.of(), XpProfile.NONE);

    public DropProfile(List<DropRule> drops, XpProfile xp) {
        this(drops, xp, 0, Map.of(), Map.of());
    }

    public DropProfile {
        drops = drops == null ? List.of() : List.copyOf(drops);
        xp = xp == null ? XpProfile.NONE : xp;
        observedDropCounts = observedDropCounts == null ? Map.of() : Map.copyOf(observedDropCounts);
        observedDropPrototypes = observedDropPrototypes == null ? Map.of() : Map.copyOf(observedDropPrototypes);
    }

    public DropProfile mergeDiscovered(DropProfile other) {
        if (other == null || other.drops().isEmpty() && other.xp().maxXp() <= 0) return this;
        if (drops().isEmpty() && xp().maxXp() <= 0) return other;

        DropProfile left = asObservedProfile(this);
        DropProfile right = asObservedProfile(other);
        int totalObservations = left.observations() + right.observations();

        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        addCounts(counts, left.observedDropCounts());
        addCounts(counts, right.observedDropCounts());

        Map<ResourceLocation, DropRule> prototypes = new LinkedHashMap<>();
        addPrototypes(prototypes, left.observedDropPrototypes());
        addPrototypes(prototypes, right.observedDropPrototypes());

        List<DropRule> adjustedRules = new ArrayList<>();
        for (Map.Entry<ResourceLocation, DropRule> entry : prototypes.entrySet()) {
            ResourceLocation itemId = entry.getKey();
            DropRule prototype = entry.getValue();
            int seen = counts.getOrDefault(itemId, 0);
            if (seen <= 0 || totalObservations <= 0) continue;
            double observedChance = Math.max(0.0D, Math.min(1.0D, prototype.chance() * ((double) seen / (double) totalObservations)));
            adjustedRules.add(new DropRule(
                    itemId,
                    observedChance,
                    prototype.minCount(),
                    prototype.maxCount(),
                    prototype.affectedByLooting(),
                    prototype.lootingChanceBonus(),
                    prototype.lootingMaxBonus()
            ));
        }

        int minXp = Math.max(xp().minXp(), other.xp().minXp());
        int maxXp = Math.max(xp().maxXp(), other.xp().maxXp());
        return new DropProfile(adjustedRules, new XpProfile(minXp, maxXp), totalObservations, counts, prototypes);
    }

    private static DropProfile asObservedProfile(DropProfile profile) {
        if (profile == null) return EMPTY;
        if (profile.observations() > 0) return profile;
        if (profile.drops().isEmpty()) return profile;

        Map<ResourceLocation, Integer> counts = new LinkedHashMap<>();
        Map<ResourceLocation, DropRule> prototypes = new LinkedHashMap<>();
        for (DropRule rule : profile.drops()) {
            if (rule == null || rule.itemId() == null) continue;
            counts.put(rule.itemId(), 1);
            prototypes.put(rule.itemId(), rule);
        }
        return new DropProfile(profile.drops(), profile.xp(), counts.isEmpty() ? 0 : 1, counts, prototypes);
    }

    private static void addCounts(Map<ResourceLocation, Integer> target, Map<ResourceLocation, Integer> source) {
        source.forEach((itemId, count) -> target.merge(itemId, count, Integer::sum));
    }

    private static void addPrototypes(Map<ResourceLocation, DropRule> target, Map<ResourceLocation, DropRule> source) {
        for (Map.Entry<ResourceLocation, DropRule> entry : source.entrySet()) {
            target.merge(entry.getKey(), entry.getValue(), DropProfile::mergePrototypeRule);
        }
    }

    private static DropRule mergePrototypeRule(DropRule existing, DropRule incoming) {
        if (existing == null) return incoming;
        if (incoming == null) return existing;
        return new DropRule(
                incoming.itemId(),
                Math.max(existing.chance(), incoming.chance()),
                Math.min(existing.minCount(), incoming.minCount()),
                Math.max(existing.maxCount(), incoming.maxCount()),
                existing.affectedByLooting() || incoming.affectedByLooting(),
                Math.max(existing.lootingChanceBonus(), incoming.lootingChanceBonus()),
                Math.max(existing.lootingMaxBonus(), incoming.lootingMaxBonus())
        );
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        drops.forEach(drop -> list.add(drop.toNbt()));
        tag.put("drops", list);
        tag.put("xp", xp.toNbt());
        tag.putInt("observations", observations);

        CompoundTag observedCounts = new CompoundTag();
        observedDropCounts.forEach((itemId, count) -> observedCounts.putInt(itemId.toString(), count));
        tag.put("observedDropCounts", observedCounts);

        ListTag prototypes = new ListTag();
        observedDropPrototypes.values().forEach(rule -> prototypes.add(rule.toNbt()));
        tag.put("observedDropPrototypes", prototypes);
        return tag;
    }

    public static DropProfile fromNbt(CompoundTag tag) {
        List<DropRule> drops = new ArrayList<>();
        for (Tag element : tag.getList("drops", Tag.TAG_COMPOUND)) {
            drops.add(DropRule.fromNbt((CompoundTag) element));
        }
        XpProfile xp = tag.contains("xp") ? XpProfile.fromNbt(tag.getCompound("xp")) : XpProfile.NONE;
        int observations = tag.getInt("observations");

        Map<ResourceLocation, Integer> observedCounts = new LinkedHashMap<>();
        CompoundTag countsTag = tag.getCompound("observedDropCounts");
        for (String key : countsTag.getAllKeys()) observedCounts.put(ResourceLocation.parse(key), countsTag.getInt(key));

        Map<ResourceLocation, DropRule> prototypes = new LinkedHashMap<>();
        for (Tag element : tag.getList("observedDropPrototypes", Tag.TAG_COMPOUND)) {
            DropRule rule = DropRule.fromNbt((CompoundTag) element);
            prototypes.put(rule.itemId(), rule);
        }
        return new DropProfile(drops, xp, observations, observedCounts, prototypes);
    }
}
