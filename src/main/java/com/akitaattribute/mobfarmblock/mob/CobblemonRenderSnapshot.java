package com.akitaattribute.mobfarmblock.mob;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/** Compact render-facing Cobblemon state captured from the live PokemonEntity. */
public record CobblemonRenderSnapshot(
        ResourceLocation speciesId,
        String form,
        List<String> aspects,
        int level,
        boolean shiny,
        String gender,
        float baseScale,
        float scaleModifier,
        String heldItem,
        String renderableDebug,
        String exposedSpecies,
        String exposedForm,
        String exposedAspects
) {
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("speciesId", speciesId.toString());
        tag.putString("form", form == null ? "" : form);
        tag.putString("aspects", String.join(",", aspects == null ? List.of() : aspects));
        tag.putInt("level", level);
        tag.putBoolean("shiny", shiny);
        tag.putString("gender", gender == null ? "" : gender);
        tag.putFloat("baseScale", baseScale);
        tag.putFloat("scaleModifier", scaleModifier);
        tag.putString("heldItem", heldItem == null ? "" : heldItem);
        tag.putString("renderableDebug", renderableDebug == null ? "" : renderableDebug);
        tag.putString("exposedSpecies", exposedSpecies == null ? "" : exposedSpecies);
        tag.putString("exposedForm", exposedForm == null ? "" : exposedForm);
        tag.putString("exposedAspects", exposedAspects == null ? "" : exposedAspects);
        return tag;
    }

    public static CobblemonRenderSnapshot fromNbt(CompoundTag tag) {
        String aspectsText = tag.getString("aspects");
        List<String> aspects = aspectsText.isBlank() ? List.of() : java.util.Arrays.stream(aspectsText.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        return new CobblemonRenderSnapshot(
                ResourceLocation.parse(tag.getString("speciesId")),
                tag.getString("form"),
                aspects,
                tag.getInt("level"),
                tag.getBoolean("shiny"),
                tag.getString("gender"),
                tag.contains("baseScale") ? tag.getFloat("baseScale") : 1.0F,
                tag.contains("scaleModifier") ? tag.getFloat("scaleModifier") : 1.0F,
                tag.getString("heldItem"),
                tag.getString("renderableDebug"),
                tag.getString("exposedSpecies"),
                tag.getString("exposedForm"),
                tag.getString("exposedAspects")
        );
    }
}
