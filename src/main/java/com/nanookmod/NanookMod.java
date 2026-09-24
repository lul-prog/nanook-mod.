package com.nanookmod;

import com.nanookmod.capability.NecromanticChainCapability;
import com.nanookmod.entity.*;
import com.nanookmod.registry.*;
import com.nanookmod.world.biome.region.MarPrimigenioRegion;
import com.nanookmod.world.biome.region.BosqueCrepuscularRegion;
import com.nanookmod.world.biome.surface.MarPrimigenioSurfaceRules;
import com.nanookmod.world.biome.surface.BosqueCrepuscularSurfaceRules;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraft.world.level.levelgen.SurfaceRules;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import software.bernie.geckolib.GeckoLib;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;

@Mod(NanookMod.MOD_ID)
public class NanookMod {
    public static final String MOD_ID = "nanookmod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public NanookMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        GeckoLib.initialize();

        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModFeatures.FEATURES.register(modEventBus);
        ModTrunkPlacerTypes.TRUNK_PLACER_TYPES.register(modEventBus);
        ModFoliagePlacerTypes.FOLIAGE_PLACER_TYPES.register(modEventBus);
        ModTreeDecoratorTypes.TREE_DECORATOR_TYPES.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModParticles.PARTICLES.register(modEventBus);
        ModSounds.SOUNDS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerAttributes);
        modEventBus.addListener(this::clientSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Nanook Mod: inicializando.");
        event.enqueueWork(ModSpawnHandlers::register);
        event.enqueueWork(com.nanookmod.network.ModNetworking::register);

        // Registro EXPLÍCITO del sapling en la maceta vacía. El constructor
        // "de mod" de FlowerPotBlock (usado en ModBlocks para crear
        // POTTED_CREPUSCULAR_SAPLING) NO siempre completa por sí solo el mapa
        // interno que usa la maceta vacía para saber qué bloque mostrar al
        // recibir el sapling (bug conocido de Forge); por eso hay que
        // avisarle explícitamente con addPlant() acá.
        event.enqueueWork(() -> {
            ((FlowerPotBlock) Blocks.FLOWER_POT).addPlant(
                    new ResourceLocation(MOD_ID, "crepuscular_sapling"),
                    ModBlocks.POTTED_CREPUSCULAR_SAPLING);
            LOGGER.info("Nanook Mod: Crepuscular Sapling registrado en maceta");
        });

        event.enqueueWork(() -> {
            Regions.register(new MarPrimigenioRegion(1));
            LOGGER.info("Nanook Mod: Región Mar Primigenio registrada");
            Regions.register(new BosqueCrepuscularRegion(1));
            LOGGER.info("Nanook Mod: Región Bosque Crepuscular registrada");

            // IMPORTANTE: TerraBlender solo espera UN registro de surface rules por
            // mod id. Si se llama addSurfaceRules(..., MOD_ID, ...) más de una vez,
            // la segunda llamada puede pisar/reemplazar a la primera y sus reglas
            // dejan de aplicarse (esto era el causante de que mar_primigenio
            // generara bloques vanilla en vez de frost_dirt/frost_grass).
            // Por eso combinamos ambos RuleSource en una sola llamada.
            SurfaceRules.RuleSource combinedRules = SurfaceRules.sequence(
                    MarPrimigenioSurfaceRules.makeRules(),
                    BosqueCrepuscularSurfaceRules.makeRules()
            );

            SurfaceRuleManager.addSurfaceRules(
                    SurfaceRuleManager.RuleCategory.OVERWORLD,
                    MOD_ID,
                    combinedRules
            );
            LOGGER.info("Nanook Mod: Surface rules registradas (Mar Primigenio + Bosque Crepuscular)");
        });
    }

    private void registerAttributes(final EntityAttributeCreationEvent event) {
        event.put(ModEntities.NANOOK.get(), NanookEntity.createAttributes().build());
        event.put(ModEntities.SNOWY_BLIZZ.get(), SnowyBlizzEntity.createAttributes().build());
        event.put(ModEntities.GNUT.get(), GnutEntity.createAttributes().build());
        event.put(ModEntities.CROW.get(), CrowEntity.createAttributes().build());
        event.put(ModEntities.SKELETAL_WARRIOR.get(), SkeletalWarriorEntity.createAttributes().build());
        event.put(ModEntities.SKELETAL_MAGE.get(), SkeletalMageEntity.createAttributes().build());
        event.put(ModEntities.TWILIGHT_WISP.get(), TwilightWispEntity.createAttributes().build());
        event.put(ModEntities.NECROTIC_KNIGHT.get(), NecroticKnightEntity.createAttributes().build());
        event.put(ModEntities.NECROTIC_KNIGHT_FAKE.get(), NecroticKnightEntity.createAttributes().build());
        event.put(ModEntities.TWILIGHT_PORTAL.get(), TwilightPortalEntity.createAttributes().build());

    }

    @OnlyIn(Dist.CLIENT)
    private void clientSetup(final FMLClientSetupEvent event) {
        LOGGER.info("Nanook Mod: Configuración del cliente iniciada");
    }
}