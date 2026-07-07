package com.akitaattribute.mobfarmblock.data;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.DropProfile;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.InteractionProfile;
import com.akitaattribute.mobfarmblock.mob.MobKind;
import com.akitaattribute.mobfarmblock.mob.MobProfile;
import com.akitaattribute.mobfarmblock.mob.MobProfileRegistry;
import com.akitaattribute.mobfarmblock.mob.XpProfile;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

/** Loads explicit data profiles for custom/Pokemon-style mobs without capturing real entity NBT. */
public class MobProfileReloadListener implements IdentifiableResourceReloadListener {
    private static final Gson GSON = new Gson();

    @Override
    public Identifier getFabricId() {
        return MobFarmBlockMod.id("mob_profiles");
    }

    @Override
    public CompletableFuture<Void> reload(
            Synchronizer synchronizer,
            ResourceManager manager,
            Profiler prepareProfiler,
            Profiler applyProfiler,
            Executor prepareExecutor,
            Executor applyExecutor
    ) {
        return CompletableFuture.supplyAsync(() -> load(manager), prepareExecutor)
                .thenCompose(synchronizer::whenPrepared)
                .thenAcceptAsync(MobProfileRegistry::replaceAll, applyExecutor);
    }

    private Map<Identifier, MobProfile> load(ResourceManager manager) {
        Map<Identifier, MobProfile> profiles = new HashMap<>();
        manager.findResources("mob_profiles", id -> id.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                MobProfile profile = parseProfile(GSON.fromJson(reader, JsonObject.class));
                profiles.put(profile.mobId(), profile);
            } catch (Exception ex) {
                MobFarmBlockMod.LOGGER.warn("Skipping malformed mob profile {}", resourceId, ex);
            }
        });
        return profiles;
    }

    private static MobProfile parseProfile(JsonObject json) {
        Identifier mobId = Identifier.of(json.get("mob").getAsString());
        MobKind kind = json.has("kind") ? MobKind.valueOf(json.get("kind").getAsString()) : MobKind.CUSTOM;
        DisplaySnapshot display = parseDisplay(mobId, json);
        DropProfile dropProfile = new DropProfile(parseDrops(json), parseXp(json));
        InteractionProfile interactions = parseInteractions(json);
        Identifier speciesId = optionalIdentifier(json, "speciesId");
        Identifier breedingItem = optionalIdentifier(json, "breedingItem");
        long breedingCooldown = json.has("breedingCooldown") ? json.get("breedingCooldown").getAsLong() : 0L;

        return new MobProfile(mobId, kind, display, dropProfile, interactions, speciesId, breedingItem, breedingCooldown);
    }

    private static DisplaySnapshot parseDisplay(Identifier mobId, JsonObject json) {
        JsonObject display = json.has("display") ? json.getAsJsonObject("display") : new JsonObject();
        Identifier texture = optionalIdentifier(display, "texture");
        if (texture == null) {
            texture = optionalIdentifier(json, "displayTexture");
        }
        if (texture == null) {
            texture = DisplaySnapshot.defaultTexture(mobId);
        }
        return new DisplaySnapshot(
                mobId,
                texture,
                stringOrDefault(display, "variant", stringOrDefault(display, "variantKey", "")),
                stringOrDefault(display, "color", stringOrDefault(display, "colorKey", "")),
                display.has("baby") && display.get("baby").getAsBoolean(),
                display.has("scale") ? display.get("scale").getAsFloat() : 1.0F
        );
    }

    private static List<DropRule> parseDrops(JsonObject json) {
        JsonElement drops = json.has("battleLootDropProfile") ? json.get("battleLootDropProfile") : json.get("drops");
        List<DropRule> rules = new ArrayList<>();
        if (drops == null || !drops.isJsonArray()) {
            return rules;
        }
        for (JsonElement element : drops.getAsJsonArray()) {
            JsonObject drop = element.getAsJsonObject();
            rules.add(new DropRule(
                    Identifier.of(drop.get("item").getAsString()),
                    numberOrDefault(drop, "chance", 1.0).doubleValue(),
                    numberOrDefault(drop, "min", 1).intValue(),
                    numberOrDefault(drop, "max", 1).intValue(),
                    !drop.has("affectedByLooting") || drop.get("affectedByLooting").getAsBoolean(),
                    numberOrDefault(drop, "lootingChanceBonus", 0.0).doubleValue(),
                    numberOrDefault(drop, "lootingMaxBonus", 1).intValue()
            ));
        }
        return List.copyOf(rules);
    }

    private static XpProfile parseXp(JsonObject json) {
        if (!json.has("xp")) {
            return XpProfile.NONE;
        }
        JsonObject xp = json.getAsJsonObject("xp");
        return new XpProfile(numberOrDefault(xp, "min", 0).intValue(), numberOrDefault(xp, "max", 0).intValue());
    }

    private static InteractionProfile parseInteractions(JsonObject json) {
        Map<String, NbtCompound> interactions = new HashMap<>();
        if (json.has("interactions")) {
            JsonObject interactionJson = json.getAsJsonObject("interactions");
            for (Map.Entry<String, JsonElement> entry : interactionJson.entrySet()) {
                interactions.put(entry.getKey(), toNbt(entry.getValue().getAsJsonObject()));
            }
        }
        if (json.has("breedingItem") || json.has("breedingCooldown")) {
            NbtCompound breed = new NbtCompound();
            if (json.has("breedingItem")) {
                breed.putString("item", json.get("breedingItem").getAsString());
            }
            if (json.has("breedingCooldown")) {
                breed.putLong("cooldownTicks", json.get("breedingCooldown").getAsLong());
            }
            interactions.putIfAbsent("breed", breed);
        }
        return new InteractionProfile(Map.copyOf(interactions));
    }

    private static NbtCompound toNbt(JsonObject json) {
        NbtCompound nbt = new NbtCompound();
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement value = entry.getValue();
            if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
                nbt.putBoolean(entry.getKey(), value.getAsBoolean());
            } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                nbt.putDouble(entry.getKey(), value.getAsDouble());
            } else if (value.isJsonPrimitive()) {
                nbt.putString(entry.getKey(), value.getAsString());
            }
        }
        return nbt;
    }

    private static Identifier optionalIdentifier(JsonObject json, String key) {
        return json.has(key) ? Identifier.of(json.get(key).getAsString()) : null;
    }

    private static String stringOrDefault(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static Number numberOrDefault(JsonObject json, String key, Number fallback) {
        return json.has(key) ? json.get(key).getAsNumber() : fallback;
    }
}
