package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.world.feature.foliage.CrepuscularSpheroidFoliagePlacer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacerType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModFoliagePlacerTypes {
    public static final DeferredRegister<FoliagePlacerType<?>> FOLIAGE_PLACER_TYPES =
            DeferredRegister.create(Registries.FOLIAGE_PLACER_TYPE, NanookMod.MOD_ID);

    public static final RegistryObject<FoliagePlacerType<CrepuscularSpheroidFoliagePlacer>> SPHEROID =
            FOLIAGE_PLACER_TYPES.register("spheroid",
                    () -> new FoliagePlacerType<>(CrepuscularSpheroidFoliagePlacer.CODEC));
}
