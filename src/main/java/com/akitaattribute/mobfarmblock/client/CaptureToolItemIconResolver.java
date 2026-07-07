package com.akitaattribute.mobfarmblock.client;

import net.minecraft.resources.ResourceLocation;

/** Static model-index fallback until a full dynamic BEWLR texture-quad renderer is added. */
public final class CaptureToolItemIconResolver {
    public static float modelIndex(ResourceLocation mobId) {
        return switch (mobId.toString()) {
            case "minecraft:cow" -> 1.0F;
            case "minecraft:sheep" -> 2.0F;
            case "minecraft:pig" -> 3.0F;
            case "minecraft:chicken" -> 4.0F;
            case "minecraft:rabbit" -> 5.0F;
            case "minecraft:mooshroom" -> 6.0F;
            default -> 7.0F;
        };
    }
    private CaptureToolItemIconResolver() {}
}
