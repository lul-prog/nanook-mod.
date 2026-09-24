package com.nanookmod.client;

import com.nanookmod.NanookMod;
import com.nanookmod.client.particles.FrostedBuffParticle;
import com.nanookmod.client.particles.CrepuscularMoteParticle;
import com.nanookmod.client.particles.CrepuscularMistParticle;
import com.nanookmod.client.particles.CrepuscularLeafParticle;
import com.nanookmod.client.particles.CorruptionSparkParticle;
import com.nanookmod.client.particles.CorruptionParticle;
import com.nanookmod.client.particles.NecromanticImpactParticle;
import com.nanookmod.client.particles.NecroticLightningRingParticle;
import com.nanookmod.client.particles.NecroticLightningImpactParticle;
import com.nanookmod.client.particles.SnowyDustParticle;
import com.nanookmod.client.particles.SnowyRingParticle;
import com.nanookmod.client.particles.SnowyWindRingParticle;
import com.nanookmod.client.particles.FrostImpactParticle;
import com.nanookmod.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ModParticleProviders {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.FROSTED_BUFF.get(), FrostedBuffParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CREPUSCULAR_MOTE.get(), CrepuscularMoteParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CREPUSCULAR_MIST.get(), CrepuscularMistParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CREPUSCULAR_LEAF.get(), CrepuscularLeafParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CORRUPTION_SPARK.get(), CorruptionSparkParticle.Provider::new);
        event.registerSpriteSet(ModParticles.CORRUPTION_PARTICLE.get(), CorruptionParticle.Provider::new);
        event.registerSpriteSet(ModParticles.NECROMANTIC_IMPACT.get(), NecromanticImpactParticle.Provider::new);
        event.registerSpriteSet(ModParticles.NECROTIC_LIGHTNING_RING_GROW.get(), NecroticLightningRingParticle.GrowProvider::new);
        event.registerSpriteSet(ModParticles.NECROTIC_LIGHTNING_RING_SHRINK.get(), NecroticLightningRingParticle.ShrinkProvider::new);
        event.registerSpriteSet(ModParticles.NECROTIC_LIGHTNING_IMPACT.get(), NecroticLightningImpactParticle.Provider::new);

        // --- VFX propios de Nanook ---
        event.registerSpriteSet(ModParticles.SNOWY_DUST.get(), SnowyDustParticle.Provider::new);
        // Variante chica para los ataques básicos (no tapa la animación).
        event.registerSpriteSet(ModParticles.SNOWY_DUST_SMALL.get(),
                sprites -> new SnowyDustParticle.Provider(sprites, SnowyDustParticle.SMALL_SCALE));
        event.registerSpriteSet(ModParticles.FROST_IMPACT.get(), FrostImpactParticle.Provider::new);
        // Anillo plano del dash / impactos (equivalente propio al "big_ring").
        event.registerSpriteSet(ModParticles.SNOWY_RING.get(), SnowyRingParticle.Provider::new);
        // Aro de viento vertical del dash (efecto "romper el aire").
        event.registerSpriteSet(ModParticles.SNOWY_WIND_RING.get(), SnowyWindRingParticle.Provider::new);
    }
}