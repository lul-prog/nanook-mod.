package com.nanookmod.client;

import com.nanookmod.NanookMod;
import com.nanookmod.client.hud.NecromanticChainHudOverlay;
import com.nanookmod.client.hud.NecroticKnightHudOverlay;
import com.nanookmod.client.hud.CorruptionScreenOverlay;
import com.nanookmod.client.hud.NanookHudOverlay;
import com.nanookmod.client.hud.PermafrostFrostOverlay;
import com.nanookmod.client.render.NanookRisingBlockRenderer;
import com.nanookmod.client.render.*;
import com.nanookmod.client.render.NecromanticChainRenderLayer;
import com.nanookmod.client.render.NanookIceSpikeRenderer;
import com.nanookmod.client.render.NanookIceMeteorRenderer;
import com.nanookmod.client.render.NecroticAltarRenderer;
import com.nanookmod.client.screen.FrostBrewingStandScreen;
import com.nanookmod.registry.ModBlockEntities;
import com.nanookmod.registry.ModBlocks;
import com.nanookmod.registry.ModEntities;
import com.nanookmod.registry.ModItems;
import com.nanookmod.registry.ModMenus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GrassColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.NANOOK.get(), NanookRenderer::new);
        event.registerEntityRenderer(ModEntities.NANOOK_CLAW_PROJECTILE.get(), NanookClawProjectileRenderer::new);
        // --- NUEVO: proyectiles de Nanook ---
        event.registerEntityRenderer(ModEntities.NANOOK_ICE_SPIKE.get(), NanookIceSpikeRenderer::new);
        event.registerEntityRenderer(ModEntities.NANOOK_ICE_METEOR.get(), NanookIceMeteorRenderer::new);
        // Escombros del golpe al suelo (double_ground).
        event.registerEntityRenderer(ModEntities.NANOOK_RISING_BLOCK.get(), NanookRisingBlockRenderer::new);
        // Cristales de hielo (abanico de cristales y aterrizaje del salto).
        event.registerEntityRenderer(ModEntities.NANOOK_CRYSTAL.get(), NanookCrystalRenderer::new);

        event.registerEntityRenderer(ModEntities.SNOWY_BLIZZ.get(), SnowyBlizzRenderer::new);
        event.registerEntityRenderer(ModEntities.SNOWY_BLIZZ_ICEBALL.get(), SnowyBlizzIceballRenderer::new);

        event.registerEntityRenderer(ModEntities.GNUT.get(), GnutRenderer::new);
        event.registerEntityRenderer(ModEntities.CROW.get(), CrowRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETAL_WARRIOR.get(), SkeletalWarriorRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETAL_WARRIOR_AURA_VFX.get(), SkeletalWarriorAuraVfxRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETAL_MAGE.get(), SkeletalMageRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETAL_MAGE_NECROBALL.get(), SkeletalMageNecroballRenderer::new);
        event.registerEntityRenderer(ModEntities.TWILIGHT_WISP.get(), TwilightWispRenderer::new);
        event.registerEntityRenderer(ModEntities.NECROTIC_KNIGHT.get(), NecroticKnightRenderer::new);
        event.registerEntityRenderer(ModEntities.NECROTIC_KNIGHT_FAKE.get(), NecroticKnightRenderer::new);
        event.registerEntityRenderer(ModEntities.NECROTIC_KNIGHT_PULSE_VFX.get(), NecroticKnightPulseVfxRenderer::new);
        event.registerEntityRenderer(ModEntities.TWILIGHT_PORTAL.get(), TwilightPortalRenderer::new);

        event.registerEntityRenderer(ModEntities.NECROTIC_SLASH_VFX.get(), NecroticSlashVfxRenderer::new);
        event.registerEntityRenderer(ModEntities.NECROTIC_GROUND_RUPTURE.get(), NecroticGroundRuptureVfxRenderer::new);
        event.registerEntityRenderer(ModEntities.NECROTIC_LIGHTNING_BEAM.get(), NecroticLightningBeamRenderer::new);

        // Brillo progresivo del Altar Necromante (anillos de ofrenda + núcleo del ritual).
        // registerBlockEntityRenderer también vive en EntityRenderersEvent.RegisterRenderers
        // en 1.20.1, no hace falta un evento aparte para block entities.
        event.registerBlockEntityRenderer(ModBlockEntities.NECROTIC_ALTAR.get(), NecroticAltarRenderer::new);
    }

    /**
     * PEDIDO: "que ciertos detalles del altar (las líneas verdes en la base y cerca del tope)
     * también brillen". La geometría es la MISMA del bloque (altar_necromante_model.json); lo
     * único que cambia es la textura, a una casi toda transparente que solo pinta esos detalles
     * (altar_necromante_body_bright.png -- ver NecroticAltarRenderer, que la dibuja encima cada
     * frame en full-bright). Ese modelo variante (altar_necromante_bright.json) no está atado a
     * ningún blockstate ni item, así que Forge NUNCA lo cargaría/hornearía por su cuenta -- hay
     * que pedirlo explícitamente acá con ModelEvent.RegisterAdditional para que exista un
     * BakedModel al que el renderer le pueda pedir getModel(...) en render-time.
     */
    @SubscribeEvent
    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        // OJO: el sufijo #inventory hace que ModelBakery busque el JSON en models/item/, NO en
        // models/block/, aunque geométricamente sea un modelo de bloque -- por eso el archivo vive
        // en models/item/altar_necromante_bright.json y no junto al resto de los modelos de bloque
        // (bug real que salió en el log: "Unable to load model ... models/item/block/..." porque
        // originalmente lo tenía mal puesto). El path acá NO lleva "block/" al principio.
        event.register(new ModelResourceLocation(
                new ResourceLocation(NanookMod.MOD_ID, "altar_necromante_bright"), "inventory"));
    }

    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        for (String skinName : event.getSkins()) {
            net.minecraft.client.renderer.entity.EntityRenderer<? extends Player> skinRenderer = event.getSkin(skinName);
            if (skinRenderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer playerRenderer) {
                playerRenderer.addLayer(new NecromanticChainRenderLayer(playerRenderer));
            }
        }
    }

    @SubscribeEvent
    public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
        // Bossbar del NecroticKnight
        event.registerAbove(
                VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(),
                "necrotic_knight_hud",
                new NecroticKnightHudOverlay()
        );
        // --- NUEVO: bossbar de Nanook ---
        event.registerAbove(
                VanillaGuiOverlay.BOSS_EVENT_PROGRESS.id(),
                "nanook_boss_hud",
                new NanookHudOverlay()
        );
        // Vignette de Corrupción
        event.registerBelowAll(
                "corruption_screen_overlay",
                new CorruptionScreenOverlay()
        );
        // Escarcha de pantalla del Permafrost (60 cuadros; el cuadro ES el
        // medidor de cuán congelado estás). Se registra DESPUÉS de la
        // vignette de Corrupción a propósito: Forge respeta el orden de
        // registro, así que si tenés las dos puestas, la escarcha queda
        // encima -- es la que te está matando, tiene que leerse primero.
        event.registerBelowAll(
                "permafrost_frost_overlay",
                new PermafrostFrostOverlay()
        );
        // HUD de Cadena Necromantica
        event.registerAboveAll(
                "necromantic_chain_hud",
                new NecromanticChainHudOverlay()
        );
        // PEDIDO: se sacó el panel de requisitos del Altar Necromante (aparecía al mirarlo) -- el
        // altar ahora comunica todo por mensaje de acción, ver NecroticAltarBlockEntity#showStatus.
    }

    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> level != null && pos != null
                        ? BiomeColors.getAverageGrassColor(level, pos)
                        : GrassColor.getDefaultColor(),
                ModBlocks.CREPUSCULAR_GRASS.get()
        );
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.level != null && mc.player != null) {
                        return BiomeColors.getAverageGrassColor(mc.level, mc.player.blockPosition());
                    }
                    return GrassColor.getDefaultColor();
                },
                ModItems.CREPUSCULAR_GRASS.get()
        );
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            if (ModBlocks.FROST_BREWING_STAND.isPresent()) {
                ItemBlockRenderTypes.setRenderLayer(ModBlocks.FROST_BREWING_STAND.get(), RenderType.cutout());
            }
            if (ModBlocks.ANCIENT_BANNER.isPresent()) {
                ItemBlockRenderTypes.setRenderLayer(ModBlocks.ANCIENT_BANNER.get(), RenderType.cutout());
            }
            if (ModBlocks.CREPUSCULAR_ALTAR.isPresent()) {
                ItemBlockRenderTypes.setRenderLayer(ModBlocks.CREPUSCULAR_ALTAR.get(), RenderType.cutout());
            }

            if (ModMenus.FROST_BREWING_STAND.isPresent()) {
                net.minecraft.client.gui.screens.MenuScreens.register(
                        ModMenus.FROST_BREWING_STAND.get(),
                        FrostBrewingStandScreen::new
                );
            }
        });
    }
}