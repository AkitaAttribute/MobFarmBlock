package com.akitaattribute.mobfarmblock.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akitaattribute.mobfarmblock.client.PixelmonEntityRenderCache;
import com.akitaattribute.mobfarmblock.client.PixelmonPayloadPreparationCache;
import com.akitaattribute.mobfarmblock.config.MobFarmConfig;
import com.akitaattribute.mobfarmblock.mob.PixelmonRenderSnapshot;
import com.akitaattribute.mobfarmblock.mob.StoredMob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.entity.Entity;

/** Keeps string-NBT parsing off the render thread while preserving the known-good entity.load path. */
@Mixin(value = PixelmonEntityRenderCache.class, remap = false)
public abstract class PixelmonRenderPreparationMixin {
    @Inject(method = "getOrCreate", at = @At("HEAD"), cancellable = true)
    private static void mobFarmBlock$prepareSavedPayload(StoredMob stored, CallbackInfoReturnable<Entity> callback) {
        if (stored == null) return;
        PixelmonRenderSnapshot snapshot = stored.pixelmonRenderSnapshot;
        if (snapshot == null || !snapshot.hasEntityPayload()) return;

        MobFarmConfig.PixelmonRenderReplayMode mode = MobFarmConfig.PIXELMON_RENDER_REPLAY_MODE.get();
        if (mode != MobFarmConfig.PixelmonRenderReplayMode.ENTITY_PAYLOAD_ONLY
                && mode != MobFarmConfig.PixelmonRenderReplayMode.ENTITY_PAYLOAD_SIZE_AFTER_LOAD
                && mode != MobFarmConfig.PixelmonRenderReplayMode.HYBRID_ALL) return;

        if (!PixelmonPayloadPreparationCache.ensurePrepared(snapshot)) callback.setReturnValue(null);
    }

    @Redirect(
            method = "createEntityFromSavedPayload",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/TagParser;parseTag(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;"
            )
    )
    private static CompoundTag mobFarmBlock$usePreparedPayload(String payload) {
        return PixelmonRenderSnapshot.cachedEntityPayload(payload).orElseGet(() -> {
            try {
                return TagParser.parseTag(payload);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        });
    }
}
