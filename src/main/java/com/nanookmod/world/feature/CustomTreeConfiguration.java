package com.nanookmod.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Le dice a CustomTreeStructureFeature QUÉ archivo .nbt cargar y en qué
 * posición local de esa estructura está la base del tronco. Así, cada
 * variante de árbol (el actual + los que agregues después) es solo un
 * configured_feature nuevo con estos dos datos, sin escribir Java de nuevo.
 */
public class CustomTreeConfiguration implements FeatureConfiguration {

    public static final Codec<CustomTreeConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(c -> c.structureId),
            BlockPos.CODEC.fieldOf("trunk_offset").forGetter(c -> c.trunkOffset)
    ).apply(instance, CustomTreeConfiguration::new));

    public final ResourceLocation structureId;
    public final BlockPos trunkOffset;

    public CustomTreeConfiguration(ResourceLocation structureId, BlockPos trunkOffset) {
        this.structureId = structureId;
        this.trunkOffset = trunkOffset;
    }
}