package com.nanookmod.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Maldición Necrótica (id interno "corruption") -- el efecto que le queda al
 * jugador por pelear contra el NecroticKnight cuando lo golpea su rayo. A
 * diferencia de un Poison normal, esto no hace daño directo: ataca la
 * CAPACIDAD de recuperarse.
 *
 * IMPORTANTE: esta clase es solo el efecto en sí (registro, color, el
 * multiplicador de vida máxima). La reducción de curación se resuelve en
 * CorruptionEffectHandler (evento LivingHealEvent) porque MobEffect no tiene
 * un hook directo para interceptar curación -- la exclusión de No-Muertos
 * (esqueletos/guerreros necróticos) también vive ahí
 * (MobEffectEvent.Applicable), y el apagado del remolino ambiente vanilla
 * también (MobEffectEvent.Added) -- ver el handler para el porqué de cada
 * uno.
 *
 * SIN PARTÍCULAS, a propósito y por pedido explícito: este efecto no dibuja
 * NINGÚN efecto visual propio (ya no llama a spawnCorruptionParticles, que se
 * sacó de acá) NI el remolino ambiente vanilla (Minecraft dibuja uno solo
 * automáticamente alrededor de cualquier entidad con un MobEffect activo,
 * coloreado con CORRUPTION_COLOR -- se apaga en CorruptionEffectHandler
 * #onCorruptionAdded, y además se aplica siempre con showParticles=false
 * desde el origen en NecroticLightningBeamEntity#applyNecroticCurse, así que
 * queda cubierto por partida doble). La única señal para el jugador es el
 * ícono del efecto y el HUD del NecroticKnight.
 *
 * Lo aplica y lo va escalando NecroticLightningBeamEntity#applyNecroticCurse
 * cada vez que el rayo del jefe conecta (nivel 1 al primer golpe, +1 nivel
 * cada 2s de golpes seguidos, hasta el nivel máximo que define
 * NecroticKnightEntity#getMaxCorruptionLevel()). Para probarlo a mano:
 * /effect give @s nanookmod:corruption <segundos> <amplificador 0-5>
 * (amplificador 0 = Nivel I ... 5 = Nivel VI).
 *
 * Mapeo de niveles (amplifier = nivel - 1):
 *   Amplifier 0 (Nivel I)   -- reducción chica de toda curación recibida (incluye regen natural, ver el handler)
 *   Amplifier 1 (Nivel II)  -- la reducción de curación sube
 *   Amplifier 2 (Nivel III) -- igual que el nivel II
 *   Amplifier 3 (Nivel IV)  -- reducción de curación más fuerte
 *   Amplifier 4+ (Nivel V)  -- además, reduce temporalmente la vida máxima
 */
public class CorruptionEffect extends MobEffect {

    // Mismo tono que el wave del NecroticKnight -- ver
    // NecroticWaveRenderer#COLOR_R/G/B -- #9cf6de. Solo se usa para el
    // ícono del efecto en el HUD de efectos activos (ya no para partículas).
    private static final int CORRUPTION_COLOR = 0x9CF6DE;

    // A partir de qué amplifier se activa la reducción de vida máxima (Corrupción V = amplifier 4).
    private static final int MAX_HEALTH_AMPLIFIER_THRESHOLD = 4;

    // Cuánto se reduce la vida máxima por encima del umbral -- 30% en amplifier 4, +5% por cada nivel extra.
    private static final double MAX_HEALTH_BASE_REDUCTION = 0.30D;
    private static final double MAX_HEALTH_REDUCTION_STEP = 0.05D;

    // Techo de seguridad: nunca reduce más de esto, sin importar qué
    // amplifier reciba (ver explicación completa donde se usa más abajo).
    private static final double MAX_HEALTH_REDUCTION_CAP = 0.60D;

    private static final String MAX_HEALTH_MODIFIER_UUID = "b3f2b1a0-4c1a-4a2b-9c3d-1a2b3c4d5e6f";

    public CorruptionEffect() {
        super(MobEffectCategory.HARMFUL, CORRUPTION_COLOR);
        this.addAttributeModifier(
                Attributes.MAX_HEALTH,
                MAX_HEALTH_MODIFIER_UUID,
                -MAX_HEALTH_BASE_REDUCTION,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        if (amplifier < MAX_HEALTH_AMPLIFIER_THRESHOLD) {
            return 0.0D;
        }
        int stepsAboveThreshold = amplifier - MAX_HEALTH_AMPLIFIER_THRESHOLD;
        double reduction = MAX_HEALTH_BASE_REDUCTION + stepsAboveThreshold * MAX_HEALTH_REDUCTION_STEP;
        return -Math.min(reduction, MAX_HEALTH_REDUCTION_CAP);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        if (amplifier >= MAX_HEALTH_AMPLIFIER_THRESHOLD) {
            // Todos los ticks -- necesitamos reclamar la vida actual en
            // cuanto se pase del nuevo máximo (ver applyEffectTick).
            return true;
        }
        // Cada 4 ticks para TODOS los niveles (incluido el I).
        return duration % 4 == 0;
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        // Bajar el atributo MAX_HEALTH no recorta la vida ACTUAL sola --
        // si el jugador estaba a vida llena, Minecraft no la "reclama"
        // hasta el próximo daño/curación. Sin este chequeo, la vida
        // máxima baja mecánicamente (afecta curación, regen, etc.) pero
        // los corazones en pantalla no se mueven hasta el primer golpe.
        // Solo servidor -- el cliente nunca decide la vida real, solo la
        // refleja.
        if (amplifier >= MAX_HEALTH_AMPLIFIER_THRESHOLD && !entity.level().isClientSide()
                && entity.getHealth() > entity.getMaxHealth()) {
            entity.setHealth(entity.getMaxHealth());
        }

        // Sin partículas a propósito -- ver el javadoc de la clase. No hay
        // más nada que hacer del lado cliente para este efecto.
    }
}
