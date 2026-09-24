package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.block.entity.FrostBrewingStandBlockEntity;
import com.nanookmod.block.entity.FrostLanternBlockEntity;
import com.nanookmod.block.entity.NecroticAltarBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, NanookMod.MOD_ID);

    public static final RegistryObject<BlockEntityType<FrostLanternBlockEntity>> FROST_LANTERN =
            BLOCK_ENTITIES.register("frost_lantern", () ->
                    BlockEntityType.Builder.of(FrostLanternBlockEntity::new,
                            ModBlocks.FROST_LANTERN.get()).build(null));

    public static final RegistryObject<BlockEntityType<FrostBrewingStandBlockEntity>> FROST_BREWING_STAND =
            BLOCK_ENTITIES.register("frost_brewing_stand",
                    () -> BlockEntityType.Builder.of(FrostBrewingStandBlockEntity::new, ModBlocks.FROST_BREWING_STAND.get()).build(null));

    public static final RegistryObject<BlockEntityType<NecroticAltarBlockEntity>> NECROTIC_ALTAR =
            BLOCK_ENTITIES.register("necrotic_altar",
                    () -> BlockEntityType.Builder.of(NecroticAltarBlockEntity::new, ModBlocks.CREPUSCULAR_ALTAR.get()).build(null));
}