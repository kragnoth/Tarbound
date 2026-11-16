package com.tarbound.item;

import com.tarbound.block.PitchCaskBlockEntity;
import com.tarbound.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

/** A Pitch Cask as an item: carries its fluid, and other mods' tanks and machines can fill and empty it. */
public class PitchCaskItem extends BlockItem {
    public PitchCaskItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    public static FluidStack contents(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.CASK_FLUID.get(), SimpleFluidContent.EMPTY).copy();
    }

    /** "3.5" for 3500 mB. */
    private static String buckets(int mb) {
        return mb % 1000 == 0 ? Integer.toString(mb / 1000) : String.format(java.util.Locale.ROOT, "%.1f", mb / 1000.0);
    }

    /** Fluid handler for one cask item. A stack of several won't fill or drain (that would copy the fluid to all). */
    public static class Handler extends FluidHandlerItemStack {
        public Handler(ItemStack stack) {
            super(ModDataComponents.CASK_FLUID, stack, PitchCaskBlockEntity.CAPACITY);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return container.getCount() == 1 ? super.fill(resource, action) : 0;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return container.getCount() == 1 ? super.drain(resource, action) : FluidStack.EMPTY;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return container.getCount() == 1 ? super.drain(maxDrain, action) : FluidStack.EMPTY;
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !contents(stack).isEmpty();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * contents(stack).getAmount() / PitchCaskBlockEntity.CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0x8A6A3A;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        FluidStack f = contents(stack);
        tooltip.add(f.isEmpty() ? Component.translatable("tooltip.tarbound.pitch_cask.empty").withStyle(ChatFormatting.GRAY)
                : Component.translatable("tooltip.tarbound.pitch_cask.holds", buckets(f.getAmount()), buckets(PitchCaskBlockEntity.CAPACITY),
                        f.getHoverName()).withStyle(ChatFormatting.GRAY));
    }
}
