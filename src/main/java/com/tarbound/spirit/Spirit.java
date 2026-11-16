package com.tarbound.spirit;

import com.tarbound.Tarbound;
import com.tarbound.config.TarboundConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/**
 * Spirit: what a radio's automation runs on. Each radio holds a store of it, fed with offerings
 * (candles, by default) by hand, by hopper or pipe, or from the pool. Storage access never costs
 * anything; loading machines, crafting stations, Spirit Fire, machine power and Spirit Boards do.
 * <p>
 * What counts as an offering, and what it's worth, is the item data map
 * {@code data/<namespace>/data_maps/item/spirit_offerings.json}, so packs can change it without code:
 * <pre>{ "values": { "#minecraft:candles": 200, "minecraft:soul_lantern": 50 } }</pre>
 */
public final class Spirit {
    private Spirit() {}

    public static final DataMapType<Item, Integer> OFFERINGS = DataMapType.builder(
                    Tarbound.id("spirit_offerings"), Registries.ITEM, ExtraCodecs.POSITIVE_INT)
            .synced(ExtraCodecs.POSITIVE_INT, false)
            .build();

    /**
     * What a Weeping Seep will make for orders, and the Spirit each one costs: item data map
     * {@code data/<namespace>/data_maps/item/seep_yields.json}, e.g. {@code { "values": { "minecraft:cobblestone": 2 } }}.
     * Items not in it are never made.
     */
    public static final DataMapType<Item, Integer> SEEP_YIELDS = DataMapType.builder(
                    Tarbound.id("seep_yields"), Registries.ITEM, ExtraCodecs.POSITIVE_INT)
            .synced(ExtraCodecs.POSITIVE_INT, false)
            .build();

    public static void registerDataMaps(RegisterDataMapTypesEvent event) {
        event.register(OFFERINGS);
        event.register(SEEP_YIELDS);
    }

    /** Spirit one of this item is worth as an offering, or 0 if it isn't one. */
    public static int value(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Integer v = stack.getItemHolder().getData(OFFERINGS);
        return v == null ? 0 : v;
    }

    /** Costs are on (server config). */
    public static boolean costs() {
        return TarboundConfig.SERVER.spiritCosts.get();
    }

    public static int capacity() {
        return TarboundConfig.SERVER.spiritCapacity.get();
    }
}
