package com.akitaattribute.mobfarmblock.mixin;

import java.util.ArrayList;

import com.akitaattribute.mobfarmblock.integration.PixelmonLootCaptureToolInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

@Mixin(targets = "com.pixelmonmod.pixelmon.api.drops.DropItemQueryList", remap = false)
public abstract class PixelmonDropItemQueryListMixin {
    @Inject(method = "register", at = @At("TAIL"), remap = false)
    private void mobFarmBlock$appendCaptureTool(@Coerce Object pixelmonEntity, ArrayList<Object> drops, ServerPlayer player, CallbackInfo ci) {
        PixelmonLootCaptureToolInjector.appendCaptureTool(pixelmonEntity, drops, player);
    }
}
