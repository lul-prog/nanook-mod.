package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro central de SoundEvents del mod. Hasta ahora el mod no tenía
 * sonidos propios (todo usaba SoundEvents vanilla); esta clase arranca ese
 * registro para poder sumar los sonidos portados de packs comprados
 * (Awakened Necromancer, etc.) sin ensuciar otros registries.
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, NanookMod.MOD_ID);

    public static final RegistryObject<SoundEvent> NECROTIC_SLASH = registerSound("necrotic_scythe_slash");
    public static final RegistryObject<SoundEvent> NECROTIC_SPIN_SLASH = registerSound("necrotic_scythe_spin_slash");
    public static final RegistryObject<SoundEvent> NECROTIC_RUPTURE = registerSound("necrotic_scythe_rupture");
    public static final RegistryObject<SoundEvent> NECROTIC_HIT_IMPACT = registerSound("necrotic_scythe_hit_impact");

    // Ambientación del bioma "bosque crepuscular"
    public static final RegistryObject<SoundEvent> AMBIENT_CREPUSCULAR_WIND = registerSound("ambient.bosque_crepuscular.wind");
    public static final RegistryObject<SoundEvent> AMBIENT_CREPUSCULAR_CROW = registerSound("ambient.bosque_crepuscular.crow");
    public static final RegistryObject<SoundEvent> AMBIENT_CREPUSCULAR_BRANCH_CREAK = registerSound("ambient.bosque_crepuscular.branch_creak");
    public static final RegistryObject<SoundEvent> AMBIENT_CREPUSCULAR_WHISPERS = registerSound("ambient.bosque_crepuscular.whispers");
    public static final RegistryObject<SoundEvent> AMBIENT_CREPUSCULAR_CHAINS = registerSound("ambient.bosque_crepuscular.chains");

    // Ambientación del bioma "mar primigenio"
    public static final RegistryObject<SoundEvent> AMBIENT_MAR_PRIMIGENIO_WIND = registerSound("ambient.mar_primigenio.wind");


    // Cuervo: sonido especial al empezar a vigilar al jugador (easter egg del pack original)
    public static final RegistryObject<SoundEvent> ENTITY_CROW_NOTICE = registerSound("entity.crow.notice");

    // Tema de batalla del NecroticKnight (arranca al invocarlo, fade out al morir -- ver
    // NecroticKnightMusicHandler / NecroticKnightMusicSound en el cliente).
    public static final RegistryObject<SoundEvent> NECROTIC_KNIGHT_THEME = registerSound("necrotic_knight_theme");

    // Tema de batalla de Nanook ("Throne of Permafrost"). Suena mientras pelea con
    // un jugador cercano, con fade in/out -- ver NanookMusicHandler / NanookMusicSound
    // en el cliente. A diferencia del Knight, NO va atado a que el jefe exista: Nanook
    // es un jefe que recorre el mundo, así que se decide por combate y distancia.
    public static final RegistryObject<SoundEvent> NANOOK_THEME = registerSound("nanook_theme");

    // Voz/combate del NecroticKnight. death y sweep_attack tienen 3 variantes
    // cada uno en sounds.json (arrays) -- vanilla elige una al azar cada vez
    // que se dispara el SoundEvent, no hace falta un RegistryObject por
    // variante.
    public static final RegistryObject<SoundEvent> NECROKNIGHT_SPAWN = registerSound("entity.necroknight.spawn");
    public static final RegistryObject<SoundEvent> NECROKNIGHT_IDLE = registerSound("entity.necroknight.idle");
    public static final RegistryObject<SoundEvent> NECROKNIGHT_HURT = registerSound("entity.necroknight.hurt");
    public static final RegistryObject<SoundEvent> NECROKNIGHT_DEATH = registerSound("entity.necroknight.death");
    public static final RegistryObject<SoundEvent> NECROKNIGHT_SWEEP_ATTACK = registerSound("entity.necroknight.sweep_attack");
    public static final RegistryObject<SoundEvent> NECROKNIGHT_LIGHTNING_WINDUP = registerSound("entity.necroknight.lightning_windup");
    public static final RegistryObject<SoundEvent> NECROKNIGHT_LIGHTNING_BEAM = registerSound("entity.necroknight.lightning_beam");
    public static final RegistryObject<SoundEvent> NECROKNIGHT_LIGHTNING_HIT = registerSound("entity.necroknight.lightning_hit");


    private static RegistryObject<SoundEvent> registerSound(String name) {
        ResourceLocation id = new ResourceLocation(NanookMod.MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}