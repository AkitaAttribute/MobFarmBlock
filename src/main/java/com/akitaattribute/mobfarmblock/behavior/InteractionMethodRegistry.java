package com.akitaattribute.mobfarmblock.behavior;

import java.util.HashMap;
import java.util.Map;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class InteractionMethodRegistry {
    private static final Map<ResourceLocation, InteractionMethod> METHODS = new HashMap<>();

    public static final ResourceLocation BREED = MobFarmBlockMod.id("breed");
    public static final ResourceLocation MILK = MobFarmBlockMod.id("milk");
    public static final ResourceLocation SHEAR = MobFarmBlockMod.id("shear");
    public static final ResourceLocation DYE = MobFarmBlockMod.id("dye");
    public static final ResourceLocation EGG = MobFarmBlockMod.id("egg");
    public static final ResourceLocation OUTPUT_ITEM = MobFarmBlockMod.id("output_item");
    public static final ResourceLocation CHANGE_STATE = MobFarmBlockMod.id("change_state");

    public static void registerDefaults() {
        register(BREED, InteractionMethodRegistry::breed);
        register(MILK, InteractionMethodRegistry::milk);
        register(OUTPUT_ITEM, InteractionMethodRegistry::outputItem);
    }

    public static void register(ResourceLocation id, InteractionMethod method) { METHODS.put(id, method); }
    public static InteractionResult apply(MobFarmContext context, InteractionDefinition definition) {
        InteractionMethod method = METHODS.get(definition.methodId());
        if (method == null || !matchesHeldItem(context, definition)) return InteractionResult.PASS;
        return method.apply(context, definition);
    }

    private static boolean matchesHeldItem(MobFarmContext context, InteractionDefinition definition) {
        ItemStack held = context.heldItem();
        if (definition.item().isPresent() && !held.is(BuiltInRegistries.ITEM.get(definition.item().get()))) return false;
        if (definition.itemTag().isPresent()) {
            TagKey<Item> tag = TagKey.create(BuiltInRegistries.ITEM.key(), definition.itemTag().get());
            if (!held.is(tag)) return false;
        }
        return definition.item().isPresent() || definition.itemTag().isPresent();
    }

    private static InteractionResult breed(MobFarmContext context, InteractionDefinition definition) {
        context.heldItem().shrink(1);
        context.stored().count++;
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult milk(MobFarmContext context, InteractionDefinition definition) {
        if (!context.heldItem().is(Items.BUCKET)) return InteractionResult.PASS;
        context.heldItem().shrink(1);
        BehaviorUtil.output(context, new ItemStack(Items.MILK_BUCKET));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult outputItem(MobFarmContext context, InteractionDefinition definition) {
        if (definition.outputItem().isEmpty()) return InteractionResult.PASS;
        BehaviorUtil.output(context, new ItemStack(BuiltInRegistries.ITEM.get(definition.outputItem().get()), Math.max(1, definition.minCount())));
        return InteractionResult.SUCCESS;
    }

    private InteractionMethodRegistry() {}
}
