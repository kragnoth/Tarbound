package com.tarbound.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Recipe viewers list plain stacks ("a flint and steel"), but the one in storage has been used: it
 * carries damage, a repair cost or other data, so it isn't the same item type to the pool. Here a
 * plain alternative also matches any copy of that item in stock, so used tools fill recipes.
 * <p>
 * Alternatives that do carry data (a potion, an enchanted book) still need an exact match. Stock that
 * is enchanted, renamed or holding items (a filled shulker box or bundle) is only offered after
 * every ordinary copy, so a job won't eat your good pickaxe when a plain one would do.
 */
public final class LooseMatch {

    /** Each input's alternatives plus the stocked copies of its plain alternatives (exact ones first, precious ones last). */
    public static List<List<ItemStack>> widen(List<List<ItemStack>> inputs, Iterable<ItemKey> stock) {
        return widen(inputs, stock, null);
    }

    /** As {@link #widen(List, Iterable)}, with the stock already indexed by {@link #index} (for checking many recipes at once). */
    public static List<List<ItemStack>> widen(List<List<ItemStack>> inputs, Iterable<ItemKey> stock, @javax.annotation.Nullable Map<Item, List<ItemKey>> indexed) {
        Map<Item, List<ItemKey>> byItem = indexed;
        List<List<ItemStack>> out = new ArrayList<>(inputs.size());
        for (List<ItemStack> alts : inputs) {
            boolean anyPlain = false;
            for (ItemStack a : alts) {
                anyPlain |= !a.isEmpty() && a.getComponentsPatch().isEmpty();
            }
            if (!anyPlain) {
                out.add(alts);
                continue;
            }
            if (byItem == null) {
                byItem = index(stock);
            }
            List<ItemStack> wide = new ArrayList<>(alts);
            List<ItemStack> precious = new ArrayList<>();
            Set<ItemKey> seen = new HashSet<>();
            for (ItemStack a : alts) {
                seen.add(ItemKey.of(a));
            }
            for (ItemStack a : alts) {
                if (a.isEmpty() || !a.getComponentsPatch().isEmpty()) {
                    continue;
                }
                int count = Math.max(1, a.getCount());
                for (ItemKey k : byItem.getOrDefault(a.getItem(), List.of())) {
                    if (seen.add(k)) {
                        (isPrecious(k.proto()) ? precious : wide).add(k.proto().copyWithCount(count));
                    }
                }
            }
            wide.addAll(precious);
            out.add(wide.size() == alts.size() ? alts : wide);
        }
        return out;
    }

    /** One input's alternatives, widened. */
    public static List<ItemStack> widenOne(List<ItemStack> alts, Iterable<ItemKey> stock) {
        return widen(List.of(alts), stock).get(0);
    }

    /** Enchanted, renamed, or holding items: only used when nothing ordinary will do. */
    public static boolean isPrecious(ItemStack stack) {
        ItemEnchantments ench = stack.get(DataComponents.ENCHANTMENTS);
        if (ench != null && !ench.isEmpty()) {
            return true;
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            return true;
        }
        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        if (container != null && !container.equals(ItemContainerContents.EMPTY)) {
            return true;
        }
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle != null && !bundle.isEmpty();
    }

    /** Stocked items carrying data, by item: what {@link #widen} looks through. */
    public static Map<Item, List<ItemKey>> index(Iterable<ItemKey> stock) {
        Map<Item, List<ItemKey>> byItem = new HashMap<>();
        for (ItemKey k : stock) {
            if (!k.proto().getComponentsPatch().isEmpty()) {
                byItem.computeIfAbsent(k.proto().getItem(), i -> new ArrayList<>()).add(k);
            }
        }
        return byItem;
    }

    private LooseMatch() {}
}
