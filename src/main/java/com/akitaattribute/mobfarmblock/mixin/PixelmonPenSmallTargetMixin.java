package com.akitaattribute.mobfarmblock.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akitaattribute.mobfarmblock.client.MobFarmBlockEntityRenderer;

@Mixin(value = MobFarmBlockEntityRenderer.class, remap = false)
public abstract class PixelmonPenSmallTargetMixin {
    @ModifyConstant(method = "placedEntityScale", constant = @Constant(floatValue = 0.60F, ordinal = 0))
    private static float mobFarmBlock$tighterPixelmonPenSmallTarget(float original) {
        return 0.40F;
    }
}
