package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

/** Display-only identity. It intentionally excludes UUID, position, AI, equipment, and full entity NBT. */
public record DisplaySnapshot(
        Identifier entityTypeId,
        Identifier textureId,
        String variantKey,
        String colorKey,
        boolean baby,
        float scale
) {
    public static final DisplaySnapshot EMPTY = new DisplaySnapshot(
            Identifier.of("minecraft:pig"),
            Identifier.of("minecraft:textures/entity/pig/pig.png"),
            "",
            "",
            false,
            1.0F
    );

    public static DisplaySnapshot forEntity(net.minecraft.entity.LivingEntity entity) {
        Identifier entityId = Registries.ENTITY_TYPE.getId(entity.getType());
        String colorKey = "";
        if (entity instanceof SheepEntity sheep) {
            colorKey = sheep.getColor().getName();
        }
        return new DisplaySnapshot(
                entityId,
                defaultTexture(entityId),
                "",
                colorKey,
                entity.isBaby(),
                entity.isBaby() ? 0.5F : 1.0F
        );
    }

    public static Identifier defaultTexture(Identifier entityId) {
        if (EntityType.SHEEP.getRegistryEntry().matchesId(entityId)) {
            return Identifier.of("minecraft:textures/entity/sheep/sheep.png");
        }
        if (EntityType.COW.getRegistryEntry().matchesId(entityId)) {
            return Identifier.of("minecraft:textures/entity/cow/cow.png");
        }
        if (EntityType.MOOSHROOM.getRegistryEntry().matchesId(entityId)) {
            return Identifier.of("minecraft:textures/entity/cow/red_mooshroom.png");
        }
        if (EntityType.PIG.getRegistryEntry().matchesId(entityId)) {
            return Identifier.of("minecraft:textures/entity/pig/pig.png");
        }
        if (EntityType.CHICKEN.getRegistryEntry().matchesId(entityId)) {
            return Identifier.of("minecraft:textures/entity/chicken.png");
        }
        if (EntityType.RABBIT.getRegistryEntry().matchesId(entityId)) {
            return Identifier.of("minecraft:textures/entity/rabbit/brown.png");
        }
        return Identifier.of(entityId.getNamespace(), "textures/entity/" + entityId.getPath() + ".png");
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("entityTypeId", entityTypeId.toString());
        nbt.putString("textureId", textureId.toString());
        nbt.putString("variantKey", variantKey);
        nbt.putString("colorKey", colorKey);
        nbt.putBoolean("baby", baby);
        nbt.putFloat("scale", scale);
        return nbt;
    }

    public static DisplaySnapshot fromNbt(NbtCompound nbt) {
        return new DisplaySnapshot(
                Identifier.of(nbt.getString("entityTypeId")),
                Identifier.of(nbt.getString("textureId")),
                nbt.getString("variantKey"),
                nbt.getString("colorKey"),
                nbt.getBoolean("baby"),
                nbt.contains("scale") ? nbt.getFloat("scale") : 1.0F
        );
    }
}
