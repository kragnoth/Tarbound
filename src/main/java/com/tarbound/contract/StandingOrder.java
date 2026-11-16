package com.tarbound.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A standing contract: "anything in this item tag, {@code count} at a time". It names no output. A
 * Reliquary holding one keeps feeding every matching item in the pool into the machine beneath it,
 * and whatever the machine makes comes back the usual way (drained outputs, or belts/funnels into
 * storage touching the tar). Written at the Scribing Lectern by generalizing a one-input contract.
 */
public record StandingOrder(TagKey<Item> tag, int count) {
    public static final int MAX_COUNT = 64;

    public static final Codec<StandingOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TagKey.codec(Registries.ITEM).fieldOf("tag").forGetter(StandingOrder::tag),
            Codec.INT.fieldOf("count").forGetter(StandingOrder::count)
    ).apply(instance, StandingOrder::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StandingOrder> STREAM_CODEC =
            StreamCodec.ofMember(StandingOrder::write, StandingOrder::read);

    public StandingOrder {
        count = Math.max(1, Math.min(MAX_COUNT, count));
    }

    public static StandingOrder of(ResourceLocation tag, int count) {
        return new StandingOrder(TagKey.create(Registries.ITEM, tag), count);
    }

    /** The tag's readable name if a mod provides one (e.g. "Crushed Raw Materials"), else "#namespace:path". */
    public static Component tagName(TagKey<Item> tag) {
        return Component.translatableWithFallback(Tags.getTagTranslationKey(tag), "#" + tag.location());
    }

    /** {@link #tagName(TagKey)} for a tag id sent as text. */
    public static Component tagName(String tagId) {
        ResourceLocation id = ResourceLocation.tryParse(tagId);
        return id == null ? Component.literal("#" + tagId) : tagName(TagKey.create(Registries.ITEM, id));
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && stack.is(tag);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeResourceLocation(tag.location());
        buf.writeVarInt(count);
    }

    private static StandingOrder read(RegistryFriendlyByteBuf buf) {
        return of(buf.readResourceLocation(), buf.readVarInt());
    }
}
