package com.nanookmod.block.entity;

import com.nanookmod.menu.FrostBrewingStandMenu;
import com.nanookmod.registry.ModBlockEntities;
import com.nanookmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class FrostBrewingStandBlockEntity extends BaseContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(3, ItemStack.EMPTY);
    private int brewingProgress;
    private int fuelTime;
    private static final int BREWING_TIME = 400; // 20 segundos
    private static final int MAX_FUEL = 1600;   // 80 segundos

    public FrostBrewingStandBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FROST_BREWING_STAND.get(), pos, state);
    }

    // === LÓGICA DE TICKING ===
    public static void tick(Level level, BlockPos pos, BlockState state, FrostBrewingStandBlockEntity blockEntity) {
        boolean dirty = false;

        // 1. Consumir combustible
        if (blockEntity.fuelTime > 0) {
            blockEntity.fuelTime--;
            dirty = true;
        }

        // 2. Si no hay combustible, intentar consumir uno nuevo
        // Slot 0 = Combustible (Blizz Rod Powder)
        if (blockEntity.fuelTime == 0 && !blockEntity.items.get(0).isEmpty()) {
            ItemStack fuel = blockEntity.items.get(0);
            // ✅ FIX: Usar BLIZZ_ROD_POWDER en lugar de FROST_FUEL
            if (fuel.is(ModItems.BLIZZ_ROD_POWDER.get())) {
                blockEntity.fuelTime = MAX_FUEL;
                fuel.shrink(1);
                dirty = true;
            }
        }

        // 3. Verificar si hay una receta válida
        boolean hasRecipe = blockEntity.hasRecipe();

        // 4. Si hay combustible y receta válida, avanzar progreso
        if (blockEntity.fuelTime > 0 && hasRecipe) {
            blockEntity.brewingProgress++;

            // ✅ FIX: Sonido solo al inicio y cada 100 ticks (no cada 10)
            if (blockEntity.brewingProgress == 1 || blockEntity.brewingProgress % 100 == 0) {
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 1.2F);
            }

            // Partículas cada 20 ticks (más suave)
            if (blockEntity.brewingProgress % 20 == 0) {
                level.addParticle(ParticleTypes.BUBBLE_COLUMN_UP,
                        pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        (Math.random()-0.5)*0.1, 0.1, (Math.random()-0.5)*0.1);
            }

            // 5. Cuando termine el crafteo, transformar los items
            if (blockEntity.brewingProgress >= BREWING_TIME) {
                blockEntity.brewingProgress = 0;
                blockEntity.craftRecipe();
                // Sonido al completar
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.8F);
                dirty = true;
            }
        } else {
            // Si no hay receta válida, resetear progreso
            if (blockEntity.brewingProgress > 0) {
                blockEntity.brewingProgress = 0;
                dirty = true;
            }
        }

        // 6. Sincronizar cambios con el cliente
        if (dirty) {
            blockEntity.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
        }
    }

    // === VERIFICAR RECETA ===
    private boolean hasRecipe() {
        return hasBasicRecipe() || hasUpgradeRecipe();
    }

    // Frost Petal + Frost Bottle (llena) -> Poción básica (inmunidad parcial, evita el instakill)
    private boolean hasBasicRecipe() {
        boolean hasIngredient = !items.get(1).isEmpty() && items.get(1).is(ModItems.FROST_PETAL.get());
        // ✅ Cambiar a FROST_BOTTLE (la llena)
        boolean hasBottle = !items.get(2).isEmpty() && items.get(2).is(ModItems.FROST_BOTTLE.get());

        return hasIngredient && hasBottle;
    }

    // Blizz Rod + Poción básica -> Poción mejorada (inmunidad total)
    private boolean hasUpgradeRecipe() {
        boolean hasIngredient = !items.get(1).isEmpty() && items.get(1).is(ModItems.BLIZZ_ROD.get());
        boolean hasBasicPotion = !items.get(2).isEmpty() && items.get(2).is(ModItems.PERMAFROST_POTION.get());

        return hasIngredient && hasBasicPotion;
    }

    // === EJECUTAR RECETA ===
    private void craftRecipe() {
        if (hasBasicRecipe()) {
            // Consumir ingrediente (Frost Petal)
            items.get(1).shrink(1);

            // ✅ FIX: Consumir SOLO 1 botella (no todas las del stack)
            ItemStack bottleStack = items.get(2);
            bottleStack.shrink(1);

            // Crear poción de inmunidad al permafrost (básica)
            items.set(2, new ItemStack(ModItems.PERMAFROST_POTION.get()));
        } else if (hasUpgradeRecipe()) {
            // Consumir ingrediente (Blizz Rod)
            items.get(1).shrink(1);

            // Consumir la poción básica
            ItemStack potionStack = items.get(2);
            potionStack.shrink(1);

            // Crear poción de inmunidad al permafrost MEJORADA (total)
            items.set(2, new ItemStack(ModItems.GREATER_PERMAFROST_POTION.get()));
        }
    }

    // === INVENTARIO ===
    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("container.nanookmod.frost_brewing_stand");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return new FrostBrewingStandMenu(id, playerInventory, this);
    }

    @Override
    public int getContainerSize() { return 3; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) if (!stack.isEmpty()) return false;
        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize()) stack.setCount(this.getMaxStackSize());
        setChanged();
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return level.getBlockEntity(worldPosition) == this;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    // === SYNC DE DATOS ===
    public int getBrewingProgress() { return brewingProgress; }
    public int getFuelTime() { return fuelTime; }
    public int getMaxFuel() { return MAX_FUEL; }
    public int getMaxProgress() { return BREWING_TIME; }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BrewTime", brewingProgress);
        tag.putInt("Fuel", fuelTime);
        ContainerHelper.saveAllItems(tag, items);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        brewingProgress = tag.getInt("BrewTime");
        fuelTime = tag.getInt("Fuel");
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }
}