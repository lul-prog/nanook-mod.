package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.registry.ModEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.MobType;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Todo lo que la Corrupción necesita del mundo pero que MobEffect no puede
 * resolver por sí solo (ver CorruptionEffect para el resto: color,
 * partícula y reducción de vida máxima, eso sí lo maneja el efecto
 * directamente).
 *
 * 1) No-Muertos son inmunes -- ya están muertos, la Corrupción no tiene
 *    nada que drenarles. Esto protege el efecto sin importar CÓMO se
 *    termine aplicando (comando, poción, o el jefe directamente) --
 *    no hace falta acordarse de filtrar esqueletos/guerreros necróticos
 *    cada vez que se conecte el disparador real más adelante.
 *
 * 2) Reduce la curación recibida mientras el efecto está activo. Como
 *    LivingEntity#heal() no distingue "regen natural" de "curación de
 *    poción/comida/etc" (ambas pasan por el mismo método), usamos UNA
 *    sola reducción por amplifier que cubre los dos casos que pediste
 *    (Corrupción I = reducción chica, que en la práctica ya afecta la
 *    regen natural; Corrupción II+ = reducción más fuerte, que ya cubre
 *    cualquier curación). Si más adelante hace falta distinguir origen
 *    de la curación con más precisión, hay que interceptar más arriba
 *    (por ejemplo en FoodData o en el ítem que cura) -- avisame si llegás
 *    a necesitar eso y lo separamos.
 *
 * 3) Apaga el "remolino" vanilla -- Minecraft dibuja automáticamente un
 *    swirl de partículas ambiente alrededor de CUALQUIER entidad con un
 *    MobEffect activo (salvo que la instancia se haya creado con
 *    ambient=false/showParticles=false), coloreado con el color del
 *    efecto. Esto es 100% vanilla, no vive en nuestro código, y en la
 *    práctica no terminaba mostrando el tono correcto -- por eso ahora
 *    Corrupción tiene su propia partícula (CorruptionParticle) y este
 *    handler apaga el remolino automático con MobEffectEvent.Added:
 *    apenas se agrega/actualiza la instancia, si todavía tiene
 *    ambient/showParticles en true, la reemplazamos por una idéntica
 *    pero con esos dos en false. Así queda apagado sin importar CÓMO se
 *    haya dado el efecto (/effect give sin el flag de hideParticles, una
 *    poción, o el jefe más adelante).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CorruptionEffectHandler {

    // Índice = amplifier (0 = Corrupción I). Ajustable a gusto, son solo números.
    private static final float[] HEAL_REDUCTION_BY_AMPLIFIER = {
            0.15F, // Corrupción I
            0.30F, // Corrupción II
            0.30F, // Corrupción III (la reducción no sigue subiendo, ahí lo que suma son las partículas)
            0.45F, // Corrupción IV
            0.60F, // Corrupción V
            0.60F  // Corrupción VI (por si algún día se llega a ese nivel)
    };

    @SubscribeEvent
    public static void onCorruptionApplicable(MobEffectEvent.Applicable event) {
        if (event.getEffectInstance() == null || event.getEffectInstance().getEffect() != ModEffects.CORRUPTION.get()) {
            return;
        }
        if (event.getEntity().getMobType() == MobType.UNDEAD) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        MobEffectInstance corruption = event.getEntity().getEffect(ModEffects.CORRUPTION.get());
        if (corruption == null) {
            return;
        }
        int amplifier = Math.min(corruption.getAmplifier(), HEAL_REDUCTION_BY_AMPLIFIER.length - 1);
        float reduction = HEAL_REDUCTION_BY_AMPLIFIER[amplifier];
        event.setAmount(event.getAmount() * (1.0F - reduction));
    }

    @SubscribeEvent
    public static void onCorruptionAdded(MobEffectEvent.Added event) {
        MobEffectInstance current = event.getEffectInstance();
        if (current == null || current.getEffect() != ModEffects.CORRUPTION.get()) {
            return;
        }
        if (!current.isAmbient() && !current.isVisible()) {
            // Ya está como lo queremos -- este chequeo también corta la
            // recursión: al reinsertar la instancia corregida más abajo,
            // este mismo método se vuelve a disparar, pero la segunda vez
            // entra por acá y no hace nada más.
            return;
        }

        MobEffectInstance corrected = new MobEffectInstance(
                current.getEffect(),
                current.getDuration(),
                current.getAmplifier(),
                false,               // ambient
                false,               // showParticles -- el remolino en sí
                current.showIcon()   // el ícono del efecto en el HUD lo dejamos como estaba
        );
        event.getEntity().forceAddEffect(corrected, event.getEffectSource());
    }
}