package com.akitaattribute.mobfarmblock.mixin;

import java.util.ArrayList;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.integration.PixelmonLootCaptureToolInjector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.level.ServerPlayer;

@Mixin(targets = "com.pixelmonmod.pixelmon.entities.pixelmon.drops.DropItemQueryList", remap = false)
public abstract class PixelmonDropItemQueryListMixin {
    static {
        MobFarmBlockMod.LOGGER.info("Mob Farm Pixelmon DropItemQueryList mixin class loaded");
    }

    @Inject(method = "register", at = @At("HEAD"), remap = false)
    private void mobFarmBlock$logRegisterHead(@Coerce Object pixelmonEntity, ArrayList<Object> drops, ServerPlayer player, CallbackInfo ci) {
        MobFarmBlockMod.LOGGER.info(
                "Mob Farm Pixelmon DropItemQueryList.register HEAD: sourceClass={} dropsBefore={} player={}",
                pixelmonEntity == null ? "null" : pixelmonEntity.getClass().getName(),
                drops == null ? -1 : drops.size(),
                player == null ? "null" : player.getGameProfile().getName()
        );
    }

    @Inject(method = "register", at = @At("TAIL"), remap = false)
    private void mobFarmBlock$appendCaptureTool(@Coerce Object pixelmonEntity, ArrayList<Object> drops, ServerPlayer player, CallbackInfo ci) {
        MobFarmBlockMod.LOGGER.info(
                "Mob Farm Pixelmon DropItemQueryList.register TAIL before append: sourceClass={} dropsBefore={} player={}",
                pixelmonEntity == null ? "null" : pixelmonEntity.getClass().getName(),
                drops == null ? -1 : drops.size(),
                player == null ? "null" : player.getGameProfile().getName()
        );
        PixelmonLootCaptureToolInjector.appendCaptureTool(pixelmonEntity, drops, player);
        MobFarmBlockMod.LOGGER.info(
                "Mob Farm Pixelmon DropItemQueryList.register TAIL after append: dropsAfter={}",
                drops == null ? -1 : drops.size()
        );
    }
}
