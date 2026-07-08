package com.akitaattribute.mobfarmblock.mob;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.integration.CobblemonIntegration;
import com.akitaattribute.mobfarmblock.integration.PixelmonIntegration;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;

/** Factory for compact profile capture. Full entity NBT is never read or persisted. */
public final class MobProfileFactory {
    public static StoredMob fromEntity(LivingEntity entity) {
        ResourceLocation mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        Optional<MobProfile> loaded = MobProfileRegistry.get(mobId);
        if (loaded.isPresent()) return fromProfile(loaded.get(), 1, "json");

        MobKind kind = detectKind(entity, mobId);
        DisplaySnapshot display = resolveDisplay(entity);
        ResourceLocation species = CobblemonIntegration.getSpeciesId(entity).or(() -> PixelmonIntegration.getSpeciesId(entity)).orElse(null);
        CobblemonRenderSnapshot cobblemonRenderSnapshot = null;
        if (kind == MobKind.COBBLEMON && species != null) {
            String variant = CobblemonIntegration.getDisplayKey(entity).orElse(species.toString());
            display = new DisplaySnapshot(display.entityTypeId(), display.textureId(), variant, display.colorKey(), display.baby(), display.scale());
            cobblemonRenderSnapshot = CobblemonIntegration.getRenderSnapshot(entity).orElse(null);
        }
        DropProfile drops = null;
        String source = "vanilla";
        if (kind == MobKind.COBBLEMON) {
            Optional<DropProfile> cobblemonDrops = CobblemonIntegration.resolveBattleDropProfile(entity);
            drops = cobblemonDrops.orElse(DropProfile.EMPTY);
            source = drops.drops().isEmpty() ? "cobblemon:unresolved_or_empty_drop_table" : "cobblemon:drop_table_reflection";
        } else {
            drops = PixelmonIntegration.resolveBattleDropProfile(entity).orElse(null);
            source = drops != null ? (kind == MobKind.PIXELMON ? "pixelmon" : "integration") : "vanilla";
            if (drops == null) {
                drops = DropProfileRegistry.get(mobId);
                if (drops.drops().isEmpty() && drops.xp().maxXp() <= 0) source = "empty";
            }
        }
        return new StoredMob(mobId, kind, 1, display, initialState(mobId, display), drops,
                builtInInteractions(mobId), new HashMap<>(), species, source, cobblemonRenderSnapshot);
    }

    public static StoredMob vanilla(ResourceLocation mobId, DisplaySnapshot display, long count) {
        return new StoredMob(mobId, MobKind.VANILLA_ENTITY, count, display, initialState(mobId, display), DropProfileRegistry.get(mobId), builtInInteractions(mobId), new HashMap<>(), null, "vanilla");
    }

    public static StoredMob fromProfile(MobProfile profile, long count) { return fromProfile(profile, count, "json"); }

    private static StoredMob fromProfile(MobProfile profile, long count, String source) {
        return new StoredMob(profile.mobId(), profile.kind(), count, profile.display(), initialState(profile.mobId(), profile.display()), profile.dropProfile(), profile.interactions(), new HashMap<>(), profile.speciesId().orElse(null), source);
    }

    public static InteractionProfile builtInInteractions(ResourceLocation mobId) {
        String id = mobId.toString();
        if ("minecraft:cow".equals(id) || "minecraft:mooshroom".equals(id)) return new InteractionProfile(List.of(breedItem("minecraft:wheat"), methodItem(MobFarmBlockMod.id("milk"), "minecraft:bucket", "minecraft:milk_bucket")));
        if ("minecraft:sheep".equals(id)) return new InteractionProfile(List.of(breedItem("minecraft:wheat"), method(MobFarmBlockMod.id("shear")), method(MobFarmBlockMod.id("dye"))));
        if ("minecraft:chicken".equals(id)) return new InteractionProfile(List.of(breedTag("mob_farm_block:chicken_breeding_items"), method(MobFarmBlockMod.id("egg"))));
        if ("minecraft:pig".equals(id)) return new InteractionProfile(List.of(breedTag("mob_farm_block:pig_breeding_items")));
        return InteractionProfile.EMPTY;
    }

    private static InteractionDefinition breedItem(String item) { return new InteractionDefinition(MobFarmBlockMod.id("breed"), Optional.of(ResourceLocation.parse(item)), Optional.empty(), Optional.empty(), 6000L, 1, 1, Optional.empty(), Map.of()); }
    private static InteractionDefinition breedTag(String tag) { return new InteractionDefinition(MobFarmBlockMod.id("breed"), Optional.empty(), Optional.of(ResourceLocation.parse(tag)), Optional.empty(), 6000L, 1, 1, Optional.empty(), Map.of()); }
    private static InteractionDefinition method(ResourceLocation method) { return new InteractionDefinition(method, Optional.empty(), Optional.empty(), Optional.empty(), 0L, 1, 1, Optional.empty(), Map.of()); }
    private static InteractionDefinition methodItem(ResourceLocation method, String item, String output) { return new InteractionDefinition(method, Optional.of(ResourceLocation.parse(item)), Optional.empty(), Optional.of(ResourceLocation.parse(output)), 0L, 1, 1, Optional.empty(), Map.of()); }

    private static MobKind detectKind(LivingEntity entity, ResourceLocation mobId) {
        if (CobblemonIntegration.isPokemonEntity(entity)) return MobKind.COBBLEMON;
        if (PixelmonIntegration.isPokemonEntity(entity)) return MobKind.PIXELMON;
        return "minecraft".equals(mobId.getNamespace()) ? MobKind.VANILLA_ENTITY : MobKind.CUSTOM;
    }

    private static DisplaySnapshot resolveDisplay(LivingEntity entity) { return CobblemonIntegration.resolveDisplay(entity).or(() -> PixelmonIntegration.resolveDisplay(entity)).orElseGet(() -> DisplaySnapshot.forEntity(entity)); }

    private static CompoundTag initialState(ResourceLocation mobId, DisplaySnapshot display) {
        CompoundTag state = new CompoundTag();
        if ("minecraft:sheep".equals(mobId.toString())) { state.putString("sheepColor", display.colorKey().isBlank() ? "white" : display.colorKey()); state.putLong("nextWoolReadyAt", 0L); }
        if (!display.variantKey().isBlank()) state.putString("displayVariantKey", display.variantKey());
        return state;
    }

    private MobProfileFactory() {}
}
