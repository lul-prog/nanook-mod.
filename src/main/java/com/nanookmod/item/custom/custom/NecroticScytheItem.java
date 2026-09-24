package com.nanookmod.item.custom.custom;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.nanookmod.entity.NecroticGroundRuptureVfx;
import com.nanookmod.entity.NecroticSlashVfx;
import com.nanookmod.registry.ModEntities;
import com.nanookmod.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Guadaña necrótica: versión Forge del arma "Necrostaff" del pack Awakened
 * Necromancer (MMOItems), habilidad "Necrotic Combo" -- portada como combo
 * de 3 golpes (golpe 1 -> golpe 2 -> golpe final).
 *
 * Diseño:
 *  - Mientras el jugador tiene este ítem en la mano principal, existe UNA
 *    entidad NecroticSlashVfx (la guadaña gigante) que lo sigue y queda en
 *    animación "idle". Se crea al equipar el arma; se destruye SOLA (ver
 *    NecroticSlashVfx#isOwnerStillWieldingScythe) apenas deja de sostenerla,
 *    la tire al piso, muera o se desconecte.
 *  - Cada golpe del combo le pide a ESA MISMA entidad que reproduzca la
 *    animación de golpe -- no se crea una entidad nueva por golpe.
 *  - El daño base de cada golpe lo aplica Minecraft solo (vía los
 *    AttributeModifier de ATTACK_DAMAGE/ATTACK_SPEED de este ítem).
 *  - v4: además de esos, se agrega un AttributeModifier de
 *    ForgeMod.ENTITY_REACH ("Attack Range") para que puedas golpear a más
 *    distancia -- acorde al tamaño real de la hoja, en vez de necesitar
 *    estar pegado al mob como con las manos. OJO: este atributo de Forge es
 *    conocido por no siempre tomar efecto en todas las builds de Forge
 *    1.20.1 (hay reportes de otros mods con el mismo problema) -- probalo,
 *    y si el alcance no cambia en la práctica, avisame para armar un
 *    sistema de detección de golpe propio (raytrace + paquete de red) en
 *    vez de depender de este atributo.
 *  - El daño en área y el golpe final (empuje + rotura) se aplican a mano,
 *    igual que antes.
 */
public class NecroticScytheItem extends Item {

    private static final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("d8f3a6a1-6b8b-4e7e-9a53-6a6f4c8d9d10");
    private static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("f2b6f1b8-2f19-4a0a-8b0e-2a2c9a6b2d40");
    private static final UUID ATTACK_REACH_MODIFIER_UUID = UUID.fromString("6a2c9b7e-3d4f-4a1b-9c5e-2b8f7a1d6e93");

    private static final float BASE_ATTACK_DAMAGE = 4.0F;
    private static final float BASE_ATTACK_SPEED = -1.4F;
    // +2 bloques de alcance sobre la base vanilla (~3), para que llegue a lo
    // que la hoja realmente cubre visualmente.
    private static final float BASE_ATTACK_REACH = 2.0F;

    private static final float HIT1_BONUS_DAMAGE = 0.0F;
    private static final float HIT2_BONUS_DAMAGE = 1.0F;
    private static final float HIT3_BONUS_DAMAGE = 3.0F;

    private static final float SPLASH_DAMAGE = 2.0F;
    private static final float SPLASH_DAMAGE_FINISHER = 3.0F;
    private static final double SPLASH_RADIUS = 3.0D;

    private static final int COMBO_RESET_TICKS = 30;

    private static final String TAG_COMBO_STAGE = "NecroticComboStage";
    private static final String TAG_LAST_HIT_TICK = "NecroticComboLastHit";

    // Habilidad "giro" (click derecho): golpea a todo lo que esté cerca en
    // un radio, con un cooldown propio -- independiente del combo de golpes.
    private static final int SPIN_COOLDOWN_TICKS = 60; // 3s
    private static final float SPIN_DAMAGE = 4.0F;
    private static final double SPIN_RADIUS = 4.0D;
    private static final double SPIN_KNOCKBACK = 0.6D;

    /**
     * Una guadaña "compañera" activa por jugador. Mapeo simple en memoria --
     * no persiste entre reinicios, y no hace falta: se re-crea sola apenas
     * el jugador vuelve a tener el arma en la mano.
     */
    private static final Map<UUID, NecroticSlashVfx> ACTIVE_SCYTHES = new HashMap<>();

    public NecroticScytheItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        boolean holdingInMainHand = isSelected && player.getMainHandItem() == stack;
        NecroticSlashVfx companion = ACTIVE_SCYTHES.get(player.getUUID());

        if (holdingInMainHand) {
            if (companion == null || !companion.isAlive()) {
                spawnCompanion((ServerLevel) level, player);
            }
        } else if (companion != null) {
            // Red de seguridad extra: el auto-chequeo real vive en
            // NecroticSlashVfx#isOwnerStillWieldingScythe (por eso funciona
            // también cuando el ítem se tira al piso, no solo cuando se
            // cambia de slot).
            companion.discard();
            ACTIVE_SCYTHES.remove(player.getUUID());
        }
    }

    private NecroticSlashVfx spawnCompanion(ServerLevel level, Player player) {
        NecroticSlashVfx companion = new NecroticSlashVfx(ModEntities.NECROTIC_SLASH_VFX.get(), level);
        companion.setOwner(player);
        companion.moveTo(player.getX(), player.getY() + player.getEyeHeight() * 0.55D, player.getZ(),
                player.getYRot(), 0.0F);
        level.addFreshEntity(companion);
        ACTIVE_SCYTHES.put(player.getUUID(), companion);
        return companion;
    }

    @Override
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) {
            return false;
        }

        Level level = player.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        CompoundTag tag = stack.getOrCreateTag();
        long lastHitTick = tag.getLong(TAG_LAST_HIT_TICK);
        int stage = tag.getInt(TAG_COMBO_STAGE);

        if (serverLevel.getGameTime() - lastHitTick > COMBO_RESET_TICKS) {
            stage = 0;
        }

        switch (stage) {
            case 0 -> performHit(serverLevel, player, livingTarget, 0, HIT1_BONUS_DAMAGE);
            case 1 -> performHit(serverLevel, player, livingTarget, 1, HIT2_BONUS_DAMAGE);
            default -> performFinisher(serverLevel, player, livingTarget);
        }

        tag.putInt(TAG_COMBO_STAGE, (stage + 1) % 3);
        tag.putLong(TAG_LAST_HIT_TICK, serverLevel.getGameTime());

        return false;
    }

    /**
     * Habilidad "giro" (vertical_spining_slash2): click derecho, golpea a
     * todo lo que esté cerca en un radio, con su propio cooldown --
     * independiente del combo de golpes de onLeftClickEntity.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (level instanceof ServerLevel serverLevel) {
            performSpinAttack(serverLevel, player);
            player.getCooldowns().addCooldown(this, SPIN_COOLDOWN_TICKS);
        }

        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private void performSpinAttack(ServerLevel level, Player player) {
        DamageSource damageSource = player.damageSources().playerAttack(player);

        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SPIN_RADIUS))) {
            if (nearby == player || !nearby.isAlive()) {
                continue;
            }

            nearby.hurt(damageSource, withEnchantmentBonus(player, nearby, SPIN_DAMAGE));

            Vec3 away = nearby.position().subtract(player.position());
            if (away.lengthSqr() < 1.0E-4) {
                away = player.getForward();
            }
            away = away.normalize();
            nearby.setDeltaMovement(nearby.getDeltaMovement()
                    .add(away.x * SPIN_KNOCKBACK, 0.25D, away.z * SPIN_KNOCKBACK));
            nearby.hurtMarked = true;

            level.playSound(null, nearby.getX(), nearby.getY(), nearby.getZ(),
                    ModSounds.NECROTIC_HIT_IMPACT.get(), player.getSoundSource(),
                    0.45F, 1.1F + level.random.nextFloat() * 0.1F);
        }

        triggerSwing(level, player, NecroticSlashVfx.STAGE_SPIN);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.NECROTIC_SPIN_SLASH.get(), player.getSoundSource(),
                0.7F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    private void performHit(ServerLevel level, Player player, LivingEntity target, int swingStage, float bonusDamage) {
        if (bonusDamage > 0) {
            target.hurt(player.damageSources().playerAttack(player), withEnchantmentBonus(player, target, bonusDamage));
        }

        applySplashDamage(level, player, target, SPLASH_DAMAGE);
        triggerSwing(level, player, swingStage);
        playSlashSound(level, player);
    }

    private void performFinisher(ServerLevel level, Player player, LivingEntity target) {
        target.hurt(player.damageSources().playerAttack(player), withEnchantmentBonus(player, target, HIT3_BONUS_DAMAGE));

        target.setDeltaMovement(target.getDeltaMovement().x, 0.6D, target.getDeltaMovement().z);
        target.hurtMarked = true;

        applySplashDamage(level, player, target, SPLASH_DAMAGE_FINISHER);
        triggerSwing(level, player, 2);
        playSlashSound(level, player);
        spawnGroundRupture(level, player);

        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                ModSounds.NECROTIC_RUPTURE.get(), player.getSoundSource(),
                0.7F, 0.95F + level.random.nextFloat() * 0.05F);
    }

    /**
     * Grieta en el piso del golpe final, ~4 bloques adelante del jugador
     * (igual que el "@forward{f=4}" del YAML original), pegada al piso
     * usando el heightmap de la columna.
     */
    private void spawnGroundRupture(ServerLevel level, Player player) {
        Vec3 look = player.getLookAngle();
        double spawnX = player.getX() + look.x * 4.0D;
        double spawnZ = player.getZ() + look.z * 4.0D;
        int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(spawnX), (int) Math.floor(spawnZ));

        NecroticGroundRuptureVfx rupture = new NecroticGroundRuptureVfx(ModEntities.NECROTIC_GROUND_RUPTURE.get(), level);
        rupture.moveTo(spawnX, groundY, spawnZ, player.getYRot(), 0.0F);
        level.addFreshEntity(rupture);
    }

    private void applySplashDamage(ServerLevel level, Player player, LivingEntity primaryTarget, float damage) {
        DamageSource damageSource = player.damageSources().playerAttack(player);

        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                primaryTarget.getBoundingBox().inflate(SPLASH_RADIUS))) {
            if (nearby == player || nearby == primaryTarget || !nearby.isAlive()) {
                continue;
            }

            nearby.hurt(damageSource, withEnchantmentBonus(player, nearby, damage));
            level.playSound(null, nearby.getX(), nearby.getY(), nearby.getZ(),
                    ModSounds.NECROTIC_HIT_IMPACT.get(), player.getSoundSource(),
                    0.45F, 1.1F + level.random.nextFloat() * 0.1F);
        }
    }

    /**
     * Suma el bonus de daño de los encantamientos del arma (Filo, Aspecto
     * Ígneo cuenta el daño de fuego aparte, no acá) al daño plano que
     * agregamos a mano. El golpe BASE (el de Minecraft, antes de que
     * onLeftClickEntity/use() hagan lo suyo) ya trae su propio bonus de
     * encantamiento aplicado automáticamente -- esto es solo para que nuestro
     * daño EXTRA (combo/splash/giro) también escale, en vez de quedar fijo.
     */
    private float withEnchantmentBonus(Player player, LivingEntity target, float baseDamage) {
        ItemStack weapon = player.getMainHandItem();
        float bonus = EnchantmentHelper.getDamageBonus(weapon, target.getMobType());
        return baseDamage + bonus;
    }

    private float withEnchantmentBonus(Player player, float baseDamage) {
        return withEnchantmentBonus(player, player, baseDamage);
    }

    private void triggerSwing(ServerLevel level, Player player, int stage) {
        NecroticSlashVfx companion = ACTIVE_SCYTHES.get(player.getUUID());
        if (companion == null || !companion.isAlive()) {
            companion = spawnCompanion(level, player);
        }
        companion.playSwing(stage);
    }

    private void playSlashSound(ServerLevel level, Player player) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.NECROTIC_SLASH.get(), player.getSoundSource(),
                0.7F, 0.8F + level.random.nextFloat() * 0.4F);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot == EquipmentSlot.MAINHAND) {
            Multimap<Attribute, AttributeModifier> modifiers = HashMultimap.create();
            modifiers.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_UUID, "Weapon modifier", BASE_ATTACK_DAMAGE, AttributeModifier.Operation.ADDITION));
            modifiers.put(Attributes.ATTACK_SPEED, new AttributeModifier(
                    ATTACK_SPEED_MODIFIER_UUID, "Weapon modifier", BASE_ATTACK_SPEED, AttributeModifier.Operation.ADDITION));
            modifiers.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(
                    ATTACK_REACH_MODIFIER_UUID, "Weapon reach modifier", BASE_ATTACK_REACH, AttributeModifier.Operation.ADDITION));
            return modifiers;
        }
        return super.getAttributeModifiers(slot, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.nanookmod.necrotic_scythe.combo").withStyle(ChatFormatting.DARK_PURPLE));
    }

    /**
     * Al extender Item (no SwordItem), vanilla por defecto NO deja aplicar
     * encantamientos de arma (Filo, Botín, Fuego, Empuje, etc.) en mesa de
     * encantar/yunque -- la categoría EnchantmentCategory.WEAPON solo
     * reconoce SwordItem por clase. Este override le dice a Forge que
     * trate esta arma igual que una espada para esos encantamientos.
     *
     * Los encantamientos vanilla (Fuego, Empuje, Botín) se aplican solos
     * sobre el golpe base, porque nunca cancelamos el ataque de Minecraft
     * (onLeftClickEntity/use() siempre devuelven false/no cancelan). Filo
     * además escala también el daño EXTRA que agregamos a mano (combo,
     * splash, giro) vía withEnchantmentBonus().
     */
    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        return enchantment.category == EnchantmentCategory.WEAPON || super.canApplyAtEnchantingTable(stack, enchantment);
    }

    @Override
    public int getEnchantmentValue() {
        return 14; // comparable a una espada de diamante
    }
}
