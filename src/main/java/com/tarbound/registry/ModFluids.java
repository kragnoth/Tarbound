package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.fluid.TarFluidType;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Tarbound.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Tarbound.MODID);

    public static final DeferredHolder<FluidType, TarFluidType> TAR_TYPE = FLUID_TYPES.register("tar", () -> new TarFluidType(
            FluidType.Properties.create()
                    .descriptionId("block.tarbound.tar")
                    .density(3000)
                    .viscosity(8000)
                    .temperature(300)
                    .motionScale(0.002D)
                    .fallDistanceModifier(0.0F)
                    .canSwim(false)
                    .canDrown(true)
                    .canExtinguish(true)
                    .canConvertToSource(false)
                    .supportsBoating(false)
                    .canHydrate(false)
                    .pathType(PathType.LAVA)
                    .adjacentPathType(null)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA)));

    // Spreads like lava (see TarFluid): the tick rate etc. in properties() are the Overworld values.
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> TAR = FLUIDS.register("tar", () -> new com.tarbound.fluid.TarFluid.Source(properties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_TAR = FLUIDS.register("flowing_tar", () -> new com.tarbound.fluid.TarFluid.Flowing(properties()));

    /** Essence of the Void: stored energy, 1 mB to 1 FE. The radio's battery shows as this in the pool. */
    public static final DeferredHolder<FluidType, FluidType> ESSENCE_TYPE = FLUID_TYPES.register("essence_of_the_void", () -> new FluidType(
            FluidType.Properties.create()
                    .descriptionId("block.tarbound.essence_of_the_void")
                    .density(-200)
                    .viscosity(600)
                    .temperature(3)
                    .lightLevel(10)
                    .rarity(net.minecraft.world.item.Rarity.UNCOMMON)
                    .canSwim(true)
                    .canDrown(true)
                    .canExtinguish(false)
                    .canConvertToSource(false)
                    .supportsBoating(false)
                    .canHydrate(false)
                    .motionScale(0.01D)
                    .fallDistanceModifier(0.0F)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)));

    public static final DeferredHolder<Fluid, BaseFlowingFluid.Source> ESSENCE = FLUIDS.register("essence_of_the_void",
            () -> new BaseFlowingFluid.Source(essenceProperties()));
    public static final DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_ESSENCE = FLUIDS.register("flowing_essence_of_the_void",
            () -> new BaseFlowingFluid.Flowing(essenceProperties()));

    private static BaseFlowingFluid.Properties essenceProperties() {
        return new BaseFlowingFluid.Properties(ESSENCE_TYPE, ESSENCE, FLOWING_ESSENCE)
                .bucket(ModItems.ESSENCE_BUCKET)
                .block(ModBlocks.ESSENCE_OF_THE_VOID)
                .tickRate(10)
                .slopeFindDistance(3)
                .levelDecreasePerBlock(2)
                .explosionResistance(100.0F);
    }

    /** {@code amount} mB of Essence of the Void. */
    public static net.neoforged.neoforge.fluids.FluidStack essence(int amount) {
        return new net.neoforged.neoforge.fluids.FluidStack(ESSENCE.get(), amount);
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TAR_TYPE, TAR, FLOWING_TAR)
                .bucket(ModItems.TAR_BUCKET)
                .block(ModBlocks.TAR)
                .tickRate(30)
                .slopeFindDistance(2)
                .levelDecreasePerBlock(2)
                .explosionResistance(100.0F);
    }

    private ModFluids() {}
}
