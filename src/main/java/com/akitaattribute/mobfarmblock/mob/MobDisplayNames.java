package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.resources.ResourceLocation;

public final class MobDisplayNames {
    public static String mobName(StoredMob stored) {
        if (stored == null || stored.isEmpty()) return "Mob";
        ResourceLocation id = stored.speciesId != null ? stored.speciesId : stored.mobId;
        return prettyName(id == null ? "mob" : id.getPath());
    }

    public static String capturedName(StoredMob stored) {
        return "Captured " + mobName(stored);
    }

    public static String penName(StoredMob stored) {
        return mobName(stored) + " Pen";
    }

    public static String prettyName(String value) {
        if (value == null || value.isBlank()) return "Mob";
        String[] parts = value.replace('-', '_').split("_");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!result.isEmpty()) result.append(' ');
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) result.append(part.substring(1));
        }
        return result.isEmpty() ? value : result.toString();
    }

    private MobDisplayNames() {}
}
