package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.block.custom.*;
import com.nanookmod.world.feature.CrepuscularTreeGrower;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import com.nanookmod.block.custom.AncientBannerBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.block.state.properties.BlockSetType;

public class ModBlocks {
    // Creamos el registro de bloques
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, NanookMod.MOD_ID);

    // Cabeza Esquelética: drop del Skeletal Warrior/Mage al morir por creeper cargado.
    // Se puede colocar como trofeo y equipar como casco (ver SkeletalHeadBlock).
    public static final RegistryObject<Block> SKELETAL_HEAD = BLOCKS.register("skeletal_head",
            () -> new SkeletalHeadBlock(BlockBehaviour.Properties.of()
                    .noOcclusion()
                    .strength(1.0F)
                    .sound(SoundType.BONE_BLOCK)));

    // 1. Tierra Helada (Frost Dirt)
    public static final RegistryObject<Block> FROST_DIRT = BLOCKS.register("frost_dirt",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)));

    // Tierra del Bosque Crepuscular (PLACEHOLDER: textura de eternal_starlight:nightfall_dirt,
    // reemplazar por textura propia mas adelante)
    public static final RegistryObject<Block> CREPUSCULAR_DIRT = BLOCKS.register("crepuscular_dirt",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.DIRT)));

    // Pasto del Bosque Crepuscular (PLACEHOLDER: textura de eternal_starlight:nightfall_grass_block,
    // reemplazar por textura propia mas adelante)
    public static final RegistryObject<Block> CREPUSCULAR_GRASS = BLOCKS.register("crepuscular_grass",
            () -> new SpreadingGrassBlock(BlockBehaviour.Properties.copy(Blocks.GRASS_BLOCK)
                    .sound(SoundType.GRASS),
                    () -> CREPUSCULAR_DIRT.get()));


    public static final RegistryObject<Block> FROST_ICE = BLOCKS.register("frost_ice",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.ICE)
                    .sound(SoundType.GLASS)
                    .noOcclusion()));

    // 3. Pasto Helado (Frost Grass)
    public static final RegistryObject<Block> FROST_GRASS = BLOCKS.register("frost_grass",
            () -> new SpreadingGrassBlock(BlockBehaviour.Properties.copy(Blocks.GRASS_BLOCK)
                    .sound(SoundType.GRASS),
                    () -> FROST_DIRT.get()));

    // 4. Tierra de Cultivo Helada (Frost Farmland)
    public static final RegistryObject<Block> FROST_FARMLAND = BLOCKS.register("frost_farmland",
            () -> new FrostFarmland(BlockBehaviour.Properties.copy(Blocks.FARMLAND)
                    .sound(SoundType.GLASS)));

    // Planta gélida (el cultivo en sí)
    public static final RegistryObject<Block> FROST_CROP = BLOCKS.register("frost_crop",
            () -> new FrostCrop(BlockBehaviour.Properties.copy(Blocks.WHEAT)
                    .noCollission()));

    // Linterna Gélida
    public static final RegistryObject<Block> FROST_LANTERN = BLOCKS.register("frost_lantern",
            () -> new FrostLanternBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .lightLevel(state -> {
                        int fuelState = state.getValue(FrostLanternBlock.FUEL_STATE);
                        if (fuelState == 2) return 14;
                        if (fuelState == 1) return 7;
                        return 0;
                    })
                    .noOcclusion()
                    .sound(SoundType.LANTERN)));

    // Snowy Bush (Arbusto nevado)
    public static final RegistryObject<Block> SNOWY_BUSH = BLOCKS.register("snowy_bush",
            () -> new FrostBushBlock(BlockBehaviour.Properties.copy(Blocks.DEAD_BUSH)
                    .strength(0.0F)
                    .sound(SoundType.GRASS)
                    .noCollission()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));

    // Dead Snow Bush (Arbusto muerto)
    public static final RegistryObject<Block> DEAD_SNOW_BUSH = BLOCKS.register("dead_snow_bush",
            () -> new FrostBushBlock(BlockBehaviour.Properties.copy(Blocks.DEAD_BUSH)
                    .strength(0.0F)
                    .sound(SoundType.GRASS)
                    .noCollission()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));

    // Snowy Tall Grass (Pasto alto)
    public static final RegistryObject<Block> SNOWY_TALL_GRASS = BLOCKS.register("snowy_tall_grass",
            () -> new FrostBushBlock(BlockBehaviour.Properties.copy(Blocks.FERN)
                    .strength(0.0F)
                    .sound(SoundType.GRASS)
                    .noCollission()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));
    // ============================================
    // VEGETACIÓN ALTA (2 bloques de altura)
    // ============================================

    // Snowy Double Plant Fern (Arbusto/Helecho alto)
    public static final RegistryObject<Block> SNOWY_DOUBLE_PLANT_FERN = BLOCKS.register("snowy_double_plant_fern",
            () -> new FrostDoublePlantBlock(BlockBehaviour.Properties.copy(Blocks.LARGE_FERN)
                    .strength(0.0F)
                    .sound(SoundType.GRASS)
                    .noCollission()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));

    // Snowy Double Plant Grass (Pasto alto)
    public static final RegistryObject<Block> SNOWY_DOUBLE_PLANT_GRASS = BLOCKS.register("snowy_double_plant_grass",
            () -> new FrostDoublePlantBlock(BlockBehaviour.Properties.copy(Blocks.TALL_GRASS)
                    .strength(0.0F)
                    .sound(SoundType.GRASS)
                    .noCollission()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));

    public static final RegistryObject<Block> FROST_BREWING_STAND = BLOCKS.register("frost_brewing_stand",
            () -> new FrostBrewingStandBlock(BlockBehaviour.Properties.of()
                    .strength(3.0F)
                    .lightLevel(state -> 2)
                    .sound(SoundType.STONE)));

// ... (dentro de la clase ModBlocks)

    // ============================================
    // MADERA CREPUSCULAR (Sonidos del Nether, Dureza Vanilla)
    // ============================================

    // Tronco: 2.0F (igual que OAK_LOG)
    public static final RegistryObject<Block> CREPUSCULAR_LOG = BLOCKS.register("crepuscular_log",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.CRIMSON_STEM)
                    .strength(2.0F)
                    .sound(SoundType.NETHER_WOOD)));

    // Hojas: 0.2F (igual que OAK_LEAVES). Sonido de hojas, NO de madera del nether
    // (antes se pisaba el sonido de OAK_LEAVES con NETHER_WOOD, por eso sonaban a tablón).
    public static final RegistryObject<Block> CREPUSCULAR_LEAVES = BLOCKS.register("crepuscular_leaves",
            () -> new CrepuscularLeavesBlock(BlockBehaviour.Properties.copy(Blocks.OAK_LEAVES)
                    .strength(0.2F)
                    .sound(SoundType.GRASS)));

    // Retoño (sapling) del árbol crepuscular: usa el SaplingBlock vanilla,
    // solo le indicamos con qué "grower" crece. CrepuscularTreeGrower apunta
    // al mismo configured_feature "nanookmod:crepuscular_tree" que ya se usa
    // para la generación natural del árbol en el mundo, así que el árbol que
    // sale del sapling es idéntico al que genera el bioma.
    // crepuscular_dirt y crepuscular_grass ya están en el tag minecraft:dirt
    // (ver data/minecraft/tags/blocks/dirt.json), que es lo que SaplingBlock
    // revisa por defecto en canSurvive, así que no hace falta lógica propia.
    public static final RegistryObject<Block> CREPUSCULAR_SAPLING = BLOCKS.register("crepuscular_sapling",
            () -> new SaplingBlock(new CrepuscularTreeGrower(),
                    BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING)));

    // Versión en maceta del sapling (igual que potted_oak_sapling vanilla).
    // Debe registrarse DESPUÉS de CREPUSCULAR_SAPLING: el constructor de
    // FlowerPotBlock resuelve el Supplier del contenido al construirse, y
    // necesita que CREPUSCULAR_SAPLING ya exista.
    public static final RegistryObject<Block> POTTED_CREPUSCULAR_SAPLING = BLOCKS.register("potted_crepuscular_sapling",
            () -> new FlowerPotBlock(() -> (FlowerPotBlock) Blocks.FLOWER_POT,
                    () -> ModBlocks.CREPUSCULAR_SAPLING.get(),
                    BlockBehaviour.Properties.copy(Blocks.POTTED_OAK_SAPLING)));

    // Planks: 2.0F (igual que OAK_PLANKS)
    public static final RegistryObject<Block> CREPUSCULAR_PLANKS = BLOCKS.register("crepuscular_planks",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.CRIMSON_PLANKS)
                    .strength(2.0F)
                    .sound(SoundType.NETHER_WOOD)));

    // Madera Crepuscular (equivalente a OAK_WOOD): mismo bloque que el tronco pero
    // con la textura de corteza (side) también en las caras de arriba/abajo, sin
    // los anillos del top/bottom. Se usa para decorativos tipo tronco roto/partido.
    public static final RegistryObject<Block> CREPUSCULAR_WOOD = BLOCKS.register("crepuscular_wood",
            () -> new RotatedPillarBlock(BlockBehaviour.Properties.copy(Blocks.CRIMSON_STEM)
                    .strength(2.0F)
                    .sound(SoundType.NETHER_WOOD)));

    // ============================================
    // VARIANTES DE MADERA CREPUSCULAR (reutilizan la textura de crepuscular_planks,
    // sin arte propio: escaleras, losa, valla, portón de valla, botón y placa de
    // presión). Puerta y trampilla quedan fuera porque necesitan textura única.
    // ============================================

    public static final RegistryObject<Block> CREPUSCULAR_STAIRS = BLOCKS.register("crepuscular_stairs",
            () -> new StairBlock(() -> CREPUSCULAR_PLANKS.get().defaultBlockState(),
                    BlockBehaviour.Properties.copy(CREPUSCULAR_PLANKS.get())));

    public static final RegistryObject<Block> CREPUSCULAR_SLAB = BLOCKS.register("crepuscular_slab",
            () -> new SlabBlock(BlockBehaviour.Properties.copy(CREPUSCULAR_PLANKS.get())));

    public static final RegistryObject<Block> CREPUSCULAR_FENCE = BLOCKS.register("crepuscular_fence",
            () -> new FenceBlock(BlockBehaviour.Properties.copy(CREPUSCULAR_PLANKS.get())));

    public static final RegistryObject<Block> CREPUSCULAR_FENCE_GATE = BLOCKS.register("crepuscular_fence_gate",
            () -> new FenceGateBlock(BlockBehaviour.Properties.copy(CREPUSCULAR_PLANKS.get()), WoodType.CRIMSON));

    public static final RegistryObject<Block> CREPUSCULAR_BUTTON = BLOCKS.register("crepuscular_button",
            () -> new ButtonBlock(BlockBehaviour.Properties.copy(CREPUSCULAR_PLANKS.get())
                    .noCollission()
                    .strength(0.5F),
                    BlockSetType.CRIMSON, 30, true));

    public static final RegistryObject<Block> CREPUSCULAR_PRESSURE_PLATE = BLOCKS.register("crepuscular_pressure_plate",
            () -> new PressurePlateBlock(PressurePlateBlock.Sensitivity.EVERYTHING,
                    BlockBehaviour.Properties.copy(CREPUSCULAR_PLANKS.get())
                            .noCollission()
                            .strength(0.5F),
                    BlockSetType.CRIMSON));

    // Sprout Crepuscular: planta pequeña sin colisión que emite luz (nivel 15).
    // Solo se cosecha con tijeras o toque de seda; con cualquier otra
    // herramienta no dropea nada (ver loot table).
    public static final RegistryObject<Block> CREPUSCULAR_SPROUT = BLOCKS.register("crepuscular_sprout",
            () -> new CrepuscularSproutBlock(BlockBehaviour.Properties.copy(Blocks.GLOW_LICHEN)
                    .strength(0.0F)
                    .sound(SoundType.GRASS)
                    .lightLevel(state -> 9)
                    .noCollission()
                    .noOcclusion()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));

    // ============================================
    // ENREDADERA DE BAYAS CREPUSCULAR (inspirada en Eternal Starlight:berries_vines)
    // Reutiliza las clases vanilla CaveVinesBlock/CaveVinesPlantBlock: mismo sistema
    // que las cave vines del End/cuevas (crece hacia abajo, tiene bayas cosechables,
    // se puede propagar con hueso). PLACEHOLDER: texturas propias pendientes.
    // ============================================

    // Cabeza de la enredadera: se coloca en el bloque de origen (rama/techo) y crece hacia abajo
    public static final RegistryObject<Block> CREPUSCULAR_VINES = BLOCKS.register("crepuscular_vines",
            () -> new CrepuscularVinesBlock(BlockBehaviour.Properties.copy(Blocks.CAVE_VINES)
                    .lightLevel(CaveVines.emission(14)),
                    () -> ModBlocks.CREPUSCULAR_VINES_PLANT.get(),
                    () -> ModItems.CREPUSCULAR_BERRIES.get()));

    // Cuerpo de la enredadera: los segmentos que se repiten hacia abajo
    public static final RegistryObject<Block> CREPUSCULAR_VINES_PLANT = BLOCKS.register("crepuscular_vines_plant",
            () -> new CrepuscularVinesPlantBlock(BlockBehaviour.Properties.copy(Blocks.CAVE_VINES_PLANT)
                    .lightLevel(CaveVines.emission(14)),
                    () -> (GrowingPlantHeadBlock) ModBlocks.CREPUSCULAR_VINES.get(),
                    () -> ModItems.CREPUSCULAR_BERRIES.get()));


    // ============================================
    // ALTAR CREPUSCULAR (Modelo 3D, Inrompible en Survival)
    // ============================================

    // strength(-1.0F, 3600000.0F) lo hace inrompible en survival (como la Bedrock o el Portal del End)
    // noOcclusion() es OBLIGATORIO para que el modelo 3D no tenga caras invisibles o bugs de luz
    // Pasó de Block a NecroticAltarBlock (BaseEntityBlock) para llevar el BlockEntity del
    // sistema de ofrendas -- ver NecroticAltarBlock / NecroticAltarBlockEntity. Las propiedades
    // (inrompible en survival, sonido de madera del Nether, noOcclusion) NO cambiaron.
    public static final RegistryObject<Block> CREPUSCULAR_ALTAR = BLOCKS.register("crepuscular_altar",
            () -> new com.nanookmod.block.custom.NecroticAltarBlock(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .sound(SoundType.NETHER_WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> ANCIENT_BANNER = BLOCKS.register("ancient_banner",
            () -> new AncientBannerBlock(BlockBehaviour.Properties.of()
                    .noCollission()
                    .noOcclusion()  // ← ESTO ES OBLIGATORIO
                    .strength(1.0F)
                    .sound(SoundType.WOOD)));

    // ============================================
    // ARENA Y ARENISCA CREPUSCULAR (orillas de río)
    // ============================================

    // Arena Crepuscular: usa SandBlock igual que la vanilla, así cae por gravedad,
    // hace el mismo sonido y funciona igual con pistones/gravedad/sofocación.
    // El int es el "falling color" que usan las partículas al caer/romperse
    // (PLACEHOLDER de color, mismo criterio que crepuscular_dirt/grass).
    public static final RegistryObject<Block> CREPUSCULAR_SAND = BLOCKS.register("crepuscular_sand",
            () -> new SandBlock(0xA89678, BlockBehaviour.Properties.copy(Blocks.SAND)));

    // Arenisca Crepuscular: bloque sólido normal, igual que SANDSTONE vanilla.
    public static final RegistryObject<Block> CREPUSCULAR_SANDSTONE = BLOCKS.register("crepuscular_sandstone",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.SANDSTONE)));

    // ============================================
    // VEGETACIÓN DE PANTANO (crece solo sobre arena/barro crepuscular)
    // ============================================

    // Junco Crepuscular: planta de 2 bloques de altura (como TALL_GRASS/LARGE_FERN),
    // para las orillas de los parches de arena/barro y las mini lagunas.
    public static final RegistryObject<Block> CREPUSCULAR_REED = BLOCKS.register("crepuscular_reed",
            () -> new CrepuscularMireDoublePlantBlock(BlockBehaviour.Properties.copy(Blocks.LARGE_FERN)
                    .strength(0.0F)
                    .sound(SoundType.WET_GRASS)
                    .noCollission()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));

    // Flor de Fango Crepuscular: planta pequeña con un leve brillo (esporas),
    // igual de baja que una flor normal.
    public static final RegistryObject<Block> CREPUSCULAR_MIRE_BLOOM = BLOCKS.register("crepuscular_mire_bloom",
            () -> new CrepuscularMirePlantBlock(BlockBehaviour.Properties.copy(Blocks.ALLIUM)
                    .strength(0.0F)
                    .sound(SoundType.WET_GRASS)
                    .lightLevel(state -> 4)
                    .noCollission()
                    .offsetType(BlockBehaviour.OffsetType.XZ)));
}