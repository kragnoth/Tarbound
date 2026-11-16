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

/** Shade item: shift-right-click a Scrying Font to tune it, then set it down anywhere loaded. */
public class ShadeItem extends BlockItem {
    public ShadeItem(Block block, Properties properties) {
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
        int rank = Math.max(1, stack.getOrDefault(com.tarbound.registry.ModDataComponents.SHADE_RANK.get(), 1));
        tooltip.add(Component.translatable("tooltip.tarbound.shade.rank", rank, com.tarbound.block.ShadeBlockEntity.capacityOf(rank))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.tarbound.shade.what").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.tarbound.shade.one").withStyle(ChatFormatting.DARK_GRAY));
        if (rank < com.tarbound.block.ShadeBlockEntity.MAX_RANK) {
            tooltip.add(Component.translatable("tooltip.tarbound.shade.upgrade").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
