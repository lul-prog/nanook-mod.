package com.nanookmod.menu;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FrostBrewingStandSlot extends Slot {
    private final int maxStack;

    public FrostBrewingStandSlot(Container container, int index, int x, int y, int maxStack) {
        super(container, index, x, y);
        this.maxStack = maxStack;
    }

    @Override
    public int getMaxStackSize() {
        return maxStack;
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        return maxStack;
    }
}
