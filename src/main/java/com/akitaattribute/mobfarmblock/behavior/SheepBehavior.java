package com.akitaattribute.mobfarmblock.behavior;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Legacy compatibility shell. GenericMobBehavior + InteractionMethodRegistry are the active paths. */
public class SheepBehavior extends GenericMobBehavior {
    public static Item woolForColor(DyeColor color) {
        return switch (color) {
            case BLACK -> Items.BLACK_WOOL; case BLUE -> Items.BLUE_WOOL; case BROWN -> Items.BROWN_WOOL; case CYAN -> Items.CYAN_WOOL;
            case GRAY -> Items.GRAY_WOOL; case GREEN -> Items.GREEN_WOOL; case LIGHT_BLUE -> Items.LIGHT_BLUE_WOOL; case LIGHT_GRAY -> Items.LIGHT_GRAY_WOOL;
            case LIME -> Items.LIME_WOOL; case MAGENTA -> Items.MAGENTA_WOOL; case ORANGE -> Items.ORANGE_WOOL; case PINK -> Items.PINK_WOOL;
            case PURPLE -> Items.PURPLE_WOOL; case RED -> Items.RED_WOOL; case YELLOW -> Items.YELLOW_WOOL; case WHITE -> Items.WHITE_WOOL;
        };
    }
}
