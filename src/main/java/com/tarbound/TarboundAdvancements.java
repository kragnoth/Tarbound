package com.tarbound;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Awards Tarbound advancements that no vanilla trigger can see (finishing an order, opening every
 * contract slot...). Each such advancement has one {@code minecraft:impossible} criterion named
 * {@code done}; the code grants it at the moment the thing happens.
 */
public final class TarboundAdvancements {
    public static final String FEED_RADIO = "feed_radio";
    public static final String CELL_TOWER = "cell_tower";
    public static final String FIRST_ORDER = "first_order";
    public static final String SIXTEEN_CANDLES = "sixteen_candles";
    public static final String ALL_SIDES = "all_sides";
    public static final String SPIRIT_BOARD = "spirit_board";
    public static final String SCORCHED_SPAWNER = "scorched_spawner";

    /** Grants {@code tarbound:<path>} to {@code player} (server side; does nothing for others or if it's missing). */
    public static void award(Player player, String path) {
        if (!(player instanceof ServerPlayer sp) || sp.server == null) {
            return;
        }
        AdvancementHolder adv = sp.server.getAdvancements().get(Tarbound.id(path));
        if (adv != null) {
            sp.getAdvancements().award(adv, "done");
        }
    }

    private TarboundAdvancements() {}
}
