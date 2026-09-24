package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.world.feature.decorator.CrepuscularVineTreeDecorator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.treedecorators.TreeDecoratorType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModTreeDecoratorTypes {
    public static final DeferredRegister<TreeDecoratorType<?>> TREE_DECORATOR_TYPES =
            DeferredRegister.create(Registries.TREE_DECORATOR_TYPE, NanookMod.MOD_ID);

    public static final RegistryObject<TreeDecoratorType<CrepuscularVineTreeDecorator>> CREPUSCULAR_VINE =
            TREE_DECORATOR_TYPES.register("crepuscular_vine_decorator",
                    () -> new TreeDecoratorType<>(CrepuscularVineTreeDecorator.CODEC));
}
