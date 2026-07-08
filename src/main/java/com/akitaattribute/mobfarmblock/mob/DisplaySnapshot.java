package com.akitaattribute.mobfarmblock.mob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import java.lang.reflect.Method;
import net.minecraft.core.registries.BuiltInRegistries;

/** Display-only identity. It intentionally excludes UUID, position, AI, equipment, and full entity NBT. */
public record DisplaySnapshot(
        ResourceLocation entityTypeId,
        ResourceLocation textureId,
        String variantKey,
        String colorKey,
        boolean baby,
        float scale
) {
    public static final DisplaySnapshot EMPTY = new DisplaySnapshot(
            ResourceLocation.withDefaultNamespace("pig"),
            ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png"),
            "",
            "",
            false,
            1.0F
    );

    public static DisplaySnapshot forEntity(LivingEntity entity) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        String colorKey = entity instanceof Sheep sheep ? sheep.getColor().getName() : "";
        String variantKey = variantKey(entity);
        return new DisplaySnapshot(
                entityId,
                defaultTexture(entityId),
                variantKey,
                colorKey,
                entity.isBaby(),
                entity.isBaby() ? 0.5F : 1.0F
        );
    }

    private static String variantKey(LivingEntity entity) {
        StringBuilder key = new StringBuilder();
        appendNoArg(key, entity, "getVariant");
        appendNoArg(key, entity, "getVariantHolder");
        appendNoArg(key, entity, "getRabbitType");
        appendNoArg(key, entity, "getMushroomType");
        appendNoArg(key, entity, "getVariantAndMarkings");
        appendNoArg(key, entity, "getTypeVariant");
        appendNoArg(key, entity, "getVillagerData");
        return key.toString();
    }

    private static void appendNoArg(StringBuilder key, Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            if (method.getParameterCount() != 0) return;
            Object value = method.invoke(target);
            if (value != null) {
                if (!key.isEmpty()) key.append('|');
                key.append(name).append('=').append(value);
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    public static ResourceLocation defaultTexture(ResourceLocation entityId) {
        if (BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.SHEEP).equals(entityId)) return ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep.png");
        if (BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.COW).equals(entityId)) return ResourceLocation.withDefaultNamespace("textures/entity/cow/cow.png");
        if (BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.MOOSHROOM).equals(entityId)) return ResourceLocation.withDefaultNamespace("textures/entity/cow/red_mooshroom.png");
        if (BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG).equals(entityId)) return ResourceLocation.withDefaultNamespace("textures/entity/pig/pig.png");
        if (BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.CHICKEN).equals(entityId)) return ResourceLocation.withDefaultNamespace("textures/entity/chicken.png");
        if (BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.RABBIT).equals(entityId)) return ResourceLocation.withDefaultNamespace("textures/entity/rabbit/brown.png");
        return ResourceLocation.fromNamespaceAndPath(entityId.getNamespace(), "textures/entity/" + entityId.getPath() + ".png");
    }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("entityTypeId", entityTypeId.toString());
        tag.putString("textureId", textureId.toString());
        tag.putString("variantKey", variantKey);
        tag.putString("colorKey", colorKey);
        tag.putBoolean("baby", baby);
        tag.putFloat("scale", scale);
        return tag;
    }

    public static DisplaySnapshot fromNbt(CompoundTag tag) {
        return new DisplaySnapshot(
                ResourceLocation.parse(tag.getString("entityTypeId")),
                ResourceLocation.parse(tag.getString("textureId")),
                tag.getString("variantKey"),
                tag.getString("colorKey"),
                tag.getBoolean("baby"),
                tag.contains("scale") ? tag.getFloat("scale") : 1.0F
        );
    }
}
