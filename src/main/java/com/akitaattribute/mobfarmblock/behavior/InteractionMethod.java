package com.akitaattribute.mobfarmblock.behavior;

import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;
import net.minecraft.world.InteractionResult;

@FunctionalInterface
public interface InteractionMethod {
    InteractionResult apply(MobFarmContext context, InteractionDefinition definition);
}
