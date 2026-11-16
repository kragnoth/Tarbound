package com.tarbound.compat.jade;

import com.tarbound.Tarbound;
import com.tarbound.block.TarAntennaBlock;
import com.tarbound.block.TarAntennaBlockEntity;
import com.tarbound.network.PoolAccess;
import com.tarbound.network.TarNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/** Tar Antenna: mode, reach, tuning, and whether the radio can reach it. */
public enum AntennaJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = Tarbound.id("tar_antenna");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof TarAntennaBlockEntity antenna)) {
            return;
        }
        BlockPos basePos = TarAntennaBlock.baseOf(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
        if (accessor.getLevel().getBlockEntity(basePos) instanceof TarAntennaBlockEntity base) {
            data.putInt("priority", base.priority());
        }
        GlobalPos link = antenna.link();
        data.putBoolean("tuned", link != null);
        if (link != null) {
            data.putLong("radio", link.pos().asLong());
            boolean here = link.dimension().equals(accessor.getLevel().dimension());
            BlockPos base = TarAntennaBlock.baseOf(accessor.getLevel(), accessor.getPosition(), accessor.getBlockState());
            data.putBoolean("inRange", here && TarNetwork.antennaInRange(accessor.getLevel(), link.pos(), base));
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockState state = accessor.getBlockState();
        PoolAccess mode = state.getValue(TarAntennaBlock.MODE);
        tooltip.add(Component.translatable("jade.tarbound.antenna.mode",
                Component.translatable("mode.tarbound." + mode.getSerializedName()).withStyle(TarAntennaBlock.modeColor(mode))));
        tooltip.add(Component.translatable("jade.tarbound.antenna.reach", TarAntennaBlock.reach(state)).withStyle(ChatFormatting.GRAY));
        CompoundTag data = accessor.getServerData();
        if (!data.contains("tuned")) {
            return;
        }
        int priority = data.getInt("priority");
        if (priority != 0) {
            tooltip.add(Component.translatable("gui.tarbound.antenna.priority", (priority > 0 ? "+" : "") + priority).withStyle(ChatFormatting.GRAY));
        }
        if (!data.getBoolean("tuned")) {
            tooltip.add(Component.translatable("tooltip.tarbound.untuned").withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos radio = BlockPos.of(data.getLong("radio"));
        tooltip.add(Component.translatable("jade.tarbound.tuned_to", radio.getX(), radio.getY(), radio.getZ()).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(data.getBoolean("inRange")
                ? Component.translatable("jade.tarbound.antenna.in_range").withStyle(ChatFormatting.GREEN)
                : Component.translatable("jade.tarbound.antenna.out_of_range").withStyle(ChatFormatting.RED));
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
