package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.world.feature.trunk.CrepuscularBranchingTrunkPlacer;
import com.nanookmod.world.feature.trunk.TwistedGiantTrunkPlacer;
import com.nanookmod.world.feature.trunk.TwistedTrunkPlacer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModTrunkPlacerTypes {
    public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACER_TYPES =
            DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, NanookMod.MOD_ID);

    public static final RegistryObject<TrunkPlacerType<TwistedGiantTrunkPlacer>> TWISTED_GIANT =
            TRUNK_PLACER_TYPES.register("twisted_giant",
                    () -> new TrunkPlacerType<>(TwistedGiantTrunkPlacer.CODEC));

    public static final RegistryObject<TrunkPlacerType<TwistedTrunkPlacer>> TWISTED_SINGLE =
            TRUNK_PLACER_TYPES.register("twisted_single",
                    () -> new TrunkPlacerType<>(TwistedTrunkPlacer.CODEC));

    public static final RegistryObject<TrunkPlacerType<CrepuscularBranchingTrunkPlacer>> BRANCHING =
            TRUNK_PLACER_TYPES.register("branching",
                    () -> new TrunkPlacerType<>(CrepuscularBranchingTrunkPlacer.CODEC));
}