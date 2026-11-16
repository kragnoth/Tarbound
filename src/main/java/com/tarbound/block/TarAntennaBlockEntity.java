package com.tarbound.block;

import com.tarbound.network.AntennaRegistry;
import com.tarbound.registry.ModBlockEntities;
import com.tarbound.registry.ModDataComponents;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Holds which Scrying Font an antenna piece is tuned to. Every piece keeps a copy so it drops still
 * tuned; the column's base is the one the radio listens for.
 */
public class TarAntennaBlockEntity extends BlockEntity {
    private static final String TAG_LINK = "link";

    @Nullable
    private GlobalPos link;
    /** The tuned radio's id (see {@link com.tarbound.network.RadioRegistry}); null for links made before 0.19.1. */
    @Nullable
    private java.util.UUID linkId;
    /** Pool priority for inventories touching this column (read from the base piece). Higher fills first, empties last. */
    private int priority;

    public static final int MIN_PRIORITY = -99;
    public static final int MAX_PRIORITY = 99;

    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Math.max(MIN_PRIORITY, Math.min(MAX_PRIORITY, priority));
        setChanged();
        com.tarbound.network.NetworkChanges.mark(level);
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public TarAntennaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TAR_ANTENNA.get(), pos, state);
    }

    @Nullable
    public GlobalPos link() {
        return link;
    }

    @Nullable
    public java.util.UUID linkId() {
        return linkId;
    }

    public void setLink(@Nullable GlobalPos link, @Nullable java.util.UUID linkId) {
        this.link = link;
        this.linkId = link == null ? null : linkId;
        com.tarbound.network.NetworkChanges.mark(level);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            AntennaRegistry.add(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide) {
            AntennaRegistry.remove(level, worldPosition);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (level != null && !level.isClientSide) {
            AntennaRegistry.remove(level, worldPosition);
        }
    }

    // --- item <-> block: the tuning travels with the item

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        GlobalPos fromItem = input.get(ModDataComponents.RADIO_LINK.get());
        if (fromItem != null) {
            link = fromItem;
            linkId = input.get(ModDataComponents.RADIO_ID.get());
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (link != null) {
            components.set(ModDataComponents.RADIO_LINK.get(), link);
            if (linkId != null) {
                components.set(ModDataComponents.RADIO_ID.get(), linkId);
            }
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove(TAG_LINK);
        tag.remove("link_id");
    }

    // --- save / sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (priority != 0) {
            tag.putInt("priority", priority);
        }
        if (link != null) {
            CompoundTag l = new CompoundTag();
            l.putString("dimension", link.dimension().location().toString());
            l.putLong("pos", link.pos().asLong());
            tag.put(TAG_LINK, l);
            if (linkId != null) {
                tag.putUUID("link_id", linkId);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        priority = tag.getInt("priority");
        link = null;
        linkId = tag.hasUUID("link_id") ? tag.getUUID("link_id") : null;
        if (tag.contains(TAG_LINK, Tag.TAG_COMPOUND)) {
            CompoundTag l = tag.getCompound(TAG_LINK);
            ResourceLocation dim = ResourceLocation.tryParse(l.getString("dimension"));
            if (dim != null) {
                link = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(l.getLong("pos")));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
