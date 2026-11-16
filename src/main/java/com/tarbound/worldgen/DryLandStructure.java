package com.tarbound.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tarbound.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A structure that only goes down on dry ground, then hands off to another structure (a jigsaw) to
 * build. Vanilla jigsaw structures sit on the water's surface over lakes, which freeze later in cold
 * forests: abandoned cabins on ice. This checks the footprint's corners and middle before building.
 *
 * <pre>{ "type": "tarbound:dry_land", "structure": "tarbound:researcher_cabin_layout",
 *   "size_x": 11, "size_z": 10, ...the usual biomes, step, terrain_adaptation, spawn_overrides }</pre>
 */
public class DryLandStructure extends Structure {
    public static final MapCodec<DryLandStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            settingsCodec(i),
            Structure.CODEC.fieldOf("structure").forGetter(s -> s.inner),
            com.mojang.serialization.Codec.INT.optionalFieldOf("size_x", 16).forGetter(s -> s.sizeX),
            com.mojang.serialization.Codec.INT.optionalFieldOf("size_z", 16).forGetter(s -> s.sizeZ)
    ).apply(i, DryLandStructure::new));

    private final Holder<Structure> inner;
    private final int sizeX;
    private final int sizeZ;

    public DryLandStructure(StructureSettings settings, Holder<Structure> inner, int sizeX, int sizeZ) {
        super(settings);
        this.inner = inner;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!isDry(context, sizeX, sizeZ)) {
            return Optional.empty();
        }
        return inner.value().findValidGenerationPoint(context); // biome check uses this structure's biomes (the context carries them)
    }

    /** The footprint's corners and middle are on dry ground (the surface, counting water, is the floor). */
    public static boolean isDry(GenerationContext context, int sizeX, int sizeZ) {
        int x0 = context.chunkPos().getMinBlockX();
        int z0 = context.chunkPos().getMinBlockZ();
        int[][] points = {{0, 0}, {sizeX - 1, 0}, {0, sizeZ - 1}, {sizeX - 1, sizeZ - 1}, {sizeX / 2, sizeZ / 2}};
        for (int[] p : points) {
            int x = x0 + p[0];
            int z = z0 + p[1];
            // Over water the surface (water counts) sits above the floor (it doesn't).
            int surface = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());
            int floor = context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                    context.heightAccessor(), context.randomState());
            if (surface != floor) {
                return false;
            }
        }
        return true;
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.DRY_LAND.get();
    }
}
