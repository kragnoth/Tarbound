package com.tarbound.compat.jade;

import com.tarbound.Tarbound;
import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.network.TarNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Scrying Font: what it has bound, its jobs, and any antennas it can't reach. */
public enum FontJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = Tarbound.id("scrying_font");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof ScryingFontBlockEntity font) {
            TarNetwork net = font.network(); // this tick's shared scan
            data.putInt("inventories", net.storage().size());
            data.putInt("machines", net.machines().size());
            data.putInt("jobs", font.jobs().size());
            data.putInt("racks", font.racks());
            data.putInt("jobLimit", font.jobLimit());
            data.putInt("outOfRange", net.outOfRange());
            data.putBoolean("crafting", net.hasCraftingTable());
            data.putInt("energy", font.energy());
            data.putInt("energyCap", com.tarbound.block.ScryingFontBlockEntity.batteryCapacity());
            if (com.tarbound.spirit.Spirit.costs()) {
                data.putInt("spirit", font.spirit());
                data.putInt("spiritCap", com.tarbound.spirit.Spirit.capacity());
            }
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains("inventories")) {
            return;
        }
        if (data.contains("spirit")) {
            int spirit = data.getInt("spirit");
            tooltip.add(Component.translatable("gui.tarbound.spirit.meter", spirit, data.getInt("spiritCap"))
                    .withStyle(spirit > 0 ? ChatFormatting.GOLD : ChatFormatting.LIGHT_PURPLE));
        }
        if (data.getInt("energyCap") > 0) {
            tooltip.add(Component.translatable("jade.tarbound.font.battery", data.getInt("energy"), data.getInt("energyCap"))
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
        tooltip.add(Component.translatable("jade.tarbound.font.bound", data.getInt("inventories"), data.getInt("machines"))
                .withStyle(ChatFormatting.GRAY));
        if (data.getInt("jobs") > 0) {
            tooltip.add(Component.translatable("jade.tarbound.font.jobs", data.getInt("jobs")).withStyle(ChatFormatting.AQUA));
        }
        if (data.getInt("racks") > 0) {
            tooltip.add(Component.translatable("jade.tarbound.racks", data.getInt("racks"), data.getInt("jobLimit")).withStyle(ChatFormatting.GOLD));
        }
        if (!data.getBoolean("crafting")) {
            tooltip.add(Component.translatable("jade.tarbound.font.no_crafting").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (data.getInt("outOfRange") > 0) {
            tooltip.add(Component.translatable("gui.tarbound.antennas_out_of_range", data.getInt("outOfRange")).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
