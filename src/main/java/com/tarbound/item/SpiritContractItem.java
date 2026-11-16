package com.tarbound.item;

import com.tarbound.contract.ContractData;
import com.tarbound.contract.StandingOrder;
import com.tarbound.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** A written Spirit Contract: the terms of one machine operation, bound to whichever bound spirit holds it. */
public class SpiritContractItem extends Item {
    public SpiritContractItem(Properties properties) {
        super(properties);
    }

    @Override
    public Component getName(ItemStack stack) {
        StandingOrder order = stack.get(ModDataComponents.STANDING.get());
        if (order != null) {
            return Component.translatable("item.tarbound.spirit_contract.standing_of", StandingOrder.tagName(order.tag()));
        }
        ContractData data = stack.get(ModDataComponents.CONTRACT.get());
        if (data != null && data.isValid()) {
            Component what = data.tracksFluid() ? data.trackedFluid().getHoverName() : data.primaryOutput().getHoverName();
            return Component.translatable("item.tarbound.spirit_contract.of", what);
        }
        return super.getName(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        StandingOrder order = stack.get(ModDataComponents.STANDING.get());
        if (order != null) {
            lines.add(Component.translatable("item.tarbound.spirit_contract.standing_takes", order.count(), StandingOrder.tagName(order.tag()))
                    .withStyle(ChatFormatting.DARK_AQUA));
            lines.add(Component.literal("#" + order.tag().location()).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("item.tarbound.spirit_contract.standing_hint").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        ContractData data = stack.get(ModDataComponents.CONTRACT.get());
        if (data == null || !data.isValid()) {
            lines.add(Component.translatable("item.tarbound.spirit_contract.blank").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (stack.has(ModDataComponents.CRAFTING_RECIPE.get())) {
            lines.add(Component.translatable("item.tarbound.spirit_contract.crafting").withStyle(ChatFormatting.GOLD));
            if (stack.getOrDefault(ModDataComponents.EXACT_ITEMS.get(), false)) {
                lines.add(Component.translatable("item.tarbound.spirit_contract.exact").withStyle(ChatFormatting.GOLD));
            }
        }
        lines.add(Component.translatable("item.tarbound.spirit_contract.takes").withStyle(ChatFormatting.GRAY));
        for (int i = 0; i < data.inputs().size(); i++) {
            List<ItemStack> alts = data.inputs().get(i);
            ItemStack first = alts.get(0);
            net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag = data.tagFor(i);
            Component name = tag != null
                    ? Component.translatable("item.tarbound.spirit_contract.any_tag", StandingOrder.tagName(tag))
                    : alts.size() > 1
                    ? Component.translatable("item.tarbound.spirit_contract.any_of", first.getHoverName(), alts.size() - 1)
                    : first.getHoverName();
            if (ContractData.staysInMachine(alts)) {
                lines.add(Component.literal("  ").append(name).append(Component.translatable("item.tarbound.spirit_contract.stays"))
                        .withStyle(ChatFormatting.DARK_AQUA));
                continue;
            }
            if (data.returnedInput(i)) {
                lines.add(Component.literal("  " + first.getCount() + "× ").append(name).append(Component.translatable("item.tarbound.spirit_contract.returned"))
                        .withStyle(ChatFormatting.DARK_AQUA));
                continue;
            }
            lines.add(Component.literal("  " + first.getCount() + "× ").append(name).withStyle(ChatFormatting.DARK_AQUA));
        }
        for (net.neoforged.neoforge.fluids.FluidStack f : data.fluidInputs()) {
            lines.add(Component.literal("  " + f.getAmount() + " mB ").append(f.getHoverName()).withStyle(ChatFormatting.DARK_AQUA));
        }
        lines.add(Component.translatable("item.tarbound.spirit_contract.gives").withStyle(ChatFormatting.GRAY));
        for (ItemStack out : data.outputs()) {
            lines.add(Component.literal("  " + out.getCount() + "× ").append(out.getHoverName()).withStyle(ChatFormatting.GOLD));
        }
        for (net.neoforged.neoforge.fluids.FluidStack f : data.fluidOutputs()) {
            lines.add(Component.literal("  " + f.getAmount() + " mB ").append(f.getHoverName()).withStyle(ChatFormatting.GOLD));
        }
        if (data.tracksFluid()) {
            lines.add(Component.translatable("item.tarbound.spirit_contract.fluid_only").withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable("item.tarbound.spirit_contract.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
