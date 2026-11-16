package com.tarbound.compat.ponder;

import net.createmod.ponder.foundation.PonderIndex;

/** Registers Tarbound's ponder plugin. Only called (client side) when the Ponder library is loaded. */
public final class PonderCompat {
    private PonderCompat() {}

    public static void init() {
        PonderIndex.addPlugin(new TarboundPonderPlugin());
    }
}
