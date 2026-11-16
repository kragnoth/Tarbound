package com.tarbound.net;

import javax.annotation.Nullable;

/**
 * The last radio bindings the server sent this client ({@link BindingsPayload}). Kept in common code, with no client
 * classes, so the payload handler is safe to register on both sides; the client's BindingHighlight reads it.
 */
public final class ClientBindings {
    @Nullable
    private static volatile BindingsPayload latest;

    private ClientBindings() {}

    static void receive(BindingsPayload payload) {
        latest = payload;
    }

    @Nullable
    public static BindingsPayload latest() {
        return latest;
    }

    public static void clear() {
        latest = null;
    }
}
