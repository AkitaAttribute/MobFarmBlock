package com.akitaattribute.mobfarmblock.behavior;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import com.akitaattribute.mobfarmblock.debug.MobFarmDebug;
import com.akitaattribute.mobfarmblock.debug.CobblemonDebugDumper;
import com.akitaattribute.mobfarmblock.mob.DropRule;
import com.akitaattribute.mobfarmblock.mob.XpProfile;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

final class BehaviorUtil {
    private BehaviorUtil() {}

    static AttackResult attack(MobFarmContext context) {
        if (context.stored().isEmpty()) return AttackResult.PASS;
        if (!isWeapon(context.heldItem())) {
            MobFarmDebug.send(context.player(), Component.literal("Mob Farm Block Debug:\nProcessing rejected\n- reason: weapon required"));
            return AttackResult.PASS;
        }
        context.stored().count--;
        if (context.stored().count <= 0) {
            context.stored().state.putBoolean("breedingCycleActive", false);
            context.stored().state.putLong("breedingFedCount", 0L);
            context.stored().state.putLong("breedingReadyAt", 0L);
        }
        int lootingLevel = getLootingLevel(context);
        StringBuilder details = new StringBuilder();
        StringBuilder rolled = new StringBuilder();
        boolean nativePixelmonDrops = context.stored().dropProfile.drops().isEmpty() && tryNativePixelmonDrops(context, details);
        int index = 0;
        if (!nativePixelmonDrops) {
            for (DropRule rule : context.stored().dropProfile.drops()) {
                RollResult result = rollDrop(context, rule, lootingLevel);
                details.append("\n  #").append(index++)
                        .append(" itemId=").append(rule.itemId())
                        .append(" chance=").append(rule.chance())
                        .append(" min=").append(rule.minCount())
                        .append(" max=").append(rule.maxCount())
                        .append(" affectedByLooting=").append(rule.affectedByLooting())
                        .append(" lootingChanceBonus=").append(rule.lootingChanceBonus())
                        .append(" lootingMaxBonus=").append(rule.lootingMaxBonus())
                        .append(" roll=").append(String.format(java.util.Locale.ROOT, "%.5f", result.roll()))
                        .append(" success=").append(result.success())
                        .append(" finalCount=").append(result.finalCount())
                        .append(" output=").append(result.outputTarget());
                if (result.success() && result.finalCount() > 0) {
                    if (!rolled.isEmpty()) rolled.append(", ");
                    rolled.append(rule.itemId()).append(" x").append(result.finalCount());
                }
            }
        } else {
            rolled.append("native Pixelmon drop routine");
        }
        int xp = awardXp(context);
        if ("cobblemon:pokemon".equals(context.stored().mobId.toString()) || "pixelmon:pixelmon".equals(context.stored().mobId.toString())) CobblemonDebugDumper.writeProcessingDump(context.player(), context.stored(), details.toString());
        MobFarmDebug.send(context.player(), Component.literal("Mob Farm Block Debug:\nProcessed mob"
                + "\n- mob id: " + context.stored().mobId
                + "\n- kind: " + context.stored().kind
                + "\n- species id: " + (context.stored().speciesId == null ? "unavailable" : context.stored().speciesId)
                + "\n- drop profile source: " + context.stored().dropProfileSource
                + "\n- drop rule count: " + context.stored().dropProfile.drops().size()
                + "\n- native Pixelmon drops: " + nativePixelmonDrops
                + "\n- xp range: " + context.stored().dropProfile.xp().minXp() + "-" + context.stored().dropProfile.xp().maxXp()
                + "\n- xp rolled: " + xp
                + "\n- rolled drops: " + (rolled.isEmpty() ? "none" : rolled)
                + "\n- drop rule details:" + (details.isEmpty() ? " none" : details)));
        return AttackResult.SUCCESS;
    }

    private static boolean tryNativePixelmonDrops(MobFarmContext context, StringBuilder details) {
        if (!"pixelmon:pixelmon".equals(context.stored().mobId.toString())) return false;
        if (!(context.level() instanceof ServerLevel) || !(context.player() instanceof ServerPlayer serverPlayer)) return false;
        if (context.stored().pixelmonRenderSnapshot == null || context.stored().pixelmonRenderSnapshot.payload() == null || context.stored().pixelmonRenderSnapshot.payload().isBlank()) return false;
        try {
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(context.stored().mobId);
            if (type == null) return false;
            Entity entity = type.create(context.level());
            if (entity == null) return false;
            CompoundTag tag = TagParser.parseTag(context.stored().pixelmonRenderSnapshot.payload());
            if (!invokeAny(entity, "load", tag)) details.append("\n  nativePixelmon=load method not found");
            entity.setPos(Vec3.atCenterOf(context.pos()).add(0.0D, 0.75D, 0.0D));
            boolean dropped = invokeAny(entity, "dropNormalItems", serverPlayer);
            if (!dropped) dropped = invokeAny(entity, "dropItems", serverPlayer);
            details.append("\n  nativePixelmon=used actual Pixelmon entity drop method result=").append(dropped ? "called" : "not_found");
            return dropped;
        } catch (Throwable error) {
            details.append("\n  nativePixelmon=failed ").append(error.getClass().getSimpleName()).append(": ").append(error.getMessage());
            return false;
        }
    }

