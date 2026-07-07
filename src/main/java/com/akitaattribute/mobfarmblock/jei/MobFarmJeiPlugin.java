package com.akitaattribute.mobfarmblock.jei;

import com.akitaattribute.mobfarmblock.MobFarmBlockMod;
import com.akitaattribute.mobfarmblock.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.util.Identifier;

/** Optional JEI scaffold. The JEI API is compile-only and this plugin is discovered only when JEI is installed. */
@JeiPlugin
public class MobFarmJeiPlugin implements IModPlugin {
    @Override public Identifier getPluginUid() { return MobFarmBlockMod.id("jei_plugin"); }
    @Override public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ModBlocks.MOB_FARM_BLOCK_ITEM.getDefaultStack(), RecipeTypes.CRAFTING);
    }
}
