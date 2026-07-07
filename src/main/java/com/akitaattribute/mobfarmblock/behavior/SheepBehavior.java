package com.akitaattribute.mobfarmblock.behavior;

import com.akitaattribute.mobfarmblock.mob.DisplaySnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.DyeColor;

public class SheepBehavior extends GenericMobBehavior {
    private static final long WOOL_COOLDOWN_TICKS = 12_000L;

    @Override public InteractionResult interact(MobFarmContext context) {
        if (context.heldItem().is(Items.SHEARS)) return shear(context);
        if (context.heldItem().getItem() instanceof DyeItem dye) return dye(context, dye.getDyeColor());
        if (context.heldItem().is(Items.WHEAT)) {
            context.heldItem().shrink(1);
            context.stored().count++;
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    private static InteractionResult shear(MobFarmContext context) {
        CompoundTag state = context.stored().state;
        long now = context.level().getGameTime();
        long readyAt = state.getLong("nextWoolReadyAt");
        if (now < readyAt) return InteractionResult.PASS;
        BehaviorUtil.output(context, new ItemStack(woolForColor(sheepColor(state))));
        context.heldItem().hurtAndBreak(1, context.player(), net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        state.putLong("nextWoolReadyAt", now + WOOL_COOLDOWN_TICKS);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult dye(MobFarmContext context, DyeColor color) {
        context.heldItem().shrink(1);
        context.stored().state.putString("sheepColor", color.getName());
        context.stored().display = new DisplaySnapshot(context.stored().display.entityTypeId(), context.stored().display.textureId(), context.stored().display.variantKey(), color.getName(), context.stored().display.baby(), context.stored().display.scale());
        return InteractionResult.SUCCESS;
    }

    private static DyeColor sheepColor(CompoundTag state) { return DyeColor.byName(state.getString("sheepColor"), DyeColor.WHITE); }

    private static Item woolForColor(DyeColor color) {
        return switch (color) {
            case BLACK -> Items.BLACK_WOOL; case BLUE -> Items.BLUE_WOOL; case BROWN -> Items.BROWN_WOOL; case CYAN -> Items.CYAN_WOOL;
            case GRAY -> Items.GRAY_WOOL; case GREEN -> Items.GREEN_WOOL; case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL; case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case LIME -> Items.LIME_WOOL; case MAGENTA -> Items.MAGENTA_WOOL; case ORANGE -> Items.ORANGE_WOOL; case PINK -> Items.PINK_WOOL;
            case PURPLE -> Items.PURPLE_WOOL; case RED -> Items.RED_WOOL; case YELLOW -> Items.YELLOW_WOOL; case WHITE -> Items.WHITE_WOOL;
        };
    }
}