    static String output(MobFarmContext context, ItemStack stack) {
        if (stack.isEmpty()) return "empty_stack";
        BlockEntity below = context.level().getBlockEntity(context.pos().below());
        if (below != null) {
            IItemHandler handler = context.level().getCapability(Capabilities.ItemHandler.BLOCK, context.pos().below(), null);
            if (handler != null) {
                int before = stack.getCount();
                stack = insert(handler, stack);
                if (stack.isEmpty()) return "inventory_below";
                if (stack.getCount() < before) {
                    Block.popResource(context.level(), context.pos().above(), stack);
                    return "inventory_below_and_world";
                }
            }
        }
        Block.popResource(context.level(), context.pos().above(), stack);
        return "world";
    }

    private static ItemStack insert(IItemHandler handler, ItemStack stack) {
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    private static boolean isWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(itemTag("minecraft", "swords"))
                || stack.is(itemTag("minecraft", "axes"))
                || stack.is(itemTag("minecraft", "enchantable/weapon"))
                || stack.is(itemTag("neoforge", "weapon"))
                || stack.is(itemTag("forge", "weapon"));
    }

    private static TagKey<Item> itemTag(String namespace, String path) {
        return TagKey.create(BuiltInRegistries.ITEM.key(), ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static RollResult rollDrop(MobFarmContext context, DropRule rule, int lootingLevel) {
        double chance = rule.chance();
        if (rule.affectedByLooting()) chance += lootingLevel * rule.lootingChanceBonus();
        chance = Math.max(0.0, Math.min(1.0, chance));
        double roll = context.random().nextDouble();
        if (roll > chance) return new RollResult(roll, false, 0, "not_rolled");
        int count = randomBetween(context, rule.minCount(), rule.maxCount());
        if (rule.affectedByLooting() && lootingLevel > 0 && rule.lootingMaxBonus() > 0) {
            count += context.random().nextInt(lootingLevel * rule.lootingMaxBonus() + 1);
        }
        if (count <= 0) return new RollResult(roll, true, count, "non_positive_count");
        String target = output(context, new ItemStack(BuiltInRegistries.ITEM.get(rule.itemId()), count));
        return new RollResult(roll, true, count, target);
    }

    private static int randomBetween(MobFarmContext context, int min, int max) {
        return max <= min ? min : min + context.random().nextInt(max - min + 1);
    }

    private static int awardXp(MobFarmContext context) {
        XpProfile xp = context.stored().dropProfile.xp();
        if (xp.maxXp() <= 0 || !(context.level() instanceof ServerLevel serverLevel)) return 0;
        int amount = randomBetween(context, xp.minXp(), xp.maxXp());
        if (amount > 0) ExperienceOrb.award(serverLevel, Vec3.atCenterOf(context.pos()), amount);
        return amount;
    }

    private static int getLootingLevel(MobFarmContext context) {
        var registry = context.level().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        Holder.Reference<Enchantment> looting = registry.getHolderOrThrow(Enchantments.LOOTING);
        return context.heldItem().getEnchantmentLevel(looting);
    }

    private static boolean invokeAny(Object target, String name, Object... args) {
        if (target == null) return false;
        try {
            Method method = findMethod(target.getClass(), name, args);
            if (method == null) return false;
            method.setAccessible(true);
            method.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method findMethod(Class<?> type, String name, Object... args) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                if (parametersCompatible(method.getParameterTypes(), args)) return method;
            }
        }
        return null;
    }

    private static boolean parametersCompatible(Class<?>[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; i++) {
            if (args[i] == null) continue;
            Class<?> parameter = wrapPrimitive(parameterTypes[i]);
            Class<?> actual = wrapPrimitive(args[i].getClass());
            if (!parameter.isAssignableFrom(actual)) return false;
        }
        return true;
    }

    private static Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private record RollResult(double roll, boolean success, int finalCount, String outputTarget) {}
}
