package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Aquí se registran todos los tipos de entidad del mod.
 * Cuando agreguemos los otros 3 mobs base, cada uno tendrá su propia línea aquí.
 */
public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, NanookMod.MOD_ID);

    public static final RegistryObject<EntityType<NanookEntity>> NANOOK =
            ENTITY_TYPES.register("nanook",
                    () -> EntityType.Builder.of(NanookEntity::new, MobCategory.MONSTER)
                            // Tamaño de la hitbox; Nanook es un boss grande, ajusta según el modelo real.
                            .sized(2.0f, 3.1f)
                            .clientTrackingRange(16)
                            .build("nanook"));

    public static final RegistryObject<EntityType<NanookClawProjectile>> NANOOK_CLAW_PROJECTILE =
            ENTITY_TYPES.register("nanook_claw_projectile",
                    () -> EntityType.Builder.<NanookClawProjectile>of(NanookClawProjectile::new, MobCategory.MISC)
                            .sized(1.0f, 2.4f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("nanook_claw_projectile"));
    public static final RegistryObject<EntityType<NanookIceSpikeProjectile>> NANOOK_ICE_SPIKE =
            ENTITY_TYPES.register("nanook_ice_spike",
                    () -> EntityType.Builder.<NanookIceSpikeProjectile>of(NanookIceSpikeProjectile::new, MobCategory.MISC)
                            .sized(0.8f, 1.6f)
                            .clientTrackingRange(12)
                            .updateInterval(1)
                            .noSave()
                            .build("nanook_ice_spike"));

    public static final RegistryObject<EntityType<NanookIceMeteorProjectile>> NANOOK_ICE_METEOR =
            ENTITY_TYPES.register("nanook_ice_meteor",
                    () -> EntityType.Builder.<NanookIceMeteorProjectile>of(NanookIceMeteorProjectile::new, MobCategory.MISC)
                            .sized(0.9f, 0.9f)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .noSave()
                            .build("nanook_ice_meteor"));

    /**
     * Bloque del terreno que sale volando con el golpe al suelo de Nanook
     * (ataque double_ground). Es puramente decorativo: no toca el mundo, no
     * hace daño y se autodestruye. Ver NanookRisingBlock.
     *
     * noSave(): si el jugador sale del mundo en medio del ataque, no tiene
     * ningún sentido que estos escombros se guarden en el chunk.
     */
    public static final RegistryObject<EntityType<NanookRisingBlock>> NANOOK_RISING_BLOCK =
            ENTITY_TYPES.register("nanook_rising_block",
                    () -> EntityType.Builder.<NanookRisingBlock>of(NanookRisingBlock::new, MobCategory.MISC)
                            .sized(0.9f, 0.9f)
                            .clientTrackingRange(12)
                            .updateInterval(2)
                            .noSave()
                            .fireImmune()
                            .build("nanook_rising_block"));

    /**
     * Cristal de hielo que surge del suelo (abanico de cristales y aterrizaje
     * del salto). Ver NanookCrystal.
     *
     * updateInterval(20): nace quieto y no se mueve nunca, no hace falta que el
     * servidor le mande actualizaciones seguido. Igual que el resto de los
     * efectos: noSave() y fireImmune().
     */
    public static final RegistryObject<EntityType<NanookCrystal>> NANOOK_CRYSTAL =
            ENTITY_TYPES.register("nanook_crystal",
                    () -> EntityType.Builder.<NanookCrystal>of(NanookCrystal::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(12)
                            .updateInterval(20)
                            .noSave()
                            .fireImmune()
                            .build("nanook_crystal"));

    public static final RegistryObject<EntityType<SnowyBlizzEntity>> SNOWY_BLIZZ =
            ENTITY_TYPES.register("snowy_blizz",
                    () -> EntityType.Builder.of(SnowyBlizzEntity::new, MobCategory.CREATURE)
                            .sized(0.6f, 1.95f)
                            .clientTrackingRange(10)
                            .build("snowy_blizz"));

    public static final RegistryObject<EntityType<SnowyBlizzIceball>> SNOWY_BLIZZ_ICEBALL =
            ENTITY_TYPES.register("snowy_blizz_iceball",
                    () -> EntityType.Builder.<SnowyBlizzIceball>of(SnowyBlizzIceball::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("snowy_blizz_iceball"));

    public static final RegistryObject<EntityType<GnutEntity>> GNUT = ENTITY_TYPES.register("gnut",
            () -> EntityType.Builder.of(GnutEntity::new, MobCategory.MONSTER)
                    .sized(0.9F, 1.9F)
                    .clientTrackingRange(8)
                    .build("gnut"));

    public static final RegistryObject<EntityType<NecroticSlashVfx>> NECROTIC_SLASH_VFX =
            ENTITY_TYPES.register("necrotic_slash_vfx",
                    () -> EntityType.Builder.<NecroticSlashVfx>of(NecroticSlashVfx::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSave()
                            .build("necrotic_slash_vfx"));

    public static final RegistryObject<EntityType<NecroticGroundRuptureVfx>> NECROTIC_GROUND_RUPTURE =
            ENTITY_TYPES.register("necrotic_ground_rupture",
                    () -> EntityType.Builder.<NecroticGroundRuptureVfx>of(NecroticGroundRuptureVfx::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .noSave()
                            .build("necrotic_ground_rupture"));

    public static final RegistryObject<EntityType<NecroticLightningBeamEntity>> NECROTIC_LIGHTNING_BEAM =
            ENTITY_TYPES.register("necrotic_lightning_beam",
                    () -> EntityType.Builder.<NecroticLightningBeamEntity>of(NecroticLightningBeamEntity::new, MobCategory.MISC)
                            .sized(0.1f, 0.1f)
                            .clientTrackingRange(16) // el rayo llega hasta 20 bloques, que se siga viendo/interpolando de lejos
                            .updateInterval(1)
                            .noSave()
                            .build("necrotic_lightning_beam"));

    public static final RegistryObject<EntityType<CrowEntity>> CROW = ENTITY_TYPES.register("crow",
            () -> EntityType.Builder.of(CrowEntity::new, MobCategory.CREATURE)
                    .sized(0.5F, 0.4F)
                    .clientTrackingRange(10)
                    .build("crow"));

    public static final RegistryObject<EntityType<SkeletalWarriorEntity>> SKELETAL_WARRIOR =
            ENTITY_TYPES.register("skeletal_warrior",
                    () -> EntityType.Builder.of(SkeletalWarriorEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.95F) // ajustar cuando tengas el .bbmodel exportado y veas el tamaño real
                            .clientTrackingRange(10)
                            .build("skeletal_warrior"));

    public static final RegistryObject<EntityType<SkeletalWarriorAuraVfx>> SKELETAL_WARRIOR_AURA_VFX =
            ENTITY_TYPES.register("skeletal_warrior_aura_vfx",
                    () -> EntityType.Builder.<SkeletalWarriorAuraVfx>of(SkeletalWarriorAuraVfx::new, MobCategory.MISC)
                            .noSave()
                            .sized(1.0F, 2.5F)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("skeletal_warrior_aura_vfx"));

    public static final RegistryObject<EntityType<SkeletalMageEntity>> SKELETAL_MAGE =
            ENTITY_TYPES.register("skeletal_mage",
                    () -> EntityType.Builder.of(SkeletalMageEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.9F)
                            .clientTrackingRange(12)
                            .build("skeletal_mage"));

    public static final RegistryObject<EntityType<SkeletalMageNecroball>> SKELETAL_MAGE_NECROBALL =
            ENTITY_TYPES.register("skeletal_mage_necroball",
                    () -> EntityType.Builder.<SkeletalMageNecroball>of(SkeletalMageNecroball::new, MobCategory.MISC)
                            .noSave()
                            .sized(0.4F, 0.4F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("skeletal_mage_necroball"));

    // --- Twilight Wisp (fantasmita temporal) ---
    public static final RegistryObject<EntityType<TwilightWispEntity>> TWILIGHT_WISP =
            ENTITY_TYPES.register("twilight_wisp",
                    () -> EntityType.Builder.of(TwilightWispEntity::new, MobCategory.MONSTER)
                            .noSave()
                            .sized(0.4F, 0.4F)
                            .clientTrackingRange(10)
                            .build("twilight_wisp"));

    public static final RegistryObject<EntityType<NecroticKnightEntity>> NECROTIC_KNIGHT =
            ENTITY_TYPES.register("necrotic_knight",
                    () -> EntityType.Builder.of(NecroticKnightEntity::new, MobCategory.MONSTER)
                            .sized(1.2F, 2.8F)   // más grande que el Skeletal Knight normal
                            .clientTrackingRange(16)
                            .fireImmune()
                            .build("necrotic_knight"));

    // --- Espejo de prueba del Necrotic Knight (ver NecroticKnightFakeEntity) ---
    public static final RegistryObject<EntityType<NecroticKnightFakeEntity>> NECROTIC_KNIGHT_FAKE =
            ENTITY_TYPES.register("necrotic_knight_fake",
                    () -> EntityType.Builder.of(NecroticKnightFakeEntity::new, MobCategory.MISC)
                            .noSave()
                            .sized(1.2F, 2.8F)
                            .clientTrackingRange(16)
                            .fireImmune()
                            .build("necrotic_knight_fake"));

    public static final RegistryObject<EntityType<TwilightPortalEntity>> TWILIGHT_PORTAL =
            ENTITY_TYPES.register("twilight_portal",
                    () -> EntityType.Builder.<TwilightPortalEntity>of(TwilightPortalEntity::new, MobCategory.MONSTER)
                            .noSave()
                            // Ojo: .sized(width, height) en vanilla arma un
                            // hitbox CUADRADO en planta (mismo "width" para X
                            // y para Z) -- Minecraft no soporta una AABB de
                            // entidad rectangular/asimétrica (ancha para un
                            // lado y angosta para el otro) de fábrica, solo
                            // cubos con base cuadrada. Subimos width de 0.6 a
                            // 2.2 para que el hitbox real se acerque al ancho
                            // visual del remolino (antes era mucho más
                            // angosto que el modelo, costaba pegarle/
                            // interactuar desde los costados). Si con 2.2
                            // sigue sintiéndose chico o grande, este es el
                            // número a tocar.
                            .sized(2.2F, 3.5F)
                            .clientTrackingRange(12)
                            .build("twilight_portal"));

    public static final RegistryObject<EntityType<NecroticKnightPulseVfx>> NECROTIC_KNIGHT_PULSE_VFX =
            ENTITY_TYPES.register("necrotic_knight_pulse_vfx",
                    () -> EntityType.Builder.<NecroticKnightPulseVfx>of(NecroticKnightPulseVfx::new, MobCategory.MISC)
                            .noSave()
                            .sized(3.0F, 3.0F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build("necrotic_knight_pulse_vfx"));
}
