package com.nanookmod.item.custom;

import net.minecraft.world.item.Item;

public class FrostBottleItem extends Item {
    public FrostBottleItem(Properties properties) {
        super(properties.stacksTo(16)); // ✅ Stackeable hasta 16
    }

    // ✅ NO agregar isFoil() para que no tenga brillo
}