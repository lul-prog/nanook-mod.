package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.menu.FrostBrewingStandMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, NanookMod.MOD_ID);

    public static final RegistryObject<MenuType<FrostBrewingStandMenu>> FROST_BREWING_STAND =
            MENUS.register("frost_brewing_stand",
                    () -> IForgeMenuType.create(FrostBrewingStandMenu::new));
}