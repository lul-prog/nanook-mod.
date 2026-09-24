package com.nanookmod.block.entity;

import com.nanookmod.block.custom.FrostCrop;
import com.nanookmod.block.custom.FrostLanternBlock;
import com.nanookmod.registry.ModBlockEntities;
import com.nanookmod.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class FrostLanternBlockEntity extends BlockEntity implements BlockEntityTicker<FrostLanternBlockEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private int fuelLevel = 0;

    // 20 minutos = 24,000 ticks (10 fuels × 2,400 ticks cada uno)
    private static final int MAX_FUEL = 24000;
    private static final int FUEL_CONSUMPTION_RATE = 1;

    private static final int GROWTH_CHECK_INTERVAL = 200;
    private int tickCounter = 0;

    private static final int EFFECT_RADIUS = 3;
    private static final int MAX_PLANTS = 6;
    private static final int GROWTH_BOOST_CHANCE = 15;

    // Registro COMPARTIDO entre TODAS las lámparas del juego: guarda cuándo
    // fue la última vez que cada posición de cultivo recibió el boost. Así,
    // si apilas 2+ lámparas y sus radios se solapan sobre el mismo cultivo,
    // solo UNA puede potenciarlo por ronda (cada GROWTH_CHECK_INTERVAL
    // ticks) en vez de que cada lámpara lo cuente aparte y se acumule el
    // doble/triple. Se guarda por dimensión+posición para no mezclar
    // cultivos que coincidan en coordenadas en dimensiones distintas.
    private static final java.util.Map<String, Long> LAST_BOOST_TICK = new java.util.concurrent.ConcurrentHashMap<>();

    private static String boostKey(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "@" + pos.asLong();
    }

    public FrostLanternBlockEntity(BlockPos pPos, BlockState pBlockState) {
        super(ModBlockEntities.FROST_LANTERN.get(), pPos, pBlockState);
    }

    @Override
    public void tick(Level level, BlockPos pos, BlockState state, FrostLanternBlockEntity blockEntity) {
        if (level.isClientSide) return;

        if (fuelLevel > 0) {
            fuelLevel -= FUEL_CONSUMPTION_RATE;
            if (fuelLevel < 0) fuelLevel = 0;

            tickCounter++;
            if (tickCounter >= GROWTH_CHECK_INTERVAL) {
                tickCounter = 0;
                if (fuelLevel > 0) {
                    accelerateNearbyPlants(level, pos);
                }
            }
        }

        // Calcular el nuevo estado visual
        int newState = 0;
        if (fuelLevel > 12000) { // Más de la mitad = Encendida
            newState = 2;
        } else if (fuelLevel > 0) { // Menos de la mitad = Baja energía
            newState = 1;
        }

        // Actualizar el bloque si cambió el estado
        if (state.getValue(FrostLanternBlock.FUEL_STATE) != newState) {
            level.setBlock(pos, state.setValue(FrostLanternBlock.FUEL_STATE, newState), 3);
        }

        setChanged();
    }

    private void accelerateNearbyPlants(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        int plantsAffected = 0;

        // 🆕 PROBABILIDAD DINÁMICA: ON = 15%, LOW = 7% (la mitad)
        int currentChance;
        if (fuelLevel > 12000) {
            currentChance = GROWTH_BOOST_CHANCE; // Estado ON: 15%
        } else {
            currentChance = GROWTH_BOOST_CHANCE / 2; // Estado LOW: 7%
        }

        for (int x = -EFFECT_RADIUS; x <= EFFECT_RADIUS; x++) {
            for (int y = -EFFECT_RADIUS; y <= EFFECT_RADIUS; y++) {
                for (int z = -EFFECT_RADIUS; z <= EFFECT_RADIUS; z++) {
                    if (plantsAffected >= MAX_PLANTS) return;

                    BlockPos plantPos = pos.offset(x, y, z);
                    BlockState plantState = level.getBlockState(plantPos);

                    if (plantState.getBlock() instanceof FrostCrop) {
                        // Si otra lámpara (o esta misma en una ronda previa)
                        // ya potenció este cultivo hace menos de un ciclo
                        // completo, no lo volvemos a contar: evita el
                        // exploit de apilar lámparas para duplicar el boost.
                        String key = boostKey(serverLevel, plantPos);
                        Long lastBoost = LAST_BOOST_TICK.get(key);
                        long now = serverLevel.getGameTime();
                        if (lastBoost != null && now - lastBoost < GROWTH_CHECK_INTERVAL) {
                            continue;
                        }

                        // Usamos la probabilidad dinámica
                        if (level.random.nextInt(100) < currentChance) {
                            int currentAge = plantState.getValue(FrostCrop.AGE);
                            int maxAge = 7;

                            if (currentAge < maxAge) {
                                level.setBlock(plantPos, plantState.setValue(FrostCrop.AGE, currentAge + 1), 3);
                                plantsAffected++;
                                LAST_BOOST_TICK.put(key, now);
                            }
                        }
                    }
                }
            }
        }
    }

    public void addFuel(ItemStack stack) {
        if (stack.is(ModItems.FROST_FUEL.get())) {
            // 1 fuel = 2,400 ticks = 2 minutos reales
            this.fuelLevel = Math.min(fuelLevel + 2400, MAX_FUEL);
            stack.shrink(1);
            setChanged();

            // ACTUALIZAR EL ESTADO VISUAL INMEDIATAMENTE
            if (this.level != null && !this.level.isClientSide) {
                BlockState currentState = this.level.getBlockState(this.worldPosition);
                int newState = fuelLevel > 12000 ? 2 : (fuelLevel > 0 ? 1 : 0);
                if (currentState.getValue(FrostLanternBlock.FUEL_STATE) != newState) {
                    this.level.setBlock(this.worldPosition, currentState.setValue(FrostLanternBlock.FUEL_STATE, newState), 3);
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag pTag) {
        pTag.putInt("FuelLevel", fuelLevel);
        pTag.putInt("TickCounter", tickCounter);
        super.saveAdditional(pTag);
    }

    @Override
    public void load(CompoundTag pTag) {
        super.load(pTag);
        this.fuelLevel = pTag.getInt("FuelLevel");
        this.tickCounter = pTag.getInt("TickCounter");
    }
}