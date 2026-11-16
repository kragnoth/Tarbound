package com.tarbound.registry;

import com.tarbound.Tarbound;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Tarbound.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.tarbound"))
            .icon(() -> ModItems.TAR_BUCKET.get().getDefaultInstance())
            .displayItems((params, output) -> {
                output.accept(ModItems.TAR_BUCKET.get());
                output.accept(ModItems.SCRYING_FONT.get());
                output.accept(ModItems.RELIQUARY.get());
                output.accept(ModItems.TAR_ANTENNA.get());
                output.accept(ModItems.TRANSIENT_ANTENNA.get());
                output.accept(ModItems.SHADE.get());
                output.accept(ModItems.STEWARD.get());
                output.accept(ModItems.VOTIVE_SKULL.get());
                output.accept(ModItems.OSSUARY.get());
                output.accept(ModItems.TAR_SOAKED_LOG.get());
                output.accept(ModItems.TAR_SOAKED_PLANKS.get());
                output.accept(ModItems.PITCH_CASK.get());
                output.accept(ModItems.VOTIVE_RACK.get());
                output.accept(ModItems.WEEPING_SEEP.get());
                output.accept(ModItems.BOTTOMLESS_SEEP.get());
                output.accept(ModItems.ESSENCE_BUCKET.get());
                output.accept(ModItems.SPIRIT_BOARD.get());
                output.accept(ModItems.SCRIBING_LECTERN.get());
                output.accept(ModItems.BLANK_CONTRACT.get());
                output.accept(ModItems.TALLOW.get());
                output.accept(ModItems.WEAKENED_SOUL.get());
                output.accept(ModItems.WEAKENED_BLAZE_SPAWN_EGG.get());
                // Plant Wax is only made by Create's mixer; without Create it would be a dead item.
                if (net.neoforged.fml.ModList.get().isLoaded("create")) {
                    output.accept(ModItems.PLANT_WAX.get());
                }
            })
            .build());

    private ModCreativeTabs() {}
}
