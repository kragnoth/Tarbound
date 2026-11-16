package com.tarbound.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Reliquary item: shift-right-click a Scrying Font to tune it, then set it on a machine anywhere in that dimension. */
public class ReliquaryItem extends BlockItem {
    public ReliquaryItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult tuned = RadioTuning.tryTune(context);
        return tuned != null ? tuned : super.useOn(context);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RadioTuning.appendTuning(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.tarbound.reliquary").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.tarbound.reliquary.anti_magnet").withStyle(ChatFormatting.DARK_GRAY));
    }
}
