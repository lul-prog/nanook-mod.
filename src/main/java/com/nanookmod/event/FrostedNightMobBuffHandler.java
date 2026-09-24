package com.nanookmod.event;

import com.nanookmod.NanookMod;
import com.nanookmod.entity.GnutEntity;
import com.nanookmod.entity.SnowyBlizzEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

/**
 * Durante la Noche Escarchada, los mobs propios del mod (agrégalos a
 * {@link #isNanookMob}) salen más fuertes, dropean más items y dan más xp.
 *
 * Todo esto es lógica de SERVIDOR (no se registra con Dist.CLIENT): el
 * cliente no decide cuánta vida tiene un mob ni qué dropea, eso lo maneja
 * siempre el servidor (o el servidor integrado en un mundo de un jugador).
 */
@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FrostedNightMobBuffHandler {

    // IDs fijos para los modifiers: así el juego sabe que es EL MISMO buff
    // si por algún motivo este código llegara a correr dos veces sobre el
    // mismo mob, y no lo duplica.
    private static final UUID HEALTH_BUFF_ID = UUID.fromString("b3f1b5b0-1c9a-4a34-9b2e-3f2a6f7e9c11");
    private static final UUID DAMAGE_BUFF_ID = UUID.fromString("c4a2c6c1-2d0b-4b45-8c3f-4a3b7e8f0d22");

    // Ajusta estos números a tu gusto.
    private static final double HEALTH_MULTIPLIER = 0.5;   // +50% de vida máxima
    private static final double DAMAGE_MULTIPLIER = 0.35;  // +35% de daño de ataque
    private static final float DROP_MULTIPLIER = 2.0F;     // el doble de items al morir
    private static final float XP_MULTIPLIER = 2.0F;       // el doble de xp al morir

    /**
     * Se dispara cuando un mob aparece/entra al mundo (spawn natural, spawn
     * por comando, o al cargar el chunk donde ya vivía). Aquí le subimos
     * vida y daño si nace durante la Noche Escarchada.
     *
     * OJO: esto solo afecta a mobs que aparecen MIENTRAS está activa la
     * Noche Escarchada. Uno que ya estaba vivo desde antes no se vuelve
     * fuerte de golpe cuando empieza la noche (eso evita que, por ejemplo,
     * cambie la dificultad de un combate a mitad de pelea).
     */
    @SubscribeEvent
    public static void onEntitySpawn(EntityJoinLevelEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide()) return;
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!isNanookMob(living)) return;
        if (!FrostedNightHandler.isFrostedNight(level)) return;

        applyBuff(living, Attributes.MAX_HEALTH, HEALTH_BUFF_ID, HEALTH_MULTIPLIER);
        applyBuff(living, Attributes.ATTACK_DAMAGE, DAMAGE_BUFF_ID, DAMAGE_MULTIPLIER);

        if (living instanceof com.nanookmod.entity.FrostedBuffed buffed) {
            buffed.setFrostedBuffed(true);
        }

        // Sin esto, el mob nacería con la vida "vieja" (la de antes de subir
        // el máximo), o sea, ya dañado sin motivo.
        living.setHealth(living.getMaxHealth());
    }

    private static void applyBuff(LivingEntity living, Attribute attribute, UUID id, double multiplier) {
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null) return;
        if (instance.getModifier(id) != null) return; // ya lo tiene, no duplicar

        instance.addPermanentModifier(new AttributeModifier(
                id, "nanookmod:frosted_night_buff", multiplier, AttributeModifier.Operation.MULTIPLY_TOTAL
        ));
    }

    /**
     * Se dispara cuando un mob va a soltar sus drops al morir.
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity living = event.getEntity();
        Level level = living.level();
        if (level.isClientSide()) return;
        if (!isNanookMob(living)) return;
        if (!FrostedNightHandler.isFrostedNight(level)) return;

        for (ItemEntity drop : event.getDrops()) {
            int newCount = Math.round(drop.getItem().getCount() * DROP_MULTIPLIER);
            drop.getItem().setCount(Math.max(1, newCount));
        }
    }

    /**
     * Se dispara cuando un mob va a soltar experiencia al morir.
     */
    @SubscribeEvent
    public static void onXpDrop(LivingExperienceDropEvent event) {
        Level level = event.getEntity().level();
        if (level.isClientSide()) return;
        if (!isNanookMob(event.getEntity())) return;
        if (!FrostedNightHandler.isFrostedNight(level)) return;

        event.setDroppedExperience(Math.round(event.getDroppedExperience() * XP_MULTIPLIER));
    }

    /**
     * Agrega aquí un "|| entity instanceof TuMob" por cada mob propio que
     * quieras que se potencie en la Noche Escarchada. Los mobs vanilla
     * (zombies, esqueletos, etc.) no se tocan.
     */
    private static boolean isNanookMob(LivingEntity entity) {
        return entity instanceof GnutEntity || entity instanceof SnowyBlizzEntity;
    }
}