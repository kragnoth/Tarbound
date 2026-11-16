package com.tarbound.contract;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tarbound.network.ItemKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What a Spirit Contract says: for one operation, these inputs go in and these outputs come out.
 * Like an AE2 processing pattern, it names no machine and no recipe type; the machine is whichever one
 * sits under the Reliquary holding the contract.
 *
 * @param inputs  one entry per input; each lists the acceptable stacks (count = amount per operation)
 * @param outputs what one operation yields; the first is the primary output, used to request and track jobs
 * @param tags    one per input: an item tag id standing for that input ("" for none). Set when an input
 *                had more alternatives than a contract stores (any log, any planks): the listed stacks
 *                are a sample, and every pool item in the tag counts. See {@link #inputsFor}.
 * @param fluidInputs  fluids one operation pumps into the machine (amounts in mB)
 * @param fluidOutputs fluids one operation makes; drained from the machine back to the pool's tanks
 */
public record ContractData(List<List<ItemStack>> inputs, List<ItemStack> outputs, List<String> tags,
                           List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs, java.util.Optional<List<Integer>> returned) {
    public static final int MAX_INPUTS = 16;
    public static final int MAX_OUTPUTS = 9;
    public static final int MAX_ALTERNATIVES = 16;
    public static final int MAX_FLUIDS = 4;

    public static final Codec<ContractData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.listOf().listOf().fieldOf("inputs").forGetter(ContractData::inputs),
            ItemStack.CODEC.listOf().fieldOf("outputs").forGetter(ContractData::outputs),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(ContractData::tags),
            FluidStack.CODEC.listOf().optionalFieldOf("fluid_inputs", List.of()).forGetter(ContractData::fluidInputs),
            FluidStack.CODEC.listOf().optionalFieldOf("fluid_outputs", List.of()).forGetter(ContractData::fluidOutputs),
            Codec.INT.listOf().optionalFieldOf("returned").forGetter(ContractData::returned)
    ).apply(instance, ContractData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ContractData> STREAM_CODEC =
            StreamCodec.ofMember(ContractData::write, ContractData::read);

    public ContractData {
        inputs = inputs.stream().map(List::copyOf).toList();
        outputs = List.copyOf(outputs);
        List<String> t = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            t.add(tags != null && i < tags.size() && tags.get(i) != null ? tags.get(i) : "");
        }
        tags = List.copyOf(t);
        fluidInputs = fluidInputs == null ? List.of() : fluidInputs.stream().map(FluidStack::copy).toList();
        fluidOutputs = fluidOutputs == null ? List.of() : fluidOutputs.stream().map(FluidStack::copy).toList();
        returned = returned == null ? java.util.Optional.empty() : returned.map(List::copyOf);
    }

    public ContractData(List<List<ItemStack>> inputs, List<ItemStack> outputs, List<String> tags,
                        List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs) {
        this(inputs, outputs, tags, fluidInputs, fluidOutputs, java.util.Optional.empty());
    }

    /**
     * The same contract, knowing exactly which inputs every craft hands back (indices into {@link #inputs}), as
     * asked of the recipe itself when the contract is written ({@code returned} in the save, since 0.50.3).
     */
    public ContractData withReturned(List<Integer> indices) {
        return new ContractData(inputs, outputs, tags, fluidInputs, fluidOutputs, java.util.Optional.of(indices));
    }

    /**
     * Whether input {@code i} comes back after every craft: as the recipe said when the contract was written, or for
     * contracts written before that was recorded (and machine contracts), whether its items are their own crafting
     * remainder ({@link #returnedByCraft}).
     */
    public boolean returnedInput(int i) {
        if (i < 0 || i >= inputs.size()) {
            return false;
        }
        return returned.map(l -> l.contains(i)).orElseGet(() -> returnedByCraft(inputs.get(i)));
    }

    public ContractData(List<List<ItemStack>> inputs, List<ItemStack> outputs, List<String> tags) {
        this(inputs, outputs, tags, List.of(), List.of());
    }

    public ContractData(List<List<ItemStack>> inputs, List<ItemStack> outputs) {
        this(inputs, outputs, List.of());
    }

    /** An input the machine keeps (every listed item in {@code tarbound:stays_in_machine}): see {@link com.tarbound.registry.ModTags#STAYS_IN_MACHINE}. */
    public static boolean staysInMachine(List<ItemStack> alts) {
        if (alts.isEmpty()) {
            return false;
        }
        for (ItemStack s : alts) {
            if (!s.is(com.tarbound.registry.ModTags.STAYS_IN_MACHINE)) {
                return false;
            }
        }
        return true;
    }

    /**
     * An input every craft hands straight back: each listed item is its own crafting remainder (a Blood Orb in a
     * NeoVitae rune recipe, a knife that comes back a little worn). One on hand does for any number of operations,
     * so plans and counts need one, not one per operation.
     */
    public static boolean returnedByCraft(List<ItemStack> alts) {
        if (alts.isEmpty()) {
            return false;
        }
        for (ItemStack s : alts) {
            if (s.isEmpty() || !s.hasCraftingRemainingItem() || !ItemStack.isSameItem(s.getCraftingRemainingItem(), s)) {
                return false;
            }
        }
        return true;
    }

    /** Uses or makes any fluid. */
    public boolean hasFluids() {
        return !fluidInputs.isEmpty() || !fluidOutputs.isEmpty();
    }

    /** Has something to order and track: an item output, or (for a fluid-only contract) a fluid one. */
    public boolean orderable() {
        return !outputs.isEmpty() || !fluidOutputs.isEmpty();
    }

    /** Makes only fluid: its jobs track its first fluid output, in mB, instead of an item. */
    public boolean tracksFluid() {
        return outputs.isEmpty() && !fluidOutputs.isEmpty();
    }

    /** The fluid a fluid-only contract's jobs track (one operation's worth), else empty. */
    public FluidStack trackedFluid() {
        return tracksFluid() ? fluidOutputs.get(0) : FluidStack.EMPTY;
    }

    /** How much of what its jobs track one operation makes: items, or mB for a fluid-only contract. */
    public int perOp() {
        return tracksFluid() ? Math.max(1, fluidOutputs.get(0).getAmount()) : Math.max(1, primaryOutput().getCount());
    }

    /** The tag standing for input {@code i}, or null. */
    @Nullable
    public TagKey<Item> tagFor(int i) {
        String id = i < tags.size() ? tags.get(i) : "";
        ResourceLocation loc = id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return loc == null ? null : TagKey.create(Registries.ITEM, loc);
    }

    /**
     * The inputs with each tagged input widened to every pool item in its tag (at the input's count),
     * the listed sample first, and plain items widened to used copies in stock (a damaged flint and
     * steel; see {@link com.tarbound.network.LooseMatch}). What jobs check and load against.
     */
    public List<List<ItemStack>> inputsFor(Map<ItemKey, Long> stock) {
        return com.tarbound.network.LooseMatch.widen(tagInputsFor(stock), stock.keySet());
    }

    private List<List<ItemStack>> tagInputsFor(Map<ItemKey, Long> stock) {
        boolean anyTag = false;
        for (String t : tags) {
            anyTag |= !t.isEmpty();
        }
        if (!anyTag) {
            return inputs;
        }
        List<List<ItemStack>> out = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            TagKey<Item> tag = tagFor(i);
            List<ItemStack> listed = inputs.get(i);
            if (tag == null || listed.isEmpty()) {
                out.add(listed);
                continue;
            }
            int count = Math.max(1, listed.get(0).getCount());
            List<ItemStack> wide = new ArrayList<>(listed);
            Set<ItemKey> seen = new HashSet<>();
            for (ItemStack s : listed) {
                seen.add(ItemKey.of(s));
            }
            for (ItemKey k : stock.keySet()) {
                if (k.proto().is(tag) && seen.add(k)) {
                    wide.add(k.proto().copyWithCount(count));
                }
            }
            out.add(wide);
        }
        return out;
    }

    /**
     * The item tag holding exactly these items (no more, no fewer), if there is one: how a long
     * ingredient list ("any log") is remembered once it's cut down to {@link #MAX_ALTERNATIVES}.
     */
    @Nullable
    public static TagKey<Item> exactTag(List<ItemStack> alts) {
        Set<Item> items = new HashSet<>();
        for (ItemStack s : alts) {
            if (!s.isEmpty()) {
                items.add(s.getItem());
            }
        }
        if (items.size() < 2) {
            return null;
        }
        ItemStack first = alts.stream().filter(s -> !s.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        return first.getTags()
                .filter(t -> BuiltInRegistries.ITEM.getTag(t).map(HolderSet::size).orElse(0) == items.size())
                .filter(t -> items.stream().allMatch(it -> it.builtInRegistryHolder().is(t)))
                .findFirst()
                .orElse(null);
    }

    /**
     * Server check on a contract a client wrote: keep a tag only if it exists and every listed
     * alternative is really in it.
     */
    public ContractData checkedTags() {
        List<String> clean = new ArrayList<>(inputs.size());
        boolean changed = false;
        for (int i = 0; i < inputs.size(); i++) {
            TagKey<Item> tag = tagFor(i);
            boolean ok = tag != null && BuiltInRegistries.ITEM.getTag(tag).isPresent()
                    && inputs.get(i).stream().allMatch(s -> s.is(tag));
            clean.add(ok ? tags.get(i) : "");
            changed |= !clean.get(i).equals(tags.get(i));
        }
        return changed ? new ContractData(inputs, outputs, clean, fluidInputs, fluidOutputs, returned) : this;
    }

    /** Trims empty entries and caps sizes, so a contract written from any recipe view stays small. */
    public static ContractData of(List<List<ItemStack>> inputs, List<ItemStack> outputs) {
        return of(inputs, outputs, List.of(), List.of());
    }

    /** As {@link #of(List, List)}, with fluids in and out (amounts in mB). */
    public static ContractData of(List<List<ItemStack>> inputs, List<ItemStack> outputs, List<FluidStack> fluidInputs, List<FluidStack> fluidOutputs) {
        List<List<ItemStack>> in = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        for (List<ItemStack> alts : inputs) {
            List<ItemStack> nonEmpty = alts.stream().filter(s -> !s.isEmpty()).toList();
            List<ItemStack> clean = nonEmpty.stream().limit(MAX_ALTERNATIVES).map(ItemStack::copy).toList();
            if (!clean.isEmpty() && in.size() < MAX_INPUTS) {
                in.add(clean);
                // Too many to keep: remember the tag they make up, so the rest still count.
                TagKey<Item> tag = nonEmpty.size() > MAX_ALTERNATIVES ? exactTag(nonEmpty) : null;
                tags.add(tag == null ? "" : tag.location().toString());
            }
        }
        List<ItemStack> out = outputs.stream().filter(s -> !s.isEmpty()).limit(MAX_OUTPUTS).map(ItemStack::copy).toList();
        return new ContractData(in, out, tags, cleanFluids(fluidInputs), cleanFluids(fluidOutputs));
    }

    private static List<FluidStack> cleanFluids(List<FluidStack> fluids) {
        return fluids.stream().filter(f -> !f.isEmpty()).limit(MAX_FLUIDS).map(FluidStack::copy).toList();
    }

    public boolean isValid() {
        return (!inputs.isEmpty() || !fluidInputs.isEmpty()) && (!outputs.isEmpty() || !fluidOutputs.isEmpty());
    }

    public ItemStack primaryOutput() {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
    }

    /** Same inputs and outputs (item, components and counts). */
    public boolean sameAs(ContractData o) {
        if (o == null || inputs.size() != o.inputs.size() || outputs.size() != o.outputs.size() || !tags.equals(o.tags)
                || !sameFluids(fluidInputs, o.fluidInputs) || !sameFluids(fluidOutputs, o.fluidOutputs)) {
            return false;
        }
        for (int i = 0; i < outputs.size(); i++) {
            if (!ItemStack.matches(outputs.get(i), o.outputs.get(i))) {
                return false;
            }
        }
        for (int i = 0; i < inputs.size(); i++) {
            List<ItemStack> a = inputs.get(i);
            List<ItemStack> b = o.inputs.get(i);
            if (a.size() != b.size()) {
                return false;
            }
            for (int j = 0; j < a.size(); j++) {
                if (!ItemStack.matches(a.get(j), b.get(j))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean sameFluids(List<FluidStack> a, List<FluidStack> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!FluidStack.matches(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof ContractData other && sameAs(other));
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (ItemStack s : outputs) {
            h = 31 * h + ItemStack.hashItemAndComponents(s) + s.getCount();
        }
        for (List<ItemStack> alts : inputs) {
            for (ItemStack s : alts) {
                h = 31 * h + ItemStack.hashItemAndComponents(s) + s.getCount();
            }
        }
        for (FluidStack f : fluidInputs) {
            h = 31 * h + FluidStack.hashFluidAndComponents(f) + f.getAmount();
        }
        for (FluidStack f : fluidOutputs) {
            h = 37 * h + FluidStack.hashFluidAndComponents(f) + f.getAmount();
        }
        return h;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(inputs.size());
        for (List<ItemStack> alts : inputs) {
            buf.writeVarInt(alts.size());
            for (ItemStack s : alts) {
                ItemStack.STREAM_CODEC.encode(buf, s);
            }
        }
        buf.writeVarInt(outputs.size());
        for (ItemStack s : outputs) {
            ItemStack.STREAM_CODEC.encode(buf, s);
        }
        for (String t : tags) {
            buf.writeUtf(t, 256);
        }
        writeFluids(buf, fluidInputs);
        writeFluids(buf, fluidOutputs);
        buf.writeBoolean(returned.isPresent());
        if (returned.isPresent()) {
            buf.writeVarInt(returned.get().size());
            for (int i : returned.get()) {
                buf.writeVarInt(i);
            }
        }
    }

    private static void writeFluids(RegistryFriendlyByteBuf buf, List<FluidStack> fluids) {
        buf.writeVarInt(fluids.size());
        for (FluidStack f : fluids) {
            FluidStack.STREAM_CODEC.encode(buf, f);
        }
    }

    private static List<FluidStack> readFluids(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_FLUIDS) {
            throw new IllegalArgumentException("Bad contract fluid count " + n);
        }
        List<FluidStack> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(FluidStack.STREAM_CODEC.decode(buf));
        }
        return out;
    }

    private static ContractData read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_INPUTS) {
            throw new IllegalArgumentException("Bad contract input count " + n);
        }
        List<List<ItemStack>> inputs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int a = buf.readVarInt();
            if (a < 0 || a > MAX_ALTERNATIVES) {
                throw new IllegalArgumentException("Bad contract alternative count " + a);
            }
            List<ItemStack> alts = new ArrayList<>(a);
            for (int j = 0; j < a; j++) {
                alts.add(ItemStack.STREAM_CODEC.decode(buf));
            }
            inputs.add(alts);
        }
        int m = buf.readVarInt();
        if (m < 0 || m > MAX_OUTPUTS) {
            throw new IllegalArgumentException("Bad contract output count " + m);
        }
        List<ItemStack> outputs = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            outputs.add(ItemStack.STREAM_CODEC.decode(buf));
        }
        List<String> tags = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            tags.add(buf.readUtf(256));
        }
        List<FluidStack> fluidInputs = readFluids(buf);
        List<FluidStack> fluidOutputs = readFluids(buf);
        java.util.Optional<List<Integer>> returned = java.util.Optional.empty();
        if (buf.readBoolean()) {
            int r = buf.readVarInt();
            if (r < 0 || r > MAX_INPUTS) {
                throw new IllegalArgumentException("Bad contract returned count " + r);
            }
            List<Integer> idx = new ArrayList<>(r);
            for (int i = 0; i < r; i++) {
                idx.add(buf.readVarInt());
            }
            returned = java.util.Optional.of(idx);
        }
        return new ContractData(inputs, outputs, tags, fluidInputs, fluidOutputs, returned);
    }
}
