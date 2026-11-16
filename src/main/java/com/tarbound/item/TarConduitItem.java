package com.tarbound.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** Tar-soaked wood: says that it carries tar, since nothing else about it would tell you. */
public class TarConduitItem extends BlockItem {
    public TarConduitItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.tarbound.conduit").withStyle(ChatFormatting.DARK_GRAY));
    }
}
