package com.akitaattribute.mobfarmblock.mixin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akitaattribute.mobfarmblock.client.PixelmonEntityRenderCache;

import net.minecraft.resources.ResourceLocation;

/** Caches Pixelmon compatibility reflection results for the lifetime of the client session. */
@Mixin(value = PixelmonEntityRenderCache.class, remap = false)
public abstract class PixelmonRenderReflectionCacheMixin {
    private static final Map<String, Optional<Method>> MOB_FARM_BLOCK$METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Constructor<?>>> MOB_FARM_BLOCK$CONSTRUCTOR_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Field>> MOB_FARM_BLOCK$FIELD_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, Optional<Object>> MOB_FARM_BLOCK$SPECIES_CACHE = new ConcurrentHashMap<>();

    @Inject(method = "findMethod", at = @At("HEAD"), cancellable = true)
    private static void mobFarmBlock$cachedMethodLookup(Class<?> type, String name, Object[] args, CallbackInfoReturnable<Method> callback) {
        Optional<Method> cached = MOB_FARM_BLOCK$METHOD_CACHE.get(mobFarmBlock$memberKey(type, name, args));
        if (cached != null) callback.setReturnValue(cached.orElse(null));
    }

    @Inject(method = "findMethod", at = @At("RETURN"))
    private static void mobFarmBlock$rememberMethodLookup(Class<?> type, String name, Object[] args, CallbackInfoReturnable<Method> callback) {
        MOB_FARM_BLOCK$METHOD_CACHE.putIfAbsent(mobFarmBlock$memberKey(type, name, args), Optional.ofNullable(callback.getReturnValue()));
    }

    @Inject(method = "findConstructor", at = @At("HEAD"), cancellable = true)
    private static void mobFarmBlock$cachedConstructorLookup(Class<?> type, Object[] args, CallbackInfoReturnable<Constructor<?>> callback) {
        Optional<Constructor<?>> cached = MOB_FARM_BLOCK$CONSTRUCTOR_CACHE.get(mobFarmBlock$memberKey(type, "<init>", args));
        if (cached != null) callback.setReturnValue(cached.orElse(null));
    }

    @Inject(method = "findConstructor", at = @At("RETURN"))
    private static void mobFarmBlock$rememberConstructorLookup(Class<?> type, Object[] args, CallbackInfoReturnable<Constructor<?>> callback) {
        MOB_FARM_BLOCK$CONSTRUCTOR_CACHE.putIfAbsent(mobFarmBlock$memberKey(type, "<init>", args), Optional.ofNullable(callback.getReturnValue()));
    }

    @Inject(method = "findField", at = @At("HEAD"), cancellable = true)
    private static void mobFarmBlock$cachedFieldLookup(Class<?> type, String name, CallbackInfoReturnable<Field> callback) {
        Optional<Field> cached = MOB_FARM_BLOCK$FIELD_CACHE.get(type.getName() + "#" + name);
        if (cached != null) callback.setReturnValue(cached.orElse(null));
    }

    @Inject(method = "findField", at = @At("RETURN"))
    private static void mobFarmBlock$rememberFieldLookup(Class<?> type, String name, CallbackInfoReturnable<Field> callback) {
        MOB_FARM_BLOCK$FIELD_CACHE.putIfAbsent(type.getName() + "#" + name, Optional.ofNullable(callback.getReturnValue()));
    }

    @Inject(method = "findPixelmonSpeciesObject", at = @At("HEAD"), cancellable = true)
    private static void mobFarmBlock$cachedSpeciesLookup(ResourceLocation speciesId, CallbackInfoReturnable<Optional<Object>> callback) {
        if (speciesId == null) return;
        Optional<Object> cached = MOB_FARM_BLOCK$SPECIES_CACHE.get(speciesId);
        if (cached != null) callback.setReturnValue(cached);
    }

    @Inject(method = "findPixelmonSpeciesObject", at = @At("RETURN"))
    private static void mobFarmBlock$rememberSpeciesLookup(ResourceLocation speciesId, CallbackInfoReturnable<Optional<Object>> callback) {
        if (speciesId != null && callback.getReturnValue() != null) MOB_FARM_BLOCK$SPECIES_CACHE.putIfAbsent(speciesId, callback.getReturnValue());
    }

    private static String mobFarmBlock$memberKey(Class<?> type, String name, Object[] args) {
        StringBuilder key = new StringBuilder(type.getName()).append('#').append(name).append('(');
        if (args != null) {
            for (Object arg : args) key.append(arg == null ? "null" : arg.getClass().getName()).append(';');
        }
        return key.append(')').toString();
    }
}
