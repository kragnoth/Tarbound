package com.tarbound.block;

import com.tarbound.network.RadioRegistry;
import com.tarbound.registry.ModBlockEntities;
import com.tarbound.registry.ModDataComponents;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The Ossuary only remembers which radio it's tuned to. */
public class OssuaryBlockEntity extends BlockEntity {
    @Nullable
    private GlobalPos link;
    @Nullable
    private java.util.UUID linkId;

    public OssuaryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OSSUARY.get(), pos, state);
    }

    @Nullable
    public GlobalPos link() {
        return link;
    }

    /** The loaded radio this Ossuary is tuned to, or null. */
    @Nullable
    public ScryingFontBlockEntity radio() {
        if (level == null || link == null || !link.dimension().equals(level.dimension())) {
            return null;
        }
        BlockPos at = linkId != null ? RadioRegistry.find(level, linkId) : null;
        if (at == null) {
            at = link.pos();
        }
        if (!level.isLoaded(at) || !(level.getBlockEntity(at) instanceof ScryingFontBlockEntity font)) {
            return null;
        }
        return linkId == null || linkId.equals(font.radioId()) ? font : null;
    }

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
        tag.remove("link");
        tag.remove("link_id");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (link != null) {
            CompoundTag l = new CompoundTag();
            l.putString("dimension", link.dimension().location().toString());
            l.putLong("pos", link.pos().asLong());
            tag.put("link", l);
            if (linkId != null) {
                tag.putUUID("link_id", linkId);
            }
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        link = null;
        linkId = tag.hasUUID("link_id") ? tag.getUUID("link_id") : null;
        if (tag.contains("link", Tag.TAG_COMPOUND)) {
            CompoundTag l = tag.getCompound("link");
            ResourceLocation dim = ResourceLocation.tryParse(l.getString("dimension"));
            if (dim != null) {
                link = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dim), BlockPos.of(l.getLong("pos")));
            }
        }
    }
}
