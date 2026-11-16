package com.tarbound.fluid;

import com.tarbound.Tarbound;
import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.config.TarboundConfig;
import com.tarbound.network.RadioRegistry;
import com.tarbound.registry.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Tar is bad for mobs: anything alive that wades in is poisoned and slowly hurt (players never are).
 * A mob that dies in a radio's tar feeds that radio Spirit, a little for each point of its health.
 */
public final class TarHazard {
    public static final ResourceKey<DamageType> TAR = ResourceKey.create(Registries.DAMAGE_TYPE, Tarbound.id("tar"));
    /** How far from a radio (blocks) a death is checked against its tar. */
    private static final double RADIO_SEARCH = 512;

    private TarHazard() {}

    private static boolean harmed(LivingEntity e) {
        return !(e instanceof Player) && !(e instanceof ArmorStand) && !(e instanceof com.tarbound.entity.Steward) && e.isAlive();
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity mob) || mob.level().isClientSide || mob.tickCount % 20 != 0
                || !harmed(mob) || !TarboundConfig.SERVER.tarHarmsMobs.get() || !mob.isInFluidType(ModFluids.TAR_TYPE.get())) {
            return;
        }
        mob.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
        float damage = TarboundConfig.SERVER.tarDamage.get().floatValue();
        if (damage > 0) {
            mob.hurt(mob.damageSources().source(TAR), damage); // poison alone never kills; this does
        }
    }

    public static void onDeath(LivingDeathEvent event) {
        LivingEntity mob = event.getEntity();
        Level level = mob.level();
        double per = TarboundConfig.SERVER.tarSpiritPerHealth.get();
        if (level.isClientSide || mob instanceof Player || mob instanceof com.tarbound.entity.Steward || per <= 0
                || !(mob.isInFluidType(ModFluids.TAR_TYPE.get()) || event.getSource().is(TAR))) {
            return;
        }
        int spirit = (int) Math.max(1, Math.round(mob.getMaxHealth() * per));
        BlockPos feet = mob.blockPosition();
        BlockPos eyes = BlockPos.containing(mob.getEyePosition());
        for (BlockPos radioPos : RadioRegistry.all(level)) {
            if (radioPos.distSqr(feet) > RADIO_SEARCH * RADIO_SEARCH || !level.isLoaded(radioPos)
                    || !(level.getBlockEntity(radioPos) instanceof ScryingFontBlockEntity font)) {
                continue;
            }
            var net = font.network();
            if (net.holdsTar(feet) || net.holdsTar(eyes) || net.holdsTar(feet.below())) {
                font.addSpirit(spirit);
                return; // one radio per death
            }
        }
    }
}
