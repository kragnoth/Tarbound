package com.tarbound.fluid;

import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.config.TarboundConfig;
import com.tarbound.network.RadioRegistry;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModFluids;
import com.tarbound.registry.ModSounds;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Tar that swallows: an item lying in a radio's tar sinks for a moment, then the tar gulps it into that radio's
 * pool, like any other way into storage (free). What the pool can't take is spat back up and tried again later; it
 * is never deleted. Wild tar (no radio's scan holds it) only drags items down.
 *
 * <p>Left alone: items a hoard or another mod pins ({@code PreventRemoteMovement}), and items that can't be picked
 * up yet (a thrown item waits out its pickup delay; "never pick up" items are skipped for good).
 *
 * <p>Progress lives in the item entity's persistent data, so it survives saving and chunk unloads.
 */
public final class TarSwallow {
    /** How often (ticks) an item in tar is looked at. */
    private static final int STEP = 5;
    private static final String SINK = "tarbound:sink";
    private static final String SPAT = "tarbound:spat";
    private static final String PINNED = "PreventRemoteMovement";
    /** How far from a radio (blocks) its tar is looked for, as for deaths in tar. */
    private static final double RADIO_SEARCH = 512;

    private TarSwallow() {}

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof ItemEntity item) || !item.isAlive() || !item.isInFluidType(ModFluids.TAR_TYPE.get())) {
            return;
        }
        Level level = item.level();
        // Tar is thick: items in it slow right down and settle, on both sides so the client's guess matches.
        Vec3 v = item.getDeltaMovement();
        item.setDeltaMovement(v.x * 0.6, v.y * 0.6 - 0.004, v.z * 0.6);
        if (level.isClientSide) {
            bubbles(level, item);
            return;
        }
        if (item.tickCount % STEP != 0 || !TarboundConfig.SERVER.tarSwallows.get() || item.hasPickUpDelay()) {
            return;
        }
        CompoundTag data = item.getPersistentData();
        if (data.getBoolean(PINNED)) {
            return; // a hoard (or another mod) holds it
        }
        long now = level.getGameTime();
        if (data.contains(SPAT) && now < data.getLong(SPAT)) {
            return;
        }
        ScryingFontBlockEntity font = radioFor(level, item.blockPosition());
        if (font == null) {
            data.remove(SINK);
            return; // wild tar: it just sits there
        }
        int sunk = data.getInt(SINK) + STEP;
        if (sunk < TarboundConfig.SERVER.tarSwallowTicks.get()) {
            data.putInt(SINK, sunk);
            return;
        }
        data.remove(SINK);
        ItemStack stack = item.getItem();
        ItemStack rest = font.network().insert(stack.copy());
        if (rest.getCount() == stack.getCount()) {
            spit(item, data, now); // the pool is full (or none of its storage takes this)
        } else if (rest.isEmpty()) {
            level.playSound(null, item.getX(), item.getY(), item.getZ(), ModSounds.TAR_GULP.get(), SoundSource.BLOCKS, 0.8F,
                    0.8F + level.random.nextFloat() * 0.3F);
            item.discard();
        } else {
            item.setItem(rest);
            spit(item, data, now);
        }
    }

    /** The loaded radio whose tar is at {@code pos}, or null. */
    @Nullable
    private static ScryingFontBlockEntity radioFor(Level level, BlockPos pos) {
        for (BlockPos radioPos : RadioRegistry.all(level)) {
            if (radioPos.distSqr(pos) > RADIO_SEARCH * RADIO_SEARCH || !level.isLoaded(radioPos)
                    || !(level.getBlockEntity(radioPos) instanceof ScryingFontBlockEntity font)) {
                continue;
            }
            TarNetwork net = font.network();
            if (net.holdsTar(pos) || net.holdsTar(pos.below())) {
                return font;
            }
        }
        return null;
    }

    private static void spit(ItemEntity item, CompoundTag data, long now) {
        data.putLong(SPAT, now + TarboundConfig.SERVER.tarSwallowRetryTicks.get());
        Level level = item.level();
        item.setDeltaMovement(item.getDeltaMovement().add((level.random.nextDouble() - 0.5) * 0.1, 0.3, (level.random.nextDouble() - 0.5) * 0.1));
        item.hurtMarked = true; // send the hop to clients now
        level.playSound(null, item.getX(), item.getY(), item.getZ(), ModSounds.TAR_SPIT.get(), SoundSource.BLOCKS, 0.6F, 0.7F);
    }

    /** Client: slow black bubbles and the odd soul wisp around an item in tar. */
    private static void bubbles(Level level, ItemEntity item) {
        if (!TarboundConfig.CLIENT.tarSwallowParticles.get() || level.random.nextInt(4) != 0) {
            return;
        }
        double x = item.getX() + (level.random.nextDouble() - 0.5) * 0.4;
        double z = item.getZ() + (level.random.nextDouble() - 0.5) * 0.4;
        double y = item.getY() + 0.1;
        level.addParticle(ParticleTypes.SQUID_INK, x, y, z, 0, 0.02, 0);
        if (level.random.nextInt(6) == 0) {
            level.addParticle(ParticleTypes.SOUL, x, y + 0.2, z, 0, 0.03, 0);
        }
    }

    /** Client: lets players turn the gulp and spit off (client config). */
    public static void onPlaySound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        if (event.getSound() == null || TarboundConfig.CLIENT.tarGulpSound.get()) {
            return;
        }
        var id = event.getSound().getLocation();
        if (id.equals(ModSounds.TAR_GULP.getId()) || id.equals(ModSounds.TAR_SPIT.getId())) {
            event.setSound(null);
        }
    }
}
