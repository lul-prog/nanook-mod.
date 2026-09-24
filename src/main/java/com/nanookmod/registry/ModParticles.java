package com.nanookmod.registry;

import com.nanookmod.NanookMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Partículas propias del mod. Ahora mismo solo tenemos la de "mob
 * potenciado por la Noche Escarchada" (ver FrostedBuffParticle).
 */
public class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, NanookMod.MOD_ID);

    public static final RegistryObject<SimpleParticleType> FROSTED_BUFF =
            PARTICLES.register("frosted_buff", () -> new SimpleParticleType(false));

    // ============================================
    // Ambientación del Bosque Crepuscular
    // ============================================

    /** Motas cálidas tipo esporas/luciérnagas que flotan por el bioma. */
    public static final RegistryObject<SimpleParticleType> CREPUSCULAR_MOTE =
            PARTICLES.register("crepuscular_mote", () -> new SimpleParticleType(false));

    /** Niebla baja y sutil que se arrastra a nivel de piso. */
    public static final RegistryObject<SimpleParticleType> CREPUSCULAR_MIST =
            PARTICLES.register("crepuscular_mist", () -> new SimpleParticleType(false));

    /** Hoja que cae ocasionalmente de crepuscular_leaves. */
    public static final RegistryObject<SimpleParticleType> CREPUSCULAR_LEAF =
            PARTICLES.register("crepuscular_leaf", () -> new SimpleParticleType(false));

    // NOTA: la pasiva de Corrupción usa partículas DUST vanilla (ver
    // CorruptionEffect), no una de sprite propia.

    // NOTA: CORRUPTION_SPARK (más abajo) quedó sin usar -- se probó para
    // la evaporación del wave y para el tick de Corrupción, pero al final
    // el wave se resuelve con geometría/shader (ver NecroticWaveRenderer)
    // y Corrupción usa CORRUPTION_PARTICLE. Se deja el registro porque no
    // molesta y puede servir para otra cosa más adelante.
    public static final RegistryObject<SimpleParticleType> CORRUPTION_SPARK =
            PARTICLES.register("corruption_spark", () -> new SimpleParticleType(false));

    /**
     * Partícula del efecto de Corrupción (ver CorruptionEffect /
     * CorruptionParticle) -- reemplaza al remolino ambiente vanilla, que
     * no se pudo pintar del tono correcto de forma confiable. Animación
     * de 3 cuadros (corruption_particle_0/1/2, placeholders por ahora).
     */
    public static final RegistryObject<SimpleParticleType> CORRUPTION_PARTICLE =
            PARTICLES.register("corruption_particle", () -> new SimpleParticleType(false));

    // ============================================
    // Cadena Necromántica
    // ============================================

    /**
     * Estallido único y grande al soltar el golpe cargado de la Cadena
     * Necromántica -- estilo "onda de choque" de cómic, no un montón de
     * partículas chicas. Animación de 7 cuadros (necromantic_impact_1..7,
     * arte final del usuario). Ver NecromanticImpactParticle.
     */
    public static final RegistryObject<SimpleParticleType> NECROMANTIC_IMPACT =
            PARTICLES.register("necromantic_impact", () -> new SimpleParticleType(false));

    // ============================================
    // Rayo (Corrupción IV) -- teatro visual del windup
    // ============================================

    /** Anillo que crece al arrancar la carga del rayo (telegraph). */
    public static final RegistryObject<SimpleParticleType> NECROTIC_LIGHTNING_RING_GROW =
            PARTICLES.register("necrotic_lightning_ring_grow", () -> new SimpleParticleType(false));

    /** Anillo que se encoge en el instante exacto en que el rayo se dispara. */
    public static final RegistryObject<SimpleParticleType> NECROTIC_LIGHTNING_RING_SHRINK =
            PARTICLES.register("necrotic_lightning_ring_shrink", () -> new SimpleParticleType(false));

    /** Destello/chispa que parpadea en el punto exacto donde el rayo toca algo. */
    public static final RegistryObject<SimpleParticleType> NECROTIC_LIGHTNING_IMPACT =
            PARTICLES.register("necrotic_lightning_impact", () -> new SimpleParticleType(true));

    // ============================================
    // Nanook -- VFX propios del jefe
    // ============================================

    /**
     * Polvo/nieve blanca de Nanook. Reemplaza a TODAS las partículas blancas
     * vanilla que usaba el jefe y sus proyectiles (CLOUD, SNOWFLAKE, CRIT,
     * EXPLOSION). Las azuladas (BLOCK de ICE/BLUE_ICE, GLOW) se dejaron
     * como estaban, porque son las que dan el tono helado.
     *
     * 9 cuadros (snowy_dust_0..8, arte whitePuff). Cae hacia abajo y se
     * achica con la edad -- ver SnowyDustParticle.
     *
     * overrideLimiter = true: el rugido y la onda del double_ground mandan
     * muchas a la vez y no queremos que el ajuste de partículas del cliente
     * se coma justo las del telegraph del ataque.
     */
    public static final RegistryObject<SimpleParticleType> SNOWY_DUST =
            PARTICLES.register("snowy_dust", () -> new SimpleParticleType(true));

    /**
     * Estallido azul de impacto (7 cuadros, arte
     * directional_impact_001_small_blue). Lo usa el meteoro de hielo de
     * Nanook al tocar el suelo o a alguien. Ver FrostImpactParticle.
     */
    public static final RegistryObject<SimpleParticleType> FROST_IMPACT =
            PARTICLES.register("frost_impact", () -> new SimpleParticleType(true));

    /**
     * Anillo PLANO sobre el suelo que se abre hacia afuera (4 cuadros,
     * snowy_ring_0..3). Es el equivalente propio al "anillo grande" que
     * deja el Nameless Guardian al correr: se usa en el dash de Nanook, en
     * el aterrizaje del salto, en el golpe doble al suelo, en el rugido y
     * en el telegraph de la ejecución.
     *
     * OJO: esta partícula usa los argumentos de VELOCIDAD como parámetros
     * (dx = multiplicador de tamaño, dy = multiplicador de duración). Hay
     * que spawnearla con count = 0 para que lleguen tal cual al cliente.
     * Ver SnowyRingParticle y NanookEntity#spawnGroundRing.
     *
     * overrideLimiter = true: es un efecto de telegrafía, no puede
     * comérselo el ajuste de partículas del cliente.
     */
    public static final RegistryObject<SimpleParticleType> SNOWY_RING =
            PARTICLES.register("snowy_ring", () -> new SimpleParticleType(true));

    /**
     * Aro de VIENTO vertical (1 cuadro, snowy_wind_ring_0). Es el efecto de
     * "romper el aire" del dash: una cadena de aros de pie, perpendiculares a
     * la dirección de la carrera, que aparecen por delante del mob, crecen de
     * golpe y se apagan mientras él los atraviesa. Ver SnowyWindRingParticle.
     *
     * Argumentos de velocidad usados como parámetros (count = 0):
     *   dx = yaw en grados, dy = radio máximo en bloques, dz = opacidad x.
     */
    public static final RegistryObject<SimpleParticleType> SNOWY_WIND_RING =
            PARTICLES.register("snowy_wind_ring", () -> new SimpleParticleType(true));

    /**
     * Misma nube que SNOWY_DUST (mismas texturas, mismo comportamiento) pero al
     * 42 % del tamaño. Es para los ataques básicos: la nube normal mide hasta
     * ~4.6 bloques y tapaba la animación del oso, que es lo que el jugador
     * necesita leer para saber qué ataque viene.
     */
    public static final RegistryObject<SimpleParticleType> SNOWY_DUST_SMALL =
            PARTICLES.register("snowy_dust_small", () -> new SimpleParticleType(true));
}