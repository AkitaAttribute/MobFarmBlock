package com.akitaattribute.mobfarmblock.registry;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.item.CaptureToolItem;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, MobFarmBlockMod.MOD_ID);

    public static final DeferredHolder<Item, CaptureToolItem> CAPTURE_TOOL = ITEMS.register(
            "capture_tool",
            () -> new CaptureToolItem(new Item.Properties().stacksTo(1))
    );

    private ModItems() {
    }
}
