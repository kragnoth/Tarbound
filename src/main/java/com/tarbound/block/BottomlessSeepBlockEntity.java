package com.tarbound.block;

import com.tarbound.contract.ContractData;
import com.tarbound.network.BoundInventory;
import com.tarbound.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * What a Bottomless Seep offers its radio: the raw inputs of every contract on the radio's Reliquaries (those no
 * contract there makes), each shown as a vast stack it never runs out of. Worked out again at every network scan;
 * not saved.
 */
public class BottomlessSeepBlockEntity extends BlockEntity {
    /** How much of each raw item (and mB of each raw fluid) the seep shows: enough for any order, safe to add up. */
    public static final int ITEMS = 1_000_000;
    public static final int MB = 1_000_000_000;

    private List<ItemStack> items = List.of();
    private List<FluidStack> fluids = List.of();

    public BottomlessSeepBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BOTTOMLESS_SEEP.get(), pos, state);
    }

    /** Offers the raw inputs of the contracts on these machines' Reliquaries. */
    public void offerFor(Level level, List<BoundInventory> machines) {
        List<ContractData> contracts = new ArrayList<>();
        for (BoundInventory m : machines) {
            if (ReliquaryBlock.reliquaryFor(level, m.pos()) instanceof ReliquaryBlockEntity r) {
                for (ContractData c : r.contractSlots()) {
                    if (c != null) {
                        contracts.add(c);
                    }
                }
            }
        }
        Set<Item> made = new HashSet<>();
        Set<Fluid> madeFluids = new HashSet<>();
        for (ContractData c : contracts) {
            c.outputs().forEach(o -> made.add(o.getItem()));
            c.fluidOutputs().forEach(f -> madeFluids.add(f.getFluid()));
        }
        List<ItemStack> raw = new ArrayList<>();
        Set<Item> offered = new HashSet<>();
        List<FluidStack> rawFluids = new ArrayList<>();
        Set<Fluid> offeredFluids = new HashSet<>();
        for (ContractData c : contracts) {
            for (List<ItemStack> alts : c.inputs()) {
                if (alts.isEmpty() || alts.stream().anyMatch(a -> made.contains(a.getItem()))) {
                    continue; // something here makes it: that's crafted, not handed out
                }
                ItemStack first = alts.get(0);
                if (offered.add(first.getItem())) {
                    raw.add(first.copyWithCount(1));
                }
            }
            for (FluidStack f : c.fluidInputs()) {
                if (!madeFluids.contains(f.getFluid()) && offeredFluids.add(f.getFluid())) {
                    rawFluids.add(f.copyWithAmount(1));
                }
            }
        }
        items = List.copyOf(raw);
        fluids = List.copyOf(rawFluids);
    }

    public IItemHandler items() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return Math.max(1, items.size());
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot < items.size() ? items.get(slot).copyWithCount(ITEMS) : ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return slot < items.size() && amount > 0 ? items.get(slot).copyWithCount(amount) : ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return ITEMS;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return false;
            }
        };
    }

    public IFluidHandler fluids() {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return Math.max(1, fluids.size());
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return tank < fluids.size() ? fluids.get(tank).copyWithAmount(MB) : FluidStack.EMPTY;
            }

            @Override
            public int getTankCapacity(int tank) {
                return MB;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return false;
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return 0;
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                for (FluidStack f : fluids) {
                    if (FluidStack.isSameFluidSameComponents(f, resource)) {
                        return resource.copy();
                    }
                }
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return fluids.isEmpty() || maxDrain <= 0 ? FluidStack.EMPTY : fluids.get(0).copyWithAmount(maxDrain);
            }
        };
    }
}
