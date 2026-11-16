package com.tarbound.entity;

import com.tarbound.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Flint and steel on a spawner that holds a blaze (as its next spawn or anywhere in its spawn list)
 * scorches the spirit out of it: from then on it spawns Weakened Blazes. Costs one durability.
 */
public final class BlazeSpawnerConversion {
    private BlazeSpawnerConversion() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.FLINT_AND_STEEL)) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof SpawnerBlockEntity spawner)) {
            return;
        }
        if (!holdsBlaze(spawner, level)) {
            return;
        }
        // Handled here on both sides, so the flint and steel doesn't also light a fire.
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (level.isClientSide) {
            return;
        }
        spawner.setEntityId(ModEntities.WEAKENED_BLAZE.get(), level.getRandom());
        com.tarbound.TarboundAdvancements.award(event.getEntity(), com.tarbound.TarboundAdvancements.SCORCHED_SPAWNER);
        spawner.setChanged();
        level.sendBlockUpdated(pos, spawner.getBlockState(), spawner.getBlockState(), 3);
        level.playSound(null, pos, SoundEvents.FLINTANDSTEEL_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        }
        if (event.getEntity() instanceof LivingEntity user) {
            stack.hurtAndBreak(1, user, LivingEntity.getSlotForHand(event.getHand()));
        }
    }

    /** The spawner's next spawn, or any entry in its spawn list, is a blaze. */
    private static boolean holdsBlaze(SpawnerBlockEntity spawner, Level level) {
        CompoundTag tag = spawner.getSpawner().save(new CompoundTag());
        if (isBlaze(tag.getCompound("SpawnData"))) {
            return true;
        }
        ListTag potentials = tag.getList("SpawnPotentials", Tag.TAG_COMPOUND);
        for (int i = 0; i < potentials.size(); i++) {
            if (isBlaze(potentials.getCompound(i).getCompound("data"))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBlaze(CompoundTag spawnData) {
        return "minecraft:blaze".equals(spawnData.getCompound("entity").getString("id"));
    }
}
