package com.tarbound.block;

import com.tarbound.registry.ModBlockEntities;
import com.tarbound.registry.ModDataComponents;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A Pitch Cask's 8 buckets. It keeps them when broken (they go into the dropped item) and gets them back when
 * placed. The fluid is synced to clients so a cask of tar can weep a little.
 */
public class PitchCaskBlockEntity extends BlockEntity {
    public static final int CAPACITY = 8000;

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            if (level != null && !level.isClientSide) {
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    };

    public PitchCaskBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PITCH_CASK.get(), pos, state);
    }

    public FluidTank tank() {
        return tank;
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        SimpleFluidContent content = input.get(ModDataComponents.CASK_FLUID.get());
        if (content != null) {
            tank.setFluid(content.copy());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!tank.isEmpty()) {
            components.set(ModDataComponents.CASK_FLUID.get(), SimpleFluidContent.copyOf(tank.getFluid()));
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Fluid");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!tank.isEmpty()) {
            tag.put("Fluid", tank.getFluid().save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.setFluid(tag.contains("Fluid") ? FluidStack.parseOptional(registries, tag.getCompound("Fluid")) : FluidStack.EMPTY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
