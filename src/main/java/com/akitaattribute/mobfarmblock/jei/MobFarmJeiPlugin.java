package com.akitaattribute.mobfarmblock.jei;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public final class MobFarmJeiPlugin implements IModPlugin {
    public MobFarmJeiPlugin() {}

    @Override public ResourceLocation getPluginUid() { return MobFarmBlockMod.id("jei_plugin"); }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModBlocks.MOB_FARM_BLOCK.get()), RecipeTypes.CRAFTING);
    }
}
