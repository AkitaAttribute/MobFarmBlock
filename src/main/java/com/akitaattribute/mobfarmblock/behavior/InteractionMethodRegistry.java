package com.akitaattribute.mobfarmblock.behavior;

import java.util.HashMap;
import java.util.Map;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.debug.MobFarmDebug;
import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
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
        register(SHEAR, InteractionMethodRegistry::shear);
        register(DYE, InteractionMethodRegistry::dye);
        register(EGG, InteractionMethodRegistry::egg);
        register(OUTPUT_ITEM, InteractionMethodRegistry::outputItem);
        register(CHANGE_STATE, InteractionMethodRegistry::changeState);
    }

    public static void register(ResourceLocation id, InteractionMethod method) { METHODS.put(id, method); }
    public static InteractionResult apply(MobFarmContext context, InteractionDefinition definition) {
        InteractionMethod method = METHODS.get(definition.methodId());
        if (method == null || !matchesHeldItem(context, definition)) return InteractionResult.PASS;
        return method.apply(context, definition);
    }

    public static void applyReadyBreeding(MobFarmContext context) {
        CompoundTag state = context.stored().state;
        long now = context.level().getGameTime();
        if (state.getBoolean("breedingCycleActive") && now >= state.getLong("breedingReadyAt")) {
            int fed = state.getInt("breedingFedCount");
            context.stored().count += fed;
            state.putBoolean("breedingCycleActive", false);
            state.putInt("breedingFedCount", 0);
            state.putLong("breedingBaseCount", context.stored().count);
            state.putLong("breedingReadyAt", 0L);
        }
    }

    private static boolean matchesHeldItem(MobFarmContext context, InteractionDefinition definition) {
        if (definition.methodId().equals(SHEAR)) return context.heldItem().is(Items.SHEARS);
        if (definition.methodId().equals(DYE)) return context.heldItem().getItem() instanceof DyeItem;
        if (definition.methodId().equals(EGG)) return context.heldItem().isEmpty();
        ItemStack held = context.heldItem();
        if (definition.item().isPresent() && !held.is(BuiltInRegistries.ITEM.get(definition.item().get()))) return false;
        if (definition.itemTag().isPresent()) {
            TagKey<Item> tag = TagKey.create(BuiltInRegistries.ITEM.key(), definition.itemTag().get());
            if (!held.is(tag)) return false;
        }
        return definition.item().isPresent() || definition.itemTag().isPresent();
    }

    private static InteractionResult breed(MobFarmContext context, InteractionDefinition definition) {
        applyReadyBreeding(context);
        CompoundTag state = context.stored().state;
        long now = context.level().getGameTime();
        long cooldown = definition.cooldownTicks() > 0 ? definition.cooldownTicks() : 6000L;
        if (!state.getBoolean("breedingCycleActive") || now >= state.getLong("breedingReadyAt")) {
            state.putLong("breedingBaseCount", context.stored().count);
            state.putInt("breedingFedCount", 0);
            state.putLong("breedingReadyAt", now + cooldown);
            state.putBoolean("breedingCycleActive", true);
            MobFarmDebug.breeding(context.player(), "- cycle started\n- base count: " + context.stored().count + "\n- ready at game time: " + (now + cooldown));
        }
        long baseCount = state.getLong("breedingBaseCount");
        int fedCount = state.getInt("breedingFedCount");
        long maxFeedings = baseCount / 2;
        if (fedCount >= maxFeedings) {
            MobFarmDebug.breeding(context.player(), "- feed rejected reason: cycle full\n- base count: " + baseCount + "\n- max feedings: " + maxFeedings + "\n- current fed count: " + fedCount);
            return InteractionResult.FAIL;
        }
        context.heldItem().shrink(1);
        state.putInt("breedingFedCount", fedCount + 1);
        MobFarmDebug.breeding(context.player(), "- feed accepted\n- base count: " + baseCount + "\n- max feedings: " + maxFeedings + "\n- current fed count: " + (fedCount + 1) + "\n- ready at game time: " + state.getLong("breedingReadyAt"));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult milk(MobFarmContext context, InteractionDefinition definition) {
        context.heldItem().shrink(1);
        BehaviorUtil.output(context, new ItemStack(Items.MILK_BUCKET));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult shear(MobFarmContext context, InteractionDefinition definition) {
        CompoundTag state = context.stored().state;
        long now = context.level().getGameTime();
        if (now < state.getLong("nextWoolReadyAt")) return InteractionResult.PASS;
        BehaviorUtil.output(context, new ItemStack(SheepBehavior.woolForColor(DyeColor.byName(state.getString("sheepColor"), DyeColor.WHITE))));
        context.heldItem().hurtAndBreak(1, context.player(), net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        state.putLong("nextWoolReadyAt", now + 12000L);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult dye(MobFarmContext context, InteractionDefinition definition) {
        if (!(context.heldItem().getItem() instanceof DyeItem dye)) return InteractionResult.PASS;
        context.heldItem().shrink(1);
        String color = dye.getDyeColor().getName();
        context.stored().state.putString("sheepColor", color);
        context.stored().display = new DisplaySnapshot(context.stored().display.entityTypeId(), context.stored().display.textureId(), context.stored().display.variantKey(), color, context.stored().display.baby(), context.stored().display.scale());
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult egg(MobFarmContext context, InteractionDefinition definition) {
        long now = context.level().getGameTime();
        ResourceLocation action = MobFarmBlockMod.id("egg");
        if (!context.stored().ready(action, now)) return InteractionResult.PASS;
        BehaviorUtil.output(context, new ItemStack(Items.EGG));
        context.stored().setCooldown(action, now, definition.cooldownTicks() > 0 ? definition.cooldownTicks() : 6000L);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult outputItem(MobFarmContext context, InteractionDefinition definition) {
        if (definition.outputItem().isEmpty()) return InteractionResult.PASS;
        BehaviorUtil.output(context, new ItemStack(BuiltInRegistries.ITEM.get(definition.outputItem().get()), Math.max(1, definition.minCount())));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult changeState(MobFarmContext context, InteractionDefinition definition) {
        definition.stateKey().ifPresent(key -> context.stored().state.putString(key, definition.parameters().getOrDefault("value", "")));
        return InteractionResult.SUCCESS;
    }
    private InteractionMethodRegistry() {}
}
