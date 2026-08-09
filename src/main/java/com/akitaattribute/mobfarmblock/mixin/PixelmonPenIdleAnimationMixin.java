package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Field;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.akitaattribute.mobfarmblock.client.ClientEntityRenderCache;
import com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Lets in-world Pixelmon pen renders advance their renderer animation clock without
 * allowing the cached display entity to move or think like a normal world entity.
 */
@Mixin(value = MobFarmBlockEntityRenderer.class, remap = false)
public abstract class PixelmonPenIdleAnimationMixin {
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lcom/akitaattribute/mobfarmblock/client/ClientEntityRenderCache;freezeForRender(Lnet/minecraft/world/entity/Entity;)V"
        )
    )
    private void mobFarmBlock$animatePixelmonPenEntity(Entity entity) {
        if (!isPixelmonEntity(entity)) {
            ClientEntityRenderCache.freezeForRender(entity);
            return;
        }
        applyPixelmonIdleClock(entity);
    }

    private static boolean isPixelmonEntity(Entity entity) {
        return entity != null && entity.getClass().getName().startsWith("com.pixelmonmod.pixelmon.");
    }

    private static void applyPixelmonIdleClock(Entity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        int tick = (int) (minecraft.level.getGameTime() & 0x3FFFFFFFL);
        entity.tickCount = tick;
        setInt(entity, "tickCount", tick);
        setInt(entity, "ticksExisted", tick);

        if (entity instanceof LivingEntity living) {
            living.yBodyRotO = living.yBodyRot;
            living.yHeadRotO = living.yHeadRot;
            living.yRotO = living.getYRot();
            living.xRotO = living.getXRot();
        }
    }

    private static void setInt(Object target, String fieldName, int value) {
        if (target == null) return;
        Class<?> type = target.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                if (field.getType() == int.class) field.setInt(target, value);
                return;
            } catch (Throwable ignored) {
                type = type.getSuperclass();
            }
        }
    }
}
