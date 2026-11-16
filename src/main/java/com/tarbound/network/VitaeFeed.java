package com.tarbound.network;

import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.config.TarboundConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * NeoVitae (0.57): a Reliquary feeds Essentia Vitae to the station beneath it, paid in Spirit.
 *
 * <ul>
 *   <li><b>Ara Vitae</b> (the blood altar): only while it's crafting a recipe, and only what that recipe still
 *       needs (never orbs, never past the craft), straight into its main tank, at most {@code vitaePerSecond}.</li>
 *   <li><b>Other stations</b> in the {@code tarbound:vitae_fed} block tag (the Athanor by default): their vitae
 *       tank is kept topped up to {@code vitaeTopUp} mB.</li>
 * </ul>
 *
 * <p>Price: one Spirit buys {@code vitaePerSpirit} Vitae. For an altar craft, Vitae already in the pool is amplified:
 * each mB drawn from the pool is matched by one bought, so it arrives doubled and every Spirit goes twice as far.
 * Tag-fed stations take pool Vitae one for one, then buy the rest (0.59: their tanks can be pumped out, and doubling
 * there looped back into the pool).
 *
 * <p>NeoVitae is reached by reflection (no compile dependency); without it, none of this runs.
 */
public final class VitaeFeed {
    public static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> VITAE_FED =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, com.tarbound.Tarbound.id("vitae_fed"));
    /** How often (ticks) a Reliquary feeds; amounts are per second, so this is also the batch size. */
    public static final int INTERVAL = 20;

    private static final String ALTAR = "com.breakinblocks.neovitae.common.blockentity.AraVitaeTile";
    private static final ResourceLocation VITAE_ID = ResourceLocation.fromNamespaceAndPath("neovitae", "essentia_vitae_source");
    private static final Map<Class<?>, Altar> ALTARS = new ConcurrentHashMap<>();
    private static Fluid vitae;

    private VitaeFeed() {}

    /** Essentia Vitae, or null without NeoVitae. */
    @Nullable
    public static Fluid vitae() {
        if (vitae == null) {
            Fluid f = BuiltInRegistries.FLUID.get(VITAE_ID);
            vitae = f == null ? Fluids.EMPTY : f;
        }
        return vitae == Fluids.EMPTY ? null : vitae;
    }

    /** Spirit-bought Vitae a Reliquary holds but hasn't delivered (like its FE credit, not saved). */
    public interface Credit {
        long vitaeCredit();

        void vitaeCredit(long credit);
    }

    /** One feed for the machine at {@code machine}, from the Reliquary at {@code reliquary}. */
    public static void feed(Level level, BlockPos reliquary, BlockPos machine, @Nullable ScryingFontBlockEntity radio, Credit credit) {
        if (!TarboundConfig.SERVER.vitaeFeed.get()) {
            return;
        }
        Fluid fluid = vitae();
        if (fluid == null) {
            return;
        }
        BlockEntity be = level.getBlockEntity(machine);
        if (be == null) {
            return;
        }
        Altar altar = altar(be.getClass());
        if (altar != null) {
            int want = altar.wanted(be, TarboundConfig.SERVER.vitaePerSecond.get());
            if (want > 0) {
                int got = buy(fluid, want, radio, credit, true);
                if (got > 0) {
                    altar.give(be, got);
                }
            }
            return;
        }
        if (!be.getBlockState().is(VITAE_FED)) {
            return;
        }
        Direction side = Direction.getNearest(reliquary.getX() - machine.getX(), reliquary.getY() - machine.getY(),
                reliquary.getZ() - machine.getZ());
        IFluidHandler tank = level.getCapability(Capabilities.FluidHandler.BLOCK, machine, side);
        if (tank == null) {
            return;
        }
        long held = 0;
        for (int i = 0; i < tank.getTanks(); i++) {
            FluidStack in = tank.getFluidInTank(i);
            if (in.is(fluid)) {
                held += in.getAmount();
            }
        }
        int want = (int) Math.min(TarboundConfig.SERVER.vitaePerSecond.get(), TarboundConfig.SERVER.vitaeTopUp.get() - held);
        if (want <= 0) {
            return;
        }
        want = tank.fill(new FluidStack(fluid, want), IFluidHandler.FluidAction.SIMULATE);
        if (want <= 0) {
            return;
        }
        int got = buy(fluid, want, radio, credit, false);
        if (got > 0) {
            tank.fill(new FluidStack(fluid, got), IFluidHandler.FluidAction.EXECUTE);
        }
    }

    /**
     * Gets up to {@code want} Vitae, bought with Spirit at {@code vitaePerSpirit}. With {@code amplify} (altar crafts),
     * each bought mB is matched by one from the pool if it has any, so pool Vitae arrives doubled. Without it, pool
     * Vitae is used one for one first and only the rest is bought. @return how much to deliver
     */
    static int buy(Fluid fluid, int want, @Nullable ScryingFontBlockEntity radio, Credit credit, boolean amplify) {
        TarNetwork net = radio == null ? null : radio.network();
        long pool = net == null ? 0 : net.fluidAmount(new FluidStack(fluid, 1));
        if (!amplify) {
            int taken = pool <= 0 ? 0 : net.drainFluid(new FluidStack(fluid, 1), (int) Math.min(pool, want), false);
            return taken + (taken < want ? purchase(fluid, want - taken, radio, credit, 0) : 0);
        }
        return purchase(fluid, want, radio, credit, pool);
    }

    /** Buys Vitae with Spirit, matching each bought mB with one of the {@code pool} mB on offer (0 for none). */
    private static int purchase(Fluid fluid, int want, @Nullable ScryingFontBlockEntity radio, Credit credit, long pool) {
        TarNetwork net = radio == null ? null : radio.network();
        int fromPool = (int) Math.min(pool, want / 2);
        int toBuy = want - fromPool;
        boolean costs = com.tarbound.spirit.Spirit.costs();
        long have = costs ? credit.vitaeCredit() : Long.MAX_VALUE;
        if (costs && have < toBuy && radio != null) {
            int per = TarboundConfig.SERVER.vitaePerSpirit.get();
            int spirit = (int) Math.min(radio.spirit(), (toBuy - have + per - 1) / per);
            if (spirit > 0 && radio.pay(spirit)) {
                have += (long) spirit * per;
            }
        }
        int bought = (int) Math.min(have, toBuy);
        if (bought <= 0) {
            return 0;
        }
        int matched = Math.min(fromPool, bought); // pool Vitae only comes with Spirit-bought Vitae beside it
        if (matched > 0) {
            matched = net.drainFluid(new FluidStack(fluid, matched), matched, false);
        }
        if (costs) {
            credit.vitaeCredit(have - bought);
        }
        return bought + matched;
    }

    // ------------------------------------------------------------------------------------------
    // The altar, by reflection
    // ------------------------------------------------------------------------------------------

    @Nullable
    private static Altar altar(Class<?> type) {
        if (!type.getName().equals(ALTAR)) {
            return null;
        }
        Altar a = ALTARS.computeIfAbsent(type, Altar::of);
        return a == Altar.NONE ? null : a;
    }

    /** NeoVitae's Ara Vitae: what its current craft still needs, and a way to pour it into the main tank. */
    private record Altar(MethodHandle recipe, MethodHandle required, MethodHandle progress, MethodHandle main,
            MethodHandle charging, MethodHandle capacity, MethodHandle add) {
        static final Altar NONE = new Altar(null, null, null, null, null, null, null);

        static Altar of(Class<?> type) {
            try {
                MethodHandles.Lookup l = MethodHandles.publicLookup();
                return new Altar(
                        l.findVirtual(type, "getCurrentRecipe", MethodType.methodType(type.getMethod("getCurrentRecipe").getReturnType())),
                        l.findVirtual(type, "getLiquidRequired", MethodType.methodType(int.class)),
                        l.findVirtual(type, "getProgress", MethodType.methodType(int.class)),
                        l.findVirtual(type, "getMainTank", MethodType.methodType(int.class)),
                        l.findVirtual(type, "getChargingTank", MethodType.methodType(int.class)),
                        l.findVirtual(type, "getMainCapacity", MethodType.methodType(int.class)),
                        l.findVirtual(type, "addSacrificeEV", MethodType.methodType(int.class, int.class, boolean.class)));
            } catch (ReflectiveOperationException | RuntimeException e) {
                com.tarbound.Tarbound.LOGGER.warn("Tarbound: NeoVitae's altar changed shape; Reliquaries won't feed it Vitae ({})", e.toString());
                return NONE;
            }
        }

        /** Vitae the craft in progress still needs and the main tank has room for (0 if it isn't crafting). */
        int wanted(Object tile, int most) {
            try {
                if (recipe.invoke(tile) == null) {
                    return 0;
                }
                int need = (int) required.invoke(tile) - (int) progress.invoke(tile) - (int) main.invoke(tile) - (int) charging.invoke(tile);
                int room = (int) capacity.invoke(tile) - (int) main.invoke(tile);
                return Math.max(0, Math.min(most, Math.min(need, room)));
            } catch (Throwable t) {
                return 0;
            }
        }

        void give(Object tile, int amount) {
            try {
                add.invoke(tile, amount, true); // as a sacrifice: the altar's sacrifice runes add their share
            } catch (Throwable t) {
                // a failed pour is lost Vitae, nothing worse
            }
        }
    }
}
