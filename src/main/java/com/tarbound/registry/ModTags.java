package com.tarbound.registry;

import com.tarbound.Tarbound;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public final class ModTags {
    /**
     * Blocks with an item inventory that is really a conveyor (Create chutes, belts, tunnels, depots...).
     * They never join the pool, even touching tar or an antenna, so the radio can't pull items out of
     * them mid-flight or push items into them. A Reliquary on one still binds it as a machine.
     */
    /** Solid blocks that carry tar like the fluid does: links can run through them. */
    public static final TagKey<Block> TAR_CONDUITS = TagKey.create(Registries.BLOCK, Tarbound.id("tar_conduits"));

    public static final TagKey<Block> NEVER_POOLED = TagKey.create(Registries.BLOCK, Tarbound.id("never_pooled"));

    /**
     * The only pool items jobs may burn as furnace fuel. Anything with a burn time would otherwise
     * do, and that includes wooden tools, chests, crafting tables and bows. Add your pack's fuels here.
     */
    public static final TagKey<Item> POOL_FUEL = TagKey.create(Registries.ITEM, Tarbound.id("pool_fuel"));
    /**
     * Recipe inputs a machine keeps rather than uses up (AE2's inscriber presses, molds and dies): a contract
     * input made only of these is loaded once, left in the machine, needs no more than the one, and is never
     * counted per operation by the planner.
     */
    public static final TagKey<Item> STAYS_IN_MACHINE = TagKey.create(Registries.ITEM, Tarbound.id("stays_in_machine"));

    /**
     * Surfaces a machine works on from above (Create's depot and belt, under a spout or press). A machine
     * with no item slots of its own, bound by a Reliquary, uses the one directly below it as its slots.
     */
    public static final TagKey<Block> WORK_SURFACE = TagKey.create(Registries.BLOCK, Tarbound.id("work_surface"));

    /** Storage an all-sides Reliquary leaves alone (it works every other neighbour with slots, tanks or power). */
    public static final TagKey<Block> RELIQUARY_IGNORES = TagKey.create(Registries.BLOCK, Tarbound.id("reliquary_ignores"));

    /** Blocks a Reliquary turns into a stonecutting station (vanilla's stonecutter). */
    public static final TagKey<Block> CUTTING_STATIONS = TagKey.create(Registries.BLOCK, Tarbound.id("cutting_stations"));
    /** Blocks a Reliquary turns into a composting station (vanilla's composter). */
    public static final TagKey<Block> COMPOSTING_STATIONS = TagKey.create(Registries.BLOCK, Tarbound.id("composting_stations"));
    /** What a composting station takes from the pool when its Reliquary has no standing contract to say otherwise. */
    public static final TagKey<Item> COMPOST_FODDER = TagKey.create(Registries.ITEM, Tarbound.id("compost_fodder"));

    private ModTags() {}
}
