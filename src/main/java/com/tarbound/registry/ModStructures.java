package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.worldgen.DryLandStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModStructures {
    public static final DeferredRegister<StructureType<?>> TYPES = DeferredRegister.create(Registries.STRUCTURE_TYPE, Tarbound.MODID);

    public static final DeferredHolder<StructureType<?>, StructureType<DryLandStructure>> DRY_LAND =
            TYPES.register("dry_land", () -> () -> DryLandStructure.CODEC);

    private ModStructures() {}
}
