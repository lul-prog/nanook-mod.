package com.nanookmod.menu;

import com.nanookmod.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.nanookmod.block.entity.FrostBrewingStandBlockEntity;

public class FrostBrewingStandMenu extends AbstractContainerMenu {
    private final FrostBrewingStandBlockEntity blockEntity;

    public FrostBrewingStandMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, (FrostBrewingStandBlockEntity) playerInventory.player.level().getBlockEntity(buf.readBlockPos()));
    }

    public FrostBrewingStandMenu(int containerId, Inventory playerInventory, FrostBrewingStandBlockEntity blockEntity) {
        super(ModMenus.FROST_BREWING_STAND.get(), containerId);
        this.blockEntity = blockEntity;

        // Slots del bloque
        addSlot(new Slot(blockEntity, 0, 26, 25));              // Slot 0: Combustible (Fuel)
        addSlot(new Slot(blockEntity, 1, 80, 19));              // Slot 1: Ingrediente
        addSlot(new FrostBrewingStandSlot(blockEntity, 2, 80, 54, 1));  // Slot 2: Botella (MAX 1)

        // Slots del inventario del jugador
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            itemstack = stackInSlot.copy();
            if (index < 3) {
                if (!this.moveItemStackTo(stackInSlot, 3, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!this.moveItemStackTo(stackInSlot, 0, 3, false)) return ItemStack.EMPTY;

            if (stackInSlot.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return itemstack;
    }

    // Getters para la Screen
    public int getBrewingProgress() { return blockEntity.getBrewingProgress(); }
    public int getFuelTime() { return blockEntity.getFuelTime(); }
    public int getMaxFuel() { return blockEntity.getMaxFuel(); }
    public int getMaxProgress() { return blockEntity.getMaxProgress(); }
}