package com.tarbound.compat.jade;

import com.tarbound.Tarbound;
import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.network.AntiMagnet;
import java.util.Objects;
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

/** Reliquary: its name, the machine it binds, its tuning, contracts and anti-magnet ward. */
public enum ReliquaryJadeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    private static final ResourceLocation UID = Tarbound.id("reliquary");

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof ReliquaryBlockEntity reliquary)) {
            return;
        }
        GlobalPos link = reliquary.link();
        data.putBoolean("tuned", link != null);
        if (link != null) {
            data.putLong("radio", link.pos().asLong());
            boolean here = link.dimension().equals(accessor.getLevel().dimension());
            data.putBoolean("otherDim", !here);
            data.putBoolean("radioFound", here && accessor.getLevel().isLoaded(link.pos())
                    && accessor.getLevel().getBlockEntity(link.pos()) instanceof ScryingFontBlockEntity);
        }
        data.putInt("contracts", (int) reliquary.contractSlots().stream().filter(Objects::nonNull).count());
        data.putInt("standing", reliquary.standingSlots().size());
        data.putInt("paused", (int) reliquary.standingSlots().stream().filter(ReliquaryBlockEntity.StandingSlot::paused).count());
        data.putBoolean("antiMagnet", reliquary.antiMagnet());
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (accessor.getBlockEntity() instanceof ReliquaryBlockEntity reliquary && reliquary.customName() != null) {
            tooltip.add(reliquary.customName().copy().withStyle(ChatFormatting.ITALIC));
        }
        com.tarbound.block.ReliquaryBlock.Attach attach = accessor.getBlockState().getValue(com.tarbound.block.ReliquaryBlock.ATTACH);
        if (attach.dir == null) {
            tooltip.add(Component.translatable("gui.tarbound.reliquary.all_sides").withStyle(ChatFormatting.GRAY));
        } else {
            BlockState below = accessor.getLevel().getBlockState(accessor.getPosition().relative(attach.dir));
            if (!below.isAir()) {
                tooltip.add(Component.translatable("gui.tarbound.reliquary.bound_to", below.getBlock().getName()).withStyle(ChatFormatting.GRAY));
            }
        }
        CompoundTag data = accessor.getServerData();
        if (!data.contains("tuned")) {
            return;
        }
        if (!data.getBoolean("tuned")) {
            tooltip.add(Component.translatable("gui.tarbound.reliquary.untuned").withStyle(ChatFormatting.RED));
        } else {
            BlockPos radio = BlockPos.of(data.getLong("radio"));
            tooltip.add(Component.translatable("jade.tarbound.tuned_to", radio.getX(), radio.getY(), radio.getZ())
                    .withStyle(ChatFormatting.DARK_AQUA));
            if (data.getBoolean("otherDim")) {
                tooltip.add(Component.translatable("jade.tarbound.radio_other_dimension").withStyle(ChatFormatting.RED));
            } else if (!data.getBoolean("radioFound")) {
                tooltip.add(Component.translatable("jade.tarbound.radio_missing").withStyle(ChatFormatting.RED));
            }
        }
        int contracts = data.getInt("contracts");
        int standing = data.getInt("standing");
        if (contracts > 0 || standing > 0) {
            tooltip.add(Component.translatable("jade.tarbound.reliquary.contracts", contracts, standing, data.getInt("paused"))
                    .withStyle(ChatFormatting.GRAY));
        }
        if (data.getBoolean("antiMagnet")) {
            tooltip.add(Component.translatable("jade.tarbound.reliquary.anti_magnet", (int) AntiMagnet.radius()).withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
