package com.tarbound.compat;

import javax.annotation.Nullable;

/**
 * Client side: a link from the radio's search box to the recipe viewer's search (JEI and/or EMI).
 * The viewers' plugins fill in a bridge when they load; nothing here touches their classes, so this
 * is safe to call whether or not they're installed.
 */
public final class SearchSync {
    /** One recipe viewer's search text. */
    public interface Bridge {
        String get();

        void set(String text);
    }

    @Nullable
    public static volatile Bridge jei;

    /**
     * JEI's own verdict on a search: what its ingredient list shows for the text in its box. Lets the
     * radio's search work exactly like JEI's (@mod, #tooltip, $tag, ^color, &id, -, |, and whatever JEI adds).
     */
    public interface Verdict {
        /** @return true/false if JEI lists this item at all, null if JEI doesn't know it (decide some other way) */
        @Nullable
        Boolean item(net.minecraft.world.item.ItemStack stack);

        @Nullable
        Boolean fluid(net.neoforged.neoforge.fluids.FluidStack stack);
    }

    /** Builds a {@link Verdict} from JEI's current filter; set while JEI's runtime is up. */
    @Nullable
    public static volatile java.util.function.Supplier<Verdict> jeiVerdict;

    /** JEI's verdict for {@code text}, if JEI is loaded and its box holds exactly that text; else null. */
    @Nullable
    public static Verdict jeiFor(String text) {
        java.util.function.Supplier<Verdict> v = jeiVerdict;
        Bridge b = jei;
        if (v == null || b == null) {
            return null;
        }
        try {
            return text.trim().equalsIgnoreCase(b.get().trim()) ? v.get() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
    @Nullable
    public static volatile Bridge emi;

    private SearchSync() {}

    /** A recipe viewer with a search box is loaded. */
    public static boolean available() {
        return jei != null || emi != null;
    }

    /** The viewer's search text (JEI first if both are installed), or null if neither is loaded. */
    @Nullable
    public static String get() {
        Bridge b = jei != null ? jei : emi;
        if (b == null) {
            return null;
        }
        try {
            return b.get();
        } catch (RuntimeException e) {
            return null; // viewer not ready (e.g. still reloading)
        }
    }

    /** Sets every loaded viewer's search text. */
    public static void set(String text) {
        for (Bridge b : new Bridge[] {jei, emi}) {
            if (b != null) {
                try {
                    if (!text.equals(b.get())) {
                        b.set(text);
                    }
                } catch (RuntimeException ignored) {
                    // viewer not ready
                }
            }
        }
    }
}
