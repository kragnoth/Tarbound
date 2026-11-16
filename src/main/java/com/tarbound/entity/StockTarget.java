package com.tarbound.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/** One line of a Steward's list: keep {@code keep} of {@code item} (one of it, as the key) in storage. */
public record StockTarget(ItemStack item, int keep) {
    /** Most a Steward keeps track of. */
    public static final int MAX = 18;
    public static final int MAX_KEEP = 1_000_000;

    public static final Codec<StockTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStack.CODEC.fieldOf("item").forGetter(StockTarget::item),
            Codec.intRange(1, MAX_KEEP).fieldOf("keep").forGetter(StockTarget::keep)
    ).apply(i, StockTarget::new));

    public static final Codec<List<StockTarget>> LIST_CODEC = CODEC.listOf();

    public static final StreamCodec<RegistryFriendlyByteBuf, StockTarget> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, StockTarget::item,
            ByteBufCodecs.VAR_INT, StockTarget::keep,
            StockTarget::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, List<StockTarget>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list(MAX));

    public StockTarget {
        item = item.copyWithCount(1);
        keep = Math.max(1, Math.min(MAX_KEEP, keep));
    }
}
