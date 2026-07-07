package com.akitaattribute.mobfarmblock.behavior;

import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.DyeColor;

public class SheepBehavior extends GenericMobBehavior {
    private static final long WOOL_COOLDOWN_TICKS = 12_000L;

    @Override
    public ActionResult interact(MobFarmContext context) {
        if (context.heldItem().isOf(Items.SHEARS)) {
            return shear(context);
        }
        if (context.heldItem().getItem() instanceof DyeItem dye) {
            return dye(context, dye.getColor());
        }
        if (context.heldItem().isOf(Items.WHEAT)) {
            context.heldItem().decrement(1);
            context.stored().count++;
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private static ActionResult shear(MobFarmContext context) {
        NbtCompound state = context.stored().state;
        long now = context.level().getTime();
        long readyAt = state.getLong("nextWoolReadyAt");
        if (now < readyAt) {
            return ActionResult.PASS;
        }

        BehaviorUtil.output(context, new ItemStack(woolForColor(sheepColor(state))));
        context.heldItem().damage(1, context.player(), EquipmentSlot.MAINHAND);
        state.putLong("nextWoolReadyAt", now + WOOL_COOLDOWN_TICKS);
        return ActionResult.SUCCESS;
    }

    private static ActionResult dye(MobFarmContext context, DyeColor color) {
        context.heldItem().decrement(1);
        context.stored().state.putString("sheepColor", color.getName());
        context.stored().display = new com.akitaattribute.mobfarmblock.mob.DisplaySnapshot(
                context.stored().display.entityTypeId(),
                context.stored().display.textureId(),
                context.stored().display.variantKey(),
                color.getName(),
                context.stored().display.baby(),
                context.stored().display.scale()
        );
        return ActionResult.SUCCESS;
    }

    private static DyeColor sheepColor(NbtCompound state) {
        return DyeColor.byName(state.getString("sheepColor"), DyeColor.WHITE);
    }

    private static Item woolForColor(DyeColor color) {
        return switch (color) {
            case BLACK -> Items.BLACK_WOOL;
            case BLUE -> Items.BLUE_WOOL;
            case BROWN -> Items.BROWN_WOOL;
            case CYAN -> Items.CYAN_WOOL;
            case GRAY -> Items.GRAY_WOOL;
            case GREEN -> Items.GREEN_WOOL;
            case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL;
            case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case LIME -> Items.LIME_WOOL;
            case MAGENTA -> Items.MAGENTA_WOOL;
            case ORANGE -> Items.ORANGE_WOOL;
            case PINK -> Items.PINK_WOOL;
            case PURPLE -> Items.PURPLE_WOOL;
            case RED -> Items.RED_WOOL;
            case YELLOW -> Items.YELLOW_WOOL;
            case WHITE -> Items.WHITE_WOOL;
        };
    }
}
