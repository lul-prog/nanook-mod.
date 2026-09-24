package com.nanookmod.capability;

import com.nanookmod.NanookMod;
import com.nanookmod.network.SyncNecromanticChainPacket;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class NecromanticChainCapability {

    public static final Logger LOGGER = LogManager.getLogger();
    public static final ResourceLocation CAPABILITY_ID = new ResourceLocation(NanookMod.MOD_ID, "necromantic_chain");
    public static final Capability<INecromanticChainCapability> NECROMANTIC_CHAIN_CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});

    public static final int DURATION_TICKS = 600;
    public static final int COOLDOWN_TICKS = 1200;
    public static final int SPEED_AMPLIFIER = 1;
    public static final float DAMAGE_PER_BLOCK = 0.5f;
    public static final float MAX_ACCUMULATED_DAMAGE = 50f;
    public static final float TRANSPARENCY_ALPHA = 0.35f;
    public static final int PARTICLE_INTERVAL = 4;

    public interface INecromanticChainCapability {
        boolean isActive();
        void activate(Player player);
        void deactivate(Player player);
        float getAccumulatedDamage();
        void setAccumulatedDamage(float dmg);
        float getTotalDistance();
        void addDistance(float dist);
        void resetDistance();
        Vec3 getLastPosition();
        void setLastPosition(Vec3 pos);
        long getCooldownEndTick();
        void setCooldownEndTick(long tick);
        long getCooldownRemaining(long gameTime);
        long getActiveEndTick();
        void setActiveEndTick(long tick);
        void tick(Player player, ServerLevel level);
        void resetDamage();
        void syncToClient(ServerPlayer player);
        void readClientData(SyncNecromanticChainPacket packet);
        void writeClientData(SyncNecromanticChainPacket packet);
    }

    public static class NecromanticChainCapabilityImpl implements INecromanticChainCapability, INBTSerializable<CompoundTag> {
        private boolean active = false;
        private float accumulatedDamage = 0f;
        private float totalDistance = 0f;
        private Vec3 lastPosition = Vec3.ZERO;
        private long cooldownEndTick = 0L;
        private long activeEndTick = 0L;
        private UUID playerUUID;

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void activate(Player player) {
            this.active = true;
            this.accumulatedDamage = 0f;
            this.totalDistance = 0f;
            this.lastPosition = player.position();
            this.activeEndTick = player.level().getGameTime() + DURATION_TICKS;
            this.playerUUID = player.getUUID();
            if (!player.level().isClientSide()) {
                LOGGER.info("[NecromanticChain] ACTIVADA para {} en gameTime={} (terminará en gameTime={})",
                        player.getName().getString(), player.level().getGameTime(), this.activeEndTick);
            }
            // IMPORTANTE: LivingEntity recalcula la bandera "invisible" TODOS
            // los ticks en base a si el jugador tiene el MobEffect de
            // Invisibilidad (updateInvisibilityStatus()). Si solo hacíamos
            // player.setInvisible(true) sin el efecto real, el juego lo
            // revertía a false casi al instante -- por eso no se veía nada.
            // Aplicamos el efecto real pero "silencioso" (sin ícono en el HUD
            // de efectos activos y sin partículas) para que el jugador no lo
            // perciba como el potion de invisibilidad de siempre; la capa
            // personalizada (NecromanticChainRenderLayer) se sigue dibujando
            // igual encima y es la que da el efecto fantasma visible.
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.INVISIBILITY, DURATION_TICKS + 20, 0,
                    false, false, false));
            player.setInvisible(true);
            if (!player.level().isClientSide()) {
                player.getCooldowns().addCooldown(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(NanookMod.MOD_ID, "necromantic_chain")), DURATION_TICKS);
                // Si algún mob ya te estaba atacando ANTES de activar, cancelar
                // solo el evento de "nuevo target" no sirve de nada: hay que
                // soltarte explícitamente de los mobs que ya te tenían fichado,
                // si no la habilidad se desactiva casi al instante con el
                // primer golpe que ya venía en camino.
                net.minecraft.world.phys.AABB nearby = player.getBoundingBox().inflate(32.0);
                for (net.minecraft.world.entity.Mob mob : ((ServerLevel) player.level()).getEntitiesOfClass(net.minecraft.world.entity.Mob.class, nearby)) {
                    if (mob.getTarget() == player) {
                        mob.setTarget(null);
                    }
                }
            }
        }

        @Override
        public void deactivate(Player player) {
            this.active = false;
            this.cooldownEndTick = player.level().getGameTime() + COOLDOWN_TICKS;
            this.accumulatedDamage = 0f;
            this.totalDistance = 0f;
            player.setInvisible(false);
            if (player.hasEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY)) {
                player.removeEffect(net.minecraft.world.effect.MobEffects.INVISIBILITY);
            }
            if (!player.level().isClientSide()) {
                LOGGER.info("[NecromanticChain] DESACTIVADA para {} en gameTime={} (podrá reusar en gameTime={})",
                        player.getName().getString(), player.level().getGameTime(), this.cooldownEndTick);
            }
            // El Speed se aplica en tick() con su propia duración de
            // DURATION_TICKS; si la habilidad se desactiva antes de tiempo
            // (por ejemplo al recibir daño), el efecto se queda corriendo
            // solo hasta agotar esa duración. Hay que quitarlo a mano aquí.
            if (player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED)) {
                player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
            }
        }

        @Override
        public float getAccumulatedDamage() {
            return accumulatedDamage;
        }

        @Override
        public void setAccumulatedDamage(float dmg) {
            this.accumulatedDamage = Math.max(0f, Math.min(dmg, MAX_ACCUMULATED_DAMAGE));
        }

        @Override
        public float getTotalDistance() {
            return totalDistance;
        }

        @Override
        public void addDistance(float dist) {
            this.totalDistance += dist;
            float newDamage = Math.min(this.totalDistance * DAMAGE_PER_BLOCK, MAX_ACCUMULATED_DAMAGE);
            this.accumulatedDamage = newDamage;
        }

        @Override
        public void resetDistance() {
            this.totalDistance = 0f;
            this.lastPosition = Vec3.ZERO;
        }

        @Override
        public Vec3 getLastPosition() {
            return lastPosition;
        }

        @Override
        public void setLastPosition(Vec3 pos) {
            this.lastPosition = pos;
        }

        @Override
        public long getCooldownEndTick() {
            return cooldownEndTick;
        }

        @Override
        public void setCooldownEndTick(long tick) {
            this.cooldownEndTick = tick;
        }

        @Override
        public long getCooldownRemaining(long gameTime) {
            return Math.max(0L, cooldownEndTick - gameTime);
        }

        @Override
        public long getActiveEndTick() {
            return activeEndTick;
        }

        @Override
        public void setActiveEndTick(long tick) {
            this.activeEndTick = tick;
        }

        @Override
        public void tick(Player player, ServerLevel level) {
            if (!active) {
                return;
            }

            long gameTime = level.getGameTime();
            if (gameTime >= activeEndTick) {
                deactivate(player);
                syncToClient((ServerPlayer) player);
                return;
            }

            Vec3 currentPos = player.position();
            if (lastPosition != Vec3.ZERO) {
                float dist = (float) lastPosition.distanceTo(currentPos);
                if (dist > 0.01f) {
                    addDistance(dist);
                }
            }
            lastPosition = currentPos;

            if (!player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED) || player.getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED).getAmplifier() < SPEED_AMPLIFIER) {
                // ambient=false, visible=false (sin partículas), showIcon=false
                // (sin ícono ni en pantalla ni en el inventario/tabla de efectos)
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, DURATION_TICKS, SPEED_AMPLIFIER, false, false, false));
            }

            // El daño acumulado se recalcula cada tick, pero antes solo se
            // sincronizaba al cliente en momentos puntuales (activar,
            // desactivar, golpear). Por eso el HUD se quedaba congelado
            // mientras la habilidad seguía activa. Sincronizamos cada 10
            // ticks (2 veces por segundo) para que se vea en vivo, sin
            // saturar la red con un paquete por tick.
            if (gameTime % 10 == 0) {
                syncToClient((ServerPlayer) player);
            }
        }

        @Override
        public void resetDamage() {
            this.accumulatedDamage = 0f;
            this.totalDistance = 0f;
            this.lastPosition = Vec3.ZERO;
        }

        @Override
        public void syncToClient(ServerPlayer player) {
            if (player.level().isClientSide()) return;
            // TRACKING_ENTITY_AND_SELF en vez de PLAYER: así el estado (activo,
            // daño acumulado, etc.) también le llega a los clientes de OTROS
            // jugadores que estén viendo a este jugador -- si no, ellos nunca
            // verían la capa translúcida (su copia local de la capability se
            // quedaría siempre en "inactivo").
            com.nanookmod.network.ModNetworking.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                    new com.nanookmod.network.SyncNecromanticChainPacket(
                            this.active,
                            this.accumulatedDamage,
                            this.cooldownEndTick,
                            this.activeEndTick
                    )
            );
        }

        @Override
        public void readClientData(SyncNecromanticChainPacket packet) {
            this.active = packet.active;
            this.accumulatedDamage = packet.accumulatedDamage;
            this.cooldownEndTick = packet.cooldownEndTick;
            this.activeEndTick = packet.activeEndTick;
        }

        @Override
        public void writeClientData(SyncNecromanticChainPacket packet) {
            // Not used for client sync, but kept for interface completeness
            // The packet is created from this capability's fields in syncToClient
        }

        @Override
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Active", active);
            tag.putFloat("AccumulatedDamage", accumulatedDamage);
            tag.putFloat("TotalDistance", totalDistance);
            tag.putLong("CooldownEndTick", cooldownEndTick);
            tag.putLong("ActiveEndTick", activeEndTick);
            if (lastPosition != Vec3.ZERO) {
                tag.putDouble("LastPosX", lastPosition.x);
                tag.putDouble("LastPosY", lastPosition.y);
                tag.putDouble("LastPosZ", lastPosition.z);
            }
            if (playerUUID != null) {
                tag.putUUID("PlayerUUID", playerUUID);
            }
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag tag) {
            this.active = tag.getBoolean("Active");
            this.accumulatedDamage = tag.getFloat("AccumulatedDamage");
            this.totalDistance = tag.getFloat("TotalDistance");
            this.cooldownEndTick = tag.getLong("CooldownEndTick");
            this.activeEndTick = tag.getLong("ActiveEndTick");
            if (tag.contains("LastPosX")) {
                this.lastPosition = new Vec3(tag.getDouble("LastPosX"), tag.getDouble("LastPosY"), tag.getDouble("LastPosZ"));
            }
            if (tag.contains("PlayerUUID")) {
                this.playerUUID = tag.getUUID("PlayerUUID");
            }
        }
    }

    public static class NecromanticChainProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {
        private final NecromanticChainCapabilityImpl capability = new NecromanticChainCapabilityImpl();
        private final LazyOptional<INecromanticChainCapability> lazyCapability = LazyOptional.of(() -> capability);

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
            if (cap == NECROMANTIC_CHAIN_CAPABILITY) {
                return lazyCapability.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return capability.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            capability.deserializeNBT(nbt);
        }
    }

    @Mod.EventBusSubscriber(modid = NanookMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class AttachHandler {
        @SubscribeEvent
        public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event) {
            if (event.getObject() instanceof Player) {
                event.addCapability(CAPABILITY_ID, new NecromanticChainProvider());
            }
        }
    }

    public static INecromanticChainCapability getCap(Player player) {
        return player.getCapability(NECROMANTIC_CHAIN_CAPABILITY).orElse(null);
    }

    public static boolean isActive(Player player) {
        INecromanticChainCapability cap = getCap(player);
        return cap != null && cap.isActive();
    }
}