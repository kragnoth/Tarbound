package com.tarbound.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A blaze worn thin: same flight, fireballs and behaviour as a blaze, but half the health, half the
 * fireball and melee damage, half the XP, and it drops Weakened Souls instead of blaze rods.
 * Made by flint-and-steeling a blaze spawner, which then spawns these instead.
 */
public class WeakenedBlaze extends Blaze {
    public WeakenedBlaze(EntityType<? extends Blaze> type, Level level) {
        super(type, level);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Blaze.createAttributes()
                .add(Attributes.MAX_HEALTH, 10.0)
                .add(Attributes.ATTACK_DAMAGE, 3.0);
    }

    /** Its fireballs land at half strength (the blaze's attack goal is private, so this is done on impact). */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getSource().getDirectEntity() instanceof SmallFireball fireball && fireball.getOwner() instanceof WeakenedBlaze) {
            event.setAmount(event.getAmount() * 0.5F);
        }
    }
}
