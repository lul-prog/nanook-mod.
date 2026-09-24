package com.nanookmod.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nanookmod.NanookMod;
import com.nanookmod.menu.FrostBrewingStandMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class FrostBrewingStandScreen extends AbstractContainerScreen<FrostBrewingStandMenu> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(NanookMod.MOD_ID, "textures/gui/frost_brewing_stand.png");

    public FrostBrewingStandScreen(FrostBrewingStandMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // 1. Dibujar el fondo completo (incluye slots, flecha vacía y barra vacía)
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(TEXTURE, x, y, 0, 0, imageWidth, imageHeight);


        // Animación de la Flecha (Vertical: 7x26 vacía, 7x27 llena)
        // Animación de la Flecha (Vertical: 7x27 llena)
        int progress = menu.getBrewingProgress();
        int maxProgress = menu.getMaxProgress();
        if (progress > 0) {
            int arrowScale = (progress * 27) / maxProgress;
            // ✅ La flecha crece de arriba hacia abajo
            // Posición Y fija en y + 19, altura variable
            guiGraphics.blit(TEXTURE, x + 100, y + 19, 177, 1, 7, arrowScale);
        }

        // 3. Animación de la Barra de Combustible (Vertical: 6x20 vacía, 4x18 llena)
        int fuel = menu.getFuelTime();
        int maxFuel = menu.getMaxFuel();
        if (fuel > 0) {
            int fuelScale = (fuel * 18) / maxFuel;
            // Centramos la barra llena (4px) dentro del espacio de la vacía (6px): 69 + 1 = 70
            guiGraphics.blit(TEXTURE, x + 70, y + 36 + (20 - fuelScale), 187, 1 + (18 - fuelScale), 4, fuelScale);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // 1. Fondo oscuro (dim background)
        this.renderBackground(guiGraphics);

        // 2. GUI completa (textura + slots + animaciones)
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        // 3. Tooltips
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}