package com.akitaattribute.mobfarmblock.data;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.DropProfile;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;
import com.akitaattribute.mobfarmblock.mob.InteractionProfile;
import com.akitaattribute.mobfarmblock.mob.MobKind;
import com.akitaattribute.mobfarmblock.mob.MobProfile;
import com.akitaattribute.mobfarmblock.mob.MobProfileRegistry;
import com.akitaattribute.mobfarmblock.mob.XpProfile;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

/** Loads explicit data profiles for custom/Pokemon-style mobs without capturing real entity NBT. */
public class MobProfileReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();

    public MobProfileReloadListener() { super(GSON, "mob_profiles"); }

    @Override protected void apply(Map<ResourceLocation, JsonElement> objects, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, MobProfile> profiles = new HashMap<>();
        objects.forEach((resourceId, json) -> {
            try {
                MobProfile profile = parseProfile(GsonHelper.convertToJsonObject(json, "mob profile"));
                profiles.put(profile.mobId(), profile);
            } catch (Exception ex) {
                MobFarmBlockMod.LOGGER.warn("Skipping malformed mob profile {}", resourceId, ex);
            }
        });
        MobProfileRegistry.replaceAll(profiles);
    }

    private static MobProfile parseProfile(JsonObject json) {
        ResourceLocation mobId = ResourceLocation.parse(json.get("mob").getAsString());
        MobKind kind = json.has("kind") ? MobKind.valueOf(json.get("kind").getAsString()) : MobKind.CUSTOM;
        DisplaySnapshot display = parseDisplay(mobId, json);
        DropProfile dropProfile = new DropProfile(parseDrops(json), parseXp(json));
        InteractionProfile interactions = parseInteractions(json);
        Optional<ResourceLocation> speciesId = optionalIdentifier(json, "speciesId");
        Optional<ResourceLocation> breedingItem = optionalIdentifier(json, "breedingItem");
        long breedingCooldown = json.has("breedingCooldown") ? json.get("breedingCooldown").getAsLong() : 0L;
        return new MobProfile(mobId, kind, display, dropProfile, interactions, speciesId, breedingItem, breedingCooldown);
    }

    private static DisplaySnapshot parseDisplay(ResourceLocation mobId, JsonObject json) {
        JsonObject display = json.has("display") ? json.getAsJsonObject("display") : new JsonObject();
        ResourceLocation texture = optionalIdentifier(display, "texture").or(() -> optionalIdentifier(json, "displayTexture")).orElse(DisplaySnapshot.defaultTexture(mobId));
        return new DisplaySnapshot(mobId, texture, stringOrDefault(display, "variant", stringOrDefault(display, "variantKey", "")), stringOrDefault(display, "color", stringOrDefault(display, "colorKey", "")), display.has("baby") && display.get("baby").getAsBoolean(), display.has("scale") ? display.get("scale").getAsFloat() : 1.0F);
    }

    private static List<DropRule> parseDrops(JsonObject json) {
        JsonElement drops = json.has("battleLootDropProfile") ? json.get("battleLootDropProfile") : json.get("drops");
        List<DropRule> rules = new ArrayList<>();
        if (drops == null || !drops.isJsonArray()) return rules;
        for (JsonElement element : drops.getAsJsonArray()) {
            JsonObject drop = element.getAsJsonObject();
            rules.add(new DropRule(ResourceLocation.parse(drop.get("item").getAsString()), numberOrDefault(drop, "chance", 1.0).doubleValue(), numberOrDefault(drop, "min", 1).intValue(), numberOrDefault(drop, "max", 1).intValue(), !drop.has("affectedByLooting") || drop.get("affectedByLooting").getAsBoolean(), numberOrDefault(drop, "lootingChanceBonus", 0.0).doubleValue(), numberOrDefault(drop, "lootingMaxBonus", 1).intValue()));
        }
        return List.copyOf(rules);
    }

    private static XpProfile parseXp(JsonObject json) {
        if (!json.has("xp")) return XpProfile.NONE;
        JsonObject xp = json.getAsJsonObject("xp");
        return new XpProfile(numberOrDefault(xp, "min", 0).intValue(), numberOrDefault(xp, "max", 0).intValue());
    }

    private static InteractionProfile parseInteractions(JsonObject json) {
        List<InteractionDefinition> definitions = new ArrayList<>();
        if (json.has("interactions") && json.get("interactions").isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray("interactions")) definitions.add(parseInteraction(element.getAsJsonObject()));
        }
        if (json.has("breedingItem")) definitions.add(new InteractionDefinition(MobFarmBlockMod.id("breed"), optionalIdentifier(json, "breedingItem"), Optional.empty(), Optional.empty(), json.has("breedingCooldown") ? json.get("breedingCooldown").getAsLong() : 0L, 1, 1, Optional.empty(), Map.of()));
        return new InteractionProfile(List.copyOf(definitions));
    }

    private static InteractionDefinition parseInteraction(JsonObject json) {
        Map<String, String> parameters = new HashMap<>();
        if (json.has("parameters")) json.getAsJsonObject("parameters").entrySet().forEach(e -> parameters.put(e.getKey(), e.getValue().getAsString()));
        return new InteractionDefinition(ResourceLocation.parse(json.get("method").getAsString()), optionalIdentifier(json, "item"), optionalIdentifier(json, "itemTag"), optionalIdentifier(json, "output"), json.has("cooldownTicks") ? json.get("cooldownTicks").getAsLong() : 0L, numberOrDefault(json, "min", 1).intValue(), numberOrDefault(json, "max", 1).intValue(), json.has("stateKey") ? Optional.of(json.get("stateKey").getAsString()) : Optional.empty(), Map.copyOf(parameters));
    }

    private static Optional<ResourceLocation> optionalIdentifier(JsonObject json, String key) { return json.has(key) ? Optional.of(ResourceLocation.parse(json.get(key).getAsString())) : Optional.empty(); }
    private static String stringOrDefault(JsonObject json, String key, String fallback) { return json.has(key) ? json.get(key).getAsString() : fallback; }
    private static Number numberOrDefault(JsonObject json, String key, Number fallback) { return json.has(key) ? json.get(key).getAsNumber() : fallback; }
}
