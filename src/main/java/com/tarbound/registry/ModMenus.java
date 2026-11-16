package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.menu.ScribingLecternMenu;
import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.menu.ReliquaryMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Tarbound.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ScryingFontMenu>> SCRYING_FONT = MENUS.register("scrying_font",
            () -> IMenuTypeExtension.create(ScryingFontMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<ReliquaryMenu>> RELIQUARY = MENUS.register("reliquary",
            () -> IMenuTypeExtension.create(ReliquaryMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<ScribingLecternMenu>> SCRIBING_LECTERN = MENUS.register("scribing_lectern",
            () -> IMenuTypeExtension.create(ScribingLecternMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<com.tarbound.menu.OssuaryMenu>> OSSUARY = MENUS.register("ossuary",
            () -> IMenuTypeExtension.create(com.tarbound.menu.OssuaryMenu::fromNetwork));

    public static final DeferredHolder<MenuType<?>, MenuType<com.tarbound.menu.StewardMenu>> STEWARD = MENUS.register("steward",
            () -> IMenuTypeExtension.create(com.tarbound.menu.StewardMenu::fromNetwork));

    private ModMenus() {}
}
