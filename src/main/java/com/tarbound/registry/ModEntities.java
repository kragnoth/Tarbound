package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.entity.WeakenedBlaze;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Tarbound.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<WeakenedBlaze>> WEAKENED_BLAZE = ENTITIES.register("weakened_blaze",
            () -> EntityType.Builder.<WeakenedBlaze>of(WeakenedBlaze::new, MobCategory.MONSTER)
                    .fireImmune()
                    .sized(0.6F, 1.8F)
                    .clientTrackingRange(8)
                    .build("weakened_blaze"));

    /** Set down from its item, never spawned; as tall as an enderman. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.tarbound.entity.Steward>> STEWARD = ENTITIES.register("steward",
            () -> EntityType.Builder.<com.tarbound.entity.Steward>of(com.tarbound.entity.Steward::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(0.6F, 2.9F)
                    .eyeHeight(2.55F)
                    .clientTrackingRange(8)
                    .build("steward"));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(WEAKENED_BLAZE.get(), WeakenedBlaze.createAttributes().build());
        event.put(STEWARD.get(), com.tarbound.entity.Steward.createAttributes().build());
    }

    /** Spawners only (it never spawns naturally); same light rules as a blaze. */
    public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(WEAKENED_BLAZE.get(), SpawnPlacementTypes.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Monster::checkAnyLightMonsterSpawnRules, RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ModEntities() {}
}
