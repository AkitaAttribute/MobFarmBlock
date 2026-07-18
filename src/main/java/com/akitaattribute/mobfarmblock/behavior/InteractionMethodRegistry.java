package com.akitaattribute.mobfarmblock.behavior;

import java.util.HashMap;
import java.util.Map;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.debug.MobFarmDebug;
import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.InteractionDefinition;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
    public static final ResourceLocation HARVEST = MobFarmBlockMod.id("harvest");
    public static final ResourceLocation DYE = MobFarmBlockMod.id("dye");
    public static final ResourceLocation EGG = MobFarmBlockMod.id("egg");
    public static final ResourceLocation PIXELMON_DROPS = MobFarmBlockMod.id("pixelmon_drops");
    public static final ResourceLocation OUTPUT_ITEM = MobFarmBlockMod.id("output_item");
    public static final ResourceLocation CHANGE_STATE = MobFarmBlockMod.id("change_state");

    public static void registerDefaults() {
        register(BREED, InteractionMethodRegistry::breed);
        register(MILK, InteractionMethodRegistry::milk);
        register(SHEAR, InteractionMethodRegistry::shear);
        register(HARVEST, InteractionMethodRegistry::harvest);
        register(DYE, InteractionMethodRegistry::dye);
        register(EGG, InteractionMethodRegistry::egg);
        register(PIXELMON_DROPS, InteractionMethodRegistry::pixelmonDrops);
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
        if (context.stored().count <= 0 && state.getBoolean("breedingCycleActive")) {
            clearBreeding(state, 0L);
            return;
        }
        if (state.getBoolean("breedingCycleActive") && now >= state.getLong("breedingReadyAt")) {
            clearBreeding(state, context.stored().count);
        }
    }

    private static boolean matchesHeldItem(MobFarmContext context, InteractionDefinition definition) {
        if (definition.methodId().equals(SHEAR)) return context.heldItem().is(Items.SHEARS);
        if (definition.methodId().equals(DYE)) return context.heldItem().getItem() instanceof DyeItem;
        if (definition.methodId().equals(EGG) || definition.methodId().equals(PIXELMON_DROPS)) return context.heldItem().isEmpty();
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
            state.putLong("breedingFedCount", 0L);
            state.putLong("breedingPendingFeedCount", 0L);
            state.putLong("breedingProducedCount", 0L);
            state.putLong("breedingReadyAt", now + cooldown);
            state.putBoolean("breedingCycleActive", true);
            MobFarmDebug.breeding(context.player(), "- cycle started\n- base count: " + context.stored().count + "\n- ready at game time: " + (now + cooldown));
        }

        long baseCount = state.getLong("breedingBaseCount");
        long pendingFeed = state.contains("breedingPendingFeedCount") ? state.getLong("breedingPendingFeedCount") : state.getLong("breedingFedCount") % 2L;
        long produced = state.contains("breedingProducedCount") ? state.getLong("breedingProducedCount") : state.getLong("breedingFedCount") / 2L;
        long effectiveCapacity = Math.min(baseCount, context.stored().count) / 2L;
        long remainingOffspring = Math.max(0L, effectiveCapacity - produced);
        long remainingFeedItems = remainingOffspring * 2L - pendingFeed;

        if (baseCount < 2L || context.stored().count < 2L) {
            clearBreeding(state, context.stored().count);
            MobFarmDebug.breeding(context.player(), "- feed rejected reason: not enough parents\n- base count: " + baseCount + "\n- current count: " + context.stored().count);
            return InteractionResult.FAIL;
        }
        if (remainingFeedItems <= 0L) {
            MobFarmDebug.breeding(context.player(), "- feed rejected reason: cycle full\n- base count: " + baseCount + "\n- current count: " + context.stored().count + "\n- effective capacity: " + effectiveCapacity + "\n- produced this cycle: " + produced + "\n- pending feed: " + pendingFeed);
            return InteractionResult.FAIL;
        }

        context.heldItem().shrink(1);
        pendingFeed++;
        if (pendingFeed >= 2L) {
            pendingFeed -= 2L;
            produced++;
            context.stored().count++;
        }
        state.putLong("breedingPendingFeedCount", pendingFeed);
        state.putLong("breedingProducedCount", produced);
        state.putLong("breedingFedCount", produced * 2L + pendingFeed);
        MobFarmDebug.breeding(context.player(), "- feed accepted\n- base count: " + baseCount + "\n- current count: " + context.stored().count + "\n- effective capacity: " + effectiveCapacity + "\n- produced this cycle: " + produced + "\n- pending feed: " + pendingFeed + "\n- remaining feed items: " + Math.max(0L, effectiveCapacity * 2L - produced * 2L - pendingFeed) + "\n- ready at game time: " + state.getLong("breedingReadyAt"));
        return InteractionResult.SUCCESS;
    }

    private static void clearBreeding(CompoundTag state, long baseCount) {
        state.putBoolean("breedingCycleActive", false);
        state.putLong("breedingFedCount", 0L);
        state.putLong("breedingPendingFeedCount", 0L);
        state.putLong("breedingProducedCount", 0L);
        state.putLong("breedingBaseCount", baseCount);
        state.putLong("breedingReadyAt", 0L);
    }

    private static InteractionResult milk(MobFarmContext context, InteractionDefinition definition) {
        replaceOneHeldItem(context.player(), context.heldItem(), new ItemStack(Items.MILK_BUCKET));
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult shear(MobFarmContext context, InteractionDefinition definition) {
        CompoundTag state = context.stored().state;
        long now = context.level().getGameTime();
        ResourceLocation action = SHEAR;
        long readyAt = Math.max(state.getLong("nextWoolReadyAt"), context.stored().readyAtTicks.getOrDefault(action, 0L));
        if (now < readyAt) return InteractionResult.PASS;

        Item outputItem = definition.outputItem()
                .map(BuiltInRegistries.ITEM::get)
                .orElseGet(() -> SheepBehavior.woolForColor(DyeColor.byName(state.getString("sheepColor"), DyeColor.WHITE)));
        int min = Math.max(1, definition.minCount());
        int max = Math.max(min, definition.maxCount());
        if (min == 1 && max == 1 && definition.outputItem().isEmpty()) max = 3;

        long total = scaledRoll(context, min, max);
        outputLargeStack(context, outputItem, total);
        context.heldItem().hurtAndBreak(1, context.player(), net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        long cooldown = definition.cooldownTicks() > 0 ? definition.cooldownTicks() : 6000L;
        state.putLong("nextWoolReadyAt", now + cooldown);
        context.stored().setCooldown(action, now, cooldown);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult harvest(MobFarmContext context, InteractionDefinition definition) {
        if (definition.outputItem().isEmpty()) return InteractionResult.PASS;
        long now = context.level().getGameTime();
        ResourceLocation action = definition.methodId();
        if (!context.stored().ready(action, now)) return InteractionResult.PASS;

        int min = Math.max(1, definition.minCount());
        int max = Math.max(min, definition.maxCount());
        boolean scaleWithCount = !"false".equalsIgnoreCase(definition.parameters().getOrDefault("scaleWithCount", "true"));
        long total = scaleWithCount ? scaledRoll(context, min, max) : randomBetween(context, min, max);
        Item outputItem = BuiltInRegistries.ITEM.get(definition.outputItem().get());
        if (isContainerFill(definition, context.heldItem(), outputItem)) {
            replaceOneHeldItem(context.player(), context.heldItem(), new ItemStack(outputItem));
            if (total > 1L) outputLargeStack(context, outputItem, total - 1L);
        } else {
            outputLargeStack(context, outputItem, total);
            if ("true".equalsIgnoreCase(definition.parameters().getOrDefault("consume", "false"))) context.heldItem().shrink(1);
        }
        if ("true".equalsIgnoreCase(definition.parameters().getOrDefault("damageTool", "false"))) context.heldItem().hurtAndBreak(1, context.player(), net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        long cooldown = harvestCooldown(context, definition);
        if (cooldown > 20L) context.stored().setCooldown(action, now, cooldown);
        return InteractionResult.SUCCESS;
    }

    private static long harvestCooldown(MobFarmContext context, InteractionDefinition definition) {
        long base = longParameter(definition, "baseCooldownTicks", definition.cooldownTicks());
        long reduction = longParameter(definition, "cooldownReductionPerMobTicks", 0L);
        long freeCount = longParameter(definition, "cooldownReductionFreeCount", 1L);
        long effectiveCount = Math.max(0L, context.stored().count - freeCount);
        return Math.max(0L, base - effectiveCount * reduction);
    }

    private static long longParameter(InteractionDefinition definition, String key, long fallback) {
        try {
            String value = definition.parameters().get(key);
            return value == null || value.isBlank() ? fallback : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long scaledRoll(MobFarmContext context, int min, int max) {
        long total = 0L;
        for (long i = 0; i < context.stored().count; i++) total += randomBetween(context, min, max);
        return total;
    }

    private static void outputLargeStack(MobFarmContext context, Item item, long amount) {
        int maxStackSize = Math.max(1, item.getDefaultInstance().getMaxStackSize());
        long remaining = Math.max(0L, amount);
        while (remaining > 0L) {
            int batch = (int) Math.min(maxStackSize, remaining);
            BehaviorUtil.output(context, new ItemStack(item, batch));
            remaining -= batch;
        }
    }

    private static void replaceOneHeldItem(Player player, ItemStack held, ItemStack filled) {
        if (held.getCount() == 1) {
            held.setCount(0);
            player.setItemInHand(player.getUsedItemHand(), filled);
            return;
        }
        held.shrink(1);
        if (!player.getInventory().add(filled)) player.drop(filled, false);
    }

    private static boolean isContainerFill(InteractionDefinition definition, ItemStack held, Item outputItem) {
        if ("true".equalsIgnoreCase(definition.parameters().getOrDefault("fillContainer", "false"))) return true;
        return (held.is(Items.GLASS_BOTTLE) && outputItem == Items.HONEY_BOTTLE)
                || (held.is(Items.BUCKET) && outputItem == Items.MILK_BUCKET)
                || (held.is(Items.BOWL) && outputItem == Items.MUSHROOM_STEW);
    }

    private static int randomBetween(MobFarmContext context, int min, int max) {
        return BehaviorUtil.randomBetween(context, min, max);
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
        outputLargeStack(context, Items.EGG, Math.max(1L, context.stored().count));
        context.stored().setCooldown(action, now, definition.cooldownTicks() > 0 ? definition.cooldownTicks() : 6000L);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult pixelmonDrops(MobFarmContext context, InteractionDefinition definition) {
        long now = context.level().getGameTime();
        ResourceLocation action = PIXELMON_DROPS;
        if (!context.stored().ready(action, now)) return InteractionResult.PASS;
        for (long mobIndex = 0; mobIndex < Math.max(1L, context.stored().count); mobIndex++) {
            for (DropRule rule : context.stored().dropProfile.drops()) {
                double chance = Math.max(0.0D, Math.min(1.0D, rule.chance()));
                if (context.random().nextDouble() > chance) continue;
                int amount = randomBetween(context, rule.minCount(), rule.maxCount());
                if (amount <= 0) continue;
                outputLargeStack(context, BuiltInRegistries.ITEM.get(rule.itemId()), amount);
            }
        }
        context.stored().setCooldown(action, now, definition.cooldownTicks() > 0 ? definition.cooldownTicks() : 6000L);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult outputItem(MobFarmContext context, InteractionDefinition definition) {
        if (definition.outputItem().isEmpty()) return InteractionResult.PASS;
        Item outputItem = BuiltInRegistries.ITEM.get(definition.outputItem().get());
        if (isContainerFill(definition, context.heldItem(), outputItem)) replaceOneHeldItem(context.player(), context.heldItem(), new ItemStack(outputItem));
        else {
            if ((definition.item().isPresent() || definition.itemTag().isPresent()) && !context.heldItem().isEmpty() && "true".equalsIgnoreCase(definition.parameters().getOrDefault("consume", "false"))) context.heldItem().shrink(1);
            BehaviorUtil.output(context, new ItemStack(outputItem, Math.max(1, definition.minCount())));
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult changeState(MobFarmContext context, InteractionDefinition definition) {
        definition.stateKey().ifPresent(key -> context.stored().state.putString(key, definition.parameters().getOrDefault("value", "")));
        return InteractionResult.SUCCESS;
    }
    private InteractionMethodRegistry() {}
}
