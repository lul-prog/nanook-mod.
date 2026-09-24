package com.nanookmod.client.hud;

import com.nanookmod.NanookMod;
import com.nanookmod.capability.NecromanticChainCapability;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

public class NecromanticChainHudOverlay implements IGuiOverlay {

    private static final ResourceLocation TEX_BACKGROUND = new ResourceLocation(NanookMod.MOD_ID, "textures/gui/necromantic_chain_bar_bg.png");
    private static final ResourceLocation TEX_FILL = new ResourceLocation(NanookMod.MOD_ID, "textures/gui/necromantic_chain_bar_fill.png");

    private static final int BAR_WIDTH = 20;
    private static final int BAR_HEIGHT = 60;
    private static final int MARGIN_LEFT = 10;
    private static final int TEXT_OFFSET_X = BAR_WIDTH + 6;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        NecromanticChainCapability.INecromanticChainCapability cap = NecromanticChainCapability.getCap(mc.player);
        if (cap == null || !cap.isActive()) {
            return;
        }

        float accumulatedDamage = cap.getAccumulatedDamage();

        float maxDamage = NecromanticChainCapability.MAX_ACCUMULATED_DAMAGE;
        float percentage = Math.max(0f, Math.min(1f, accumulatedDamage / maxDamage));

        int x = MARGIN_LEFT;
        int y = (screenHeight - BAR_HEIGHT) / 2;

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x, y, 0);

        guiGraphics.blit(TEX_BACKGROUND, 0, 0, 0, 0, BAR_WIDTH, BAR_HEIGHT, BAR_WIDTH, BAR_HEIGHT);

        int fillHeight = Math.round(BAR_HEIGHT * percentage);
        if (fillHeight > 0) {
            int srcY = BAR_HEIGHT - fillHeight;
            guiGraphics.blit(TEX_FILL, 0, BAR_HEIGHT - fillHeight, 0, srcY, BAR_WIDTH, fillHeight, BAR_WIDTH, BAR_HEIGHT);
        }

        String damageText = String.format("DMG: %.1f / %.0f", accumulatedDamage, maxDamage);
        int textX = TEXT_OFFSET_X;
        int textY = BAR_HEIGHT / 2 - 4;
        guiGraphics.drawString(mc.font, damageText, textX, textY, 0x9CF6DE, true);

        guiGraphics.pose().popPose();
    }
}