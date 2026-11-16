package com.tarbound.compat;

import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.net.MachineFillPayload;
import com.tarbound.net.TrayPayload;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client-side helpers for filling a bound machine from a recipe viewer: one operation's worth of
 * ingredients (plus fuel, if the machine burns any), the way a player would load it by hand.
 */
public final class MachineFill {
    /**
     * The bound machine that runs this recipe: one whose block item is among the recipe's catalysts
     * (JEI) / workstations (EMI). The machine selected in the tray wins if it qualifies.
     */
    @Nullable
    public static TrayPayload.Machine findMachine(ScryingFontMenu menu, Collection<ItemStack> catalysts) {
        TrayPayload tray = menu.clientTray();
        if (tray == null || catalysts.isEmpty()) {
            return null;
        }
        Set<Item> items = new HashSet<>();
        for (ItemStack s : catalysts) {
            if (!s.isEmpty()) {
                items.add(s.getItem());
            }
        }
        TrayPayload.Machine first = null;
        for (TrayPayload.Machine b : tray.machines()) {
            if (!items.contains(b.state().getBlock().asItem())) {
                continue;
            }
            if (b.pos().equals(menu.clientSelection())) {
                return b;
            }
            if (first == null) {
                first = b;
            }
        }
        return first;
    }

    public static void send(ScryingFontMenu menu, TrayPayload.Machine machine, List<List<ItemStack>> inputs) {
        send(menu, machine, inputs, List.of());
    }

    public static void send(ScryingFontMenu menu, TrayPayload.Machine machine, List<List<ItemStack>> inputs,
                            List<net.neoforged.neoforge.fluids.FluidStack> fluids) {
        PacketDistributor.sendToServer(new MachineFillPayload(menu.containerId, machine.pos(), inputs, fluids));
    }

    private MachineFill() {}
}
