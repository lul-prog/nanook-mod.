package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.item.custom.FrozenAppleItem;
import com.nanookmod.item.custom.NecromanticChainItem;
import com.nanookmod.item.custom.NecroticScytheItem;
import com.nanookmod.item.custom.SummoningStaffItem;
import net.minecraft.world.item.BlockItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.PotionItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.food.FoodProperties;
import net.minecraftforge.common.ForgeSpawnEggItem;

public class ModItems {
    // Creamos el registro de items usando el ID de tu mod
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, NanookMod.MOD_ID);

    // Registramos el bastón. "stacksTo(1)" hace que no se pueda apilar en el inventario.
    public static final RegistryObject<Item> SUMMONING_STAFF = ITEMS.register("summoning_staff",
            () -> new SummoningStaffItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> FROZEN_APPLE = ITEMS.register("frozen_apple",
            () -> new FrozenAppleItem(new Item.Properties()
                    .stacksTo(1),
                    6,    // nutrition: 6 puntos = 3 muslitos
                    0.8f  // saturationModifier
            ));
    public static final RegistryObject<Item> FROST_DIRT = ITEMS.register("frost_dirt",
            () -> new BlockItem(ModBlocks.FROST_DIRT.get(), new Item.Properties()));

    public static final RegistryObject<Item> FROST_ICE = ITEMS.register("frost_ice",
            () -> new BlockItem(ModBlocks.FROST_ICE.get(), new Item.Properties()));

    public static final RegistryObject<Item> FROST_GRASS = ITEMS.register("frost_grass",
            () -> new BlockItem(ModBlocks.FROST_GRASS.get(), new Item.Properties()));

    public static final RegistryObject<Item> FROST_FARMLAND = ITEMS.register("frost_farmland",
            () -> new BlockItem(ModBlocks.FROST_FARMLAND.get(), new Item.Properties()));

    public static final RegistryObject<Item> FROST_SEEDS = ITEMS.register("frost_seeds",
            () -> new ItemNameBlockItem(ModBlocks.FROST_CROP.get(), new Item.Properties()));

    public static final RegistryObject<Item> FROST_PETAL = ITEMS.register("frost_petal",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> FROST_FUEL = ITEMS.register("frost_fuel",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> FROST_LANTERN = ITEMS.register("frost_lantern",
            () -> new BlockItem(ModBlocks.FROST_LANTERN.get(), new Item.Properties()));

    // Items de la vegetación
    public static final RegistryObject<Item> SNOWY_BUSH_ITEM = ITEMS.register("snowy_bush",
            () -> new BlockItem(ModBlocks.SNOWY_BUSH.get(), new Item.Properties()));

    public static final RegistryObject<Item> DEAD_SNOW_BUSH_ITEM = ITEMS.register("dead_snow_bush",
            () -> new BlockItem(ModBlocks.DEAD_SNOW_BUSH.get(), new Item.Properties()));

    public static final RegistryObject<Item> SNOWY_TALL_GRASS_ITEM = ITEMS.register("snowy_tall_grass",
            () -> new BlockItem(ModBlocks.SNOWY_TALL_GRASS.get(), new Item.Properties()));

    // Items de vegetación alta (2 bloques)
    public static final RegistryObject<Item> SNOWY_DOUBLE_PLANT_FERN_ITEM = ITEMS.register("snowy_double_plant_fern",
            () -> new BlockItem(ModBlocks.SNOWY_DOUBLE_PLANT_FERN.get(), new Item.Properties()));

    public static final RegistryObject<Item> SNOWY_DOUBLE_PLANT_GRASS_ITEM = ITEMS.register("snowy_double_plant_grass",
            () -> new BlockItem(ModBlocks.SNOWY_DOUBLE_PLANT_GRASS.get(), new Item.Properties()));

    public static final RegistryObject<Item> CLAW_PROJECTILE_ITEM = ITEMS.register("nanook_claw_projectile",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> NANOOK_ICE_SPIKE_ITEM = ITEMS.register("nanook_ice_spike",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> NANOOK_ICE_METEOR_ITEM = ITEMS.register("nanook_ice_meteor",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> SNOWY_BLIZZ_ICEBALL_ITEM = ITEMS.register("snowy_blizz_iceball",
            () -> new Item(new Item.Properties()));

    public static final RegistryObject<Item> FROST_BREWING_STAND = ITEMS.register("frost_brewing_stand",
            () -> new BlockItem(ModBlocks.FROST_BREWING_STAND.get(), new Item.Properties()));

    // Botella de Escarcha VACÍA (máx 16)
    public static final RegistryObject<Item> FROST_BOTTLE_EMPTY = ITEMS.register("frost_bottle_empty",
            () -> new com.nanookmod.item.custom.FrostBottleEmptyItem(new Item.Properties().stacksTo(16)));

    // Botella de Escarcha LLENA con agua (máx 1)
    public static final RegistryObject<Item> FROST_BOTTLE = ITEMS.register("frost_bottle",
            () -> new com.nanookmod.item.custom.FrostBottleItem(new Item.Properties().stacksTo(1)));

    // Poción de Inmunidad al Permafrost - MAX 1, con brillo
    public static final RegistryObject<Item> PERMAFROST_POTION = ITEMS.register("permafrost_potion",
            () -> new com.nanookmod.item.custom.PotionItem(new Item.Properties()));

    // Vara de Blizz (drop del mob)
    public static final RegistryObject<Item> BLIZZ_ROD = ITEMS.register("blizz_rod",
            () -> new Item(new Item.Properties()));

    // Polvo de Vara de Blizz (combustible)
    public static final RegistryObject<Item> BLIZZ_ROD_POWDER = ITEMS.register("blizz_rod_powder",
            () -> new Item(new Item.Properties()));

    // Cuerno de Gnut (drop del mob, mismo drop rate que la Vara de Blizz)
    public static final RegistryObject<Item> GNUT_HORN = ITEMS.register("gnut_horn",
            () -> new Item(new Item.Properties()));

    // Poción de Inmunidad al Permafrost MEJORADA (inmunidad total) - MAX 1, con brillo
    public static final RegistryObject<Item> GREATER_PERMAFROST_POTION = ITEMS.register("greater_permafrost_potion",
            () -> new com.nanookmod.item.custom.FrostPotionItem(new Item.Properties()));

    public static final RegistryObject<Item> NECROTIC_SCYTHE = ITEMS.register("necrotic_scythe",
            () -> new NecroticScytheItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> NECROMANTIC_CHAIN = ITEMS.register("necromantic_chain",
            () -> new NecromanticChainItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    // Hueso Necrótico - drop del Skeletal Warrior y del Skeletal Mage (mismo drop rate que el hueso del esqueleto vanilla)
    public static final RegistryObject<Item> NECROTIC_BONE = ITEMS.register("necrotic_bone",
            () -> new Item(new Item.Properties()));

    // Tela Espectral - drop exclusivo del Skeletal Mage (drop rate similar al de la Vara de Blizz)
    public static final RegistryObject<Item> SPECTRAL_CLOTH = ITEMS.register("spectral_cloth",
            () -> new Item(new Item.Properties()));

    // Cabeza Esquelética - drop del Skeletal Warrior y del Skeletal Mage al morir por un creeper cargado (mismo item para ambos).
    // Solo se coloca de pie (ver SkeletalHeadBlock). También se puede equipar como casco (ver SkeletalHeadBlock.Equipable).
    // Es también la tercera ofrenda del altar necromante (ver NecroticAltarBlockEntity.REQUIREMENTS) -- reemplazó al
    // Cráneo Crepuscular, que se sacó del mod por no tener ninguna fuente de obtención en el mundo.
    public static final RegistryObject<Item> SKELETAL_HEAD = ITEMS.register("skeletal_head",
            () -> new BlockItem(ModBlocks.SKELETAL_HEAD.get(), new Item.Properties()));

    // Items de terreno crepuscular
    public static final RegistryObject<Item> CREPUSCULAR_DIRT = ITEMS.register("crepuscular_dirt",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_DIRT.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_GRASS = ITEMS.register("crepuscular_grass",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_GRASS.get(), new Item.Properties()));

    // Items de madera crepuscular
    public static final RegistryObject<Item> CREPUSCULAR_LOG = ITEMS.register("crepuscular_log",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_LOG.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_LEAVES = ITEMS.register("crepuscular_leaves",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_LEAVES.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_SAPLING = ITEMS.register("crepuscular_sapling",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_SAPLING.get(), new Item.Properties()));

    // Bayas de la enredadera crepuscular: es a la vez el item que plantas (coloca
    // ModBlocks.CREPUSCULAR_VINES) y la comida que sueltas al cosechar, igual que
    // vanilla hace con glow_berries/cave_vines.
    public static final RegistryObject<Item> CREPUSCULAR_BERRIES = ITEMS.register("crepuscular_berries",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_VINES.get(), new Item.Properties()
                    .food(new FoodProperties.Builder()
                            .nutrition(2)
                            .saturationMod(0.1F)
                            .build())));

    public static final RegistryObject<Item> CREPUSCULAR_PLANKS = ITEMS.register("crepuscular_planks",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_PLANKS.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_WOOD = ITEMS.register("crepuscular_wood",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_WOOD.get(), new Item.Properties()));

    // Variantes de madera crepuscular (misma textura que las planks)
    public static final RegistryObject<Item> CREPUSCULAR_STAIRS = ITEMS.register("crepuscular_stairs",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_STAIRS.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_SLAB = ITEMS.register("crepuscular_slab",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_SLAB.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_FENCE = ITEMS.register("crepuscular_fence",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_FENCE.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_FENCE_GATE = ITEMS.register("crepuscular_fence_gate",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_FENCE_GATE.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_BUTTON = ITEMS.register("crepuscular_button",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_BUTTON.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_PRESSURE_PLATE = ITEMS.register("crepuscular_pressure_plate",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_PRESSURE_PLATE.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_SPROUT = ITEMS.register("crepuscular_sprout",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_SPROUT.get(), new Item.Properties()));

    // Item del Altar
    public static final RegistryObject<Item> CREPUSCULAR_ALTAR = ITEMS.register("crepuscular_altar",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_ALTAR.get(), new Item.Properties()));

    // Estandarte Antiguo (item)
    public static final RegistryObject<Item> ANCIENT_BANNER = ITEMS.register("ancient_banner",
            () -> new BlockItem(ModBlocks.ANCIENT_BANNER.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_SAND = ITEMS.register("crepuscular_sand",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_SAND.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_SANDSTONE = ITEMS.register("crepuscular_sandstone",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_SANDSTONE.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_REED = ITEMS.register("crepuscular_reed",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_REED.get(), new Item.Properties()));

    public static final RegistryObject<Item> CREPUSCULAR_MIRE_BLOOM = ITEMS.register("crepuscular_mire_bloom",
            () -> new BlockItem(ModBlocks.CREPUSCULAR_MIRE_BLOOM.get(), new Item.Properties()));

    public static final RegistryObject<Item> NECRO_BALL_ITEM = ITEMS.register("necro_ball",
            () -> new Item(new Item.Properties()));

    // ============================================
    // HUEVOS DE GENERACIÓN (spawn eggs)
    // Colores: primaryColor = color de fondo del huevo, secondaryColor = color de las manchas.
    // Para cambiar los colores de cualquiera, edita los dos valores 0xRRGGBB de su línea aquí abajo.
    // ============================================

    public static final RegistryObject<Item> NANOOK_SPAWN_EGG = ITEMS.register("nanook_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.NANOOK, 0xbcd3d6, 0x555170, new Item.Properties()));

    public static final RegistryObject<Item> SKELETAL_WARRIOR_SPAWN_EGG = ITEMS.register("skeletal_warrior_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SKELETAL_WARRIOR, 0x836a7d, 0x836a7d, new Item.Properties()));

    public static final RegistryObject<Item> SKELETAL_MAGE_SPAWN_EGG = ITEMS.register("skeletal_mage_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SKELETAL_MAGE, 0x39455b, 0x9df4de, new Item.Properties()));

    public static final RegistryObject<Item> NECROTIC_KNIGHT_SPAWN_EGG = ITEMS.register("necrotic_knight_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.NECROTIC_KNIGHT, 0xcbfecc, 0x44edbd, new Item.Properties()));

    public static final RegistryObject<Item> GNUT_SPAWN_EGG = ITEMS.register("gnut_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.GNUT, 0xeae7de, 0xcdc5ae, new Item.Properties()));

    public static final RegistryObject<Item> SNOWY_BLIZZ_SPAWN_EGG = ITEMS.register("snowy_blizz_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.SNOWY_BLIZZ, 0xdde8de, 0x3c91c9, new Item.Properties()));

    public static final RegistryObject<Item> CROW_SPAWN_EGG = ITEMS.register("crow_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.CROW, 0x1A1A1A, 0x4A4A4A, new Item.Properties()));

    public static final RegistryObject<Item> TWILIGHT_WISP_SPAWN_EGG = ITEMS.register("twilight_wisp_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.TWILIGHT_WISP, 0xe9ffea, 0x1fd4bd, new Item.Properties()));
}