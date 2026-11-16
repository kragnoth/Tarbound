package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.contract.ContractData;
import com.tarbound.contract.StandingOrder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Tarbound.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ContractData>> CONTRACT =
            COMPONENTS.registerComponentType("contract", b -> b.persistent(ContractData.CODEC).networkSynchronized(ContractData.STREAM_CODEC));

    /** A standing contract (any item in a tag); on the same Spirit Contract item, instead of CONTRACT. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<StandingOrder>> STANDING =
            COMPONENTS.registerComponentType("standing", b -> b.persistent(StandingOrder.CODEC).networkSynchronized(StandingOrder.STREAM_CODEC));

    /** On a Spirit Contract written from a crafting recipe: that recipe's id (run by crafting stations). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.resources.ResourceLocation>> CRAFTING_RECIPE =
            COMPONENTS.registerComponentType("crafting_recipe", b -> b.persistent(net.minecraft.resources.ResourceLocation.CODEC)
                    .networkSynchronized(net.minecraft.resources.ResourceLocation.STREAM_CODEC));

    /** On a crafting Spirit Contract written without substitutes: crafting stations use only the exact items it lists. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> EXACT_ITEMS =
            COMPONENTS.registerComponentType("exact_items", b -> b.persistent(com.mojang.serialization.Codec.BOOL)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.BOOL));

    /** Which Scrying Font a Tar Antenna or Reliquary is tuned to. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> RADIO_LINK =
            COMPONENTS.registerComponentType("radio_link", b -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /**
     * The id of the radio a tuned item belongs to (alongside {@link #RADIO_LINK}), and on a Scrying Font
     * item its own id, so a radio that's broken and placed again keeps everything tuned to it.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.UUID>> RADIO_ID =
            COMPONENTS.registerComponentType("radio_id", b -> b.persistent(net.minecraft.core.UUIDUtil.CODEC)
                    .networkSynchronized(net.minecraft.core.UUIDUtil.STREAM_CODEC));

    /** On a Scrying Font item: the Spirit it held when broken, so moving a radio doesn't waste the candles. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SPIRIT =
            COMPONENTS.registerComponentType("spirit", b -> b.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** On a Scrying Font item: the FE its battery held when broken. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            COMPONENTS.registerComponentType("energy", b -> b.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** On a Reliquary item: how many of its 18 contract slots it had opened (candles fed to it). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CONTRACT_SLOTS =
            COMPONENTS.registerComponentType("contract_slots", b -> b.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** On a Pitch Cask item: the fluid it held when broken. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.neoforged.neoforge.fluids.SimpleFluidContent>> CASK_FLUID =
            COMPONENTS.registerComponentType("cask_fluid", b -> b.persistent(net.neoforged.neoforge.fluids.SimpleFluidContent.CODEC)
                    .networkSynchronized(net.neoforged.neoforge.fluids.SimpleFluidContent.STREAM_CODEC));

    /** On a Shade item: its rank (2 to 5; absent means 1), kept when it's picked up. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SHADE_RANK =
            COMPONENTS.registerComponentType("shade_rank", b -> b.persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT));

    /** On a Steward item: its stock list, kept when it's picked up. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<java.util.List<com.tarbound.entity.StockTarget>>> STEWARD_TARGETS =
            COMPONENTS.registerComponentType("steward_targets", b -> b.persistent(com.tarbound.entity.StockTarget.LIST_CODEC)
                    .networkSynchronized(com.tarbound.entity.StockTarget.LIST_STREAM_CODEC));

    private ModDataComponents() {}
}
