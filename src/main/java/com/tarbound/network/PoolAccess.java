package com.tarbound.network;

import net.minecraft.util.StringRepresentable;

/**
 * How the radio may use a pooled inventory, set by the mode of the antenna it touches (like an AE2
 * storage bus's access setting).
 * <ul>
 *   <li>{@link #COMMUNE}: both ways (read/write). The default, and what touching tar gives.</li>
 *   <li>{@link #SCRY}: the radio sees and takes, but never puts anything in (read only). For output
 *       chests you still want to craft from.</li>
 *   <li>{@link #TITHE}: the radio puts things in, but never sees or takes them again (write only,
 *       contents hidden). For feed or void chests.</li>
 * </ul>
 */
public enum PoolAccess implements StringRepresentable {
    COMMUNE("commune", true, true),
    SCRY("scry", true, false),
    TITHE("tithe", false, true);

    private final String name;
    private final boolean extract;
    private final boolean insert;

    PoolAccess(String name, boolean extract, boolean insert) {
        this.name = name;
        this.extract = extract;
        this.insert = insert;
    }

    /** The radio may take from it, and lists its contents. */
    public boolean canExtract() {
        return extract;
    }

    /** The radio may put things into it. */
    public boolean canInsert() {
        return insert;
    }

    public PoolAccess next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /**
     * The stricter of two accesses, for an inventory bound more than one way.
     *
     * @return null if together they allow nothing (Scry with Tithe)
     */
    public static PoolAccess stricter(PoolAccess a, PoolAccess b) {
        boolean ex = a.extract && b.extract;
        boolean in = a.insert && b.insert;
        if (ex && in) {
            return COMMUNE;
        }
        return ex ? SCRY : in ? TITHE : null;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
