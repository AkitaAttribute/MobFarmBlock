package com.akitaattribute.mobfarmblock.behavior;

import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;
import net.minecraft.world.InteractionResult;

public class GenericMobBehavior implements StoredMobBehavior {
    @Override public InteractionResult interact(MobFarmContext context) {
        InteractionMethodRegistry.applyReadyBreeding(context);
        for (InteractionDefinition definition : context.stored().interactionProfile.definitions()) {
            InteractionResult result = InteractionMethodRegistry.apply(context, definition);
            if (result.consumesAction()) return result;
        }
        return InteractionResult.PASS;
    }
    @Override public AttackResult attack(MobFarmContext context) {
        InteractionMethodRegistry.applyReadyBreeding(context);
        return BehaviorUtil.attack(context);
    }
}
