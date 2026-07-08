package com.akitaattribute.mobfarmblock.client;

import java.util.HashMap;
import java.util.Map;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

public final class ClientEntityRenderCache {
    private static final Map<String, Entity> CACHE = new HashMap<>();
    private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

    public static Entity getOrCreate(StoredMob stored) {
        if (stored == null || stored.isEmpty() || Minecraft.getInstance().level == null) return null;
        String key = stored.mobId + "|" + stored.display.variantKey() + "|" + stored.display.colorKey() + "|" + stored.display.baby();
        Entity cached = CACHE.get(key);
        if (cached != null) return cached;
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(stored.mobId);
        if (type == null || type == EntityType.PIG && !stored.mobId.equals(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PIG))) {
            warnOnce("entity type could not be resolved: " + stored.mobId);
            return null;
        }
        Entity entity = type.create(Minecraft.getInstance().level);
        if (entity == null) {
            warnOnce("dummy entity could not be created: " + stored.mobId);
            return null;
        }
        applyDisplay(entity, stored);
        CACHE.put(key, entity);
        return entity;
    }

    private static void applyDisplay(Entity entity, StoredMob stored) {
        entity.setYRot(0.0F);
        entity.setXRot(0.0F);
        if (entity instanceof Sheep sheep && !stored.display.colorKey().isBlank()) {
            sheep.setColor(DyeColor.byName(stored.display.colorKey(), DyeColor.WHITE));
        }
        if (entity instanceof AgeableMob ageable) {
            ageable.setBaby(stored.display.baby());
        }
    }

    private static void warnOnce(String message) {
        if (WARNED.add(message)) MobFarmBlockMod.LOGGER.debug("Mob Farm Block render fallback: {}", message);
    }

    private ClientEntityRenderCache() {}
}
