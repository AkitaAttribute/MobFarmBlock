package com.akitaattribute.mobfarmblock.behavior;
import net.minecraft.item.Items; import net.minecraft.util.ActionResult;
public class CowBehavior extends GenericMobBehavior { public ActionResult interact(MobFarmContext c){var h=c.heldItem(); if(h.isOf(Items.BUCKET)){h.decrement(1); BehaviorUtil.output(c,Items.MILK_BUCKET.getDefaultStack()); return ActionResult.SUCCESS;} if(h.isOf(Items.WHEAT)){h.decrement(1); c.stored().count++; return ActionResult.SUCCESS;} return ActionResult.PASS;} }
