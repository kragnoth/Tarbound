package com.tarbound.net;

import com.tarbound.Tarbound;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Client → server (from JEI/EMI): load one operation of a recipe into a bound machine.
 *
 * @param inputs one entry per recipe input; each lists acceptable stacks, with the count one operation needs
 * @param fluids fluids one operation needs, pumped from the pool's tanks
 */
public record MachineFillPayload(int containerId, BlockPos machine, List<List<ItemStack>> inputs, List<FluidStack> fluids)
        implements CustomPacketPayload {
    public static final int MAX_INPUTS = 16;
    public static final int MAX_FLUIDS = 4;

    public static final Type<MachineFillPayload> TYPE = new Type<>(Tarbound.id("machine_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineFillPayload> STREAM_CODEC =
            StreamCodec.ofMember(MachineFillPayload::write, MachineFillPayload::read);

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeBlockPos(machine);
        List<List<ItemStack>> clean = inputs.stream()
                .map(alts -> alts.stream().filter(s -> !s.isEmpty()).limit(FillGridPayload.MAX_ALTERNATIVES).toList())
                .filter(alts -> !alts.isEmpty())
                .limit(MAX_INPUTS)
                .toList();
        buf.writeVarInt(clean.size());
        for (List<ItemStack> alts : clean) {
            buf.writeVarInt(alts.size());
            for (ItemStack s : alts) {
                ItemStack.STREAM_CODEC.encode(buf, s);
            }
        }
        List<FluidStack> f = fluids.stream().filter(x -> !x.isEmpty()).limit(MAX_FLUIDS).toList();
        buf.writeVarInt(f.size());
        for (FluidStack x : f) {
            FluidStack.STREAM_CODEC.encode(buf, x);
        }
    }

    private static MachineFillPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        BlockPos machine = buf.readBlockPos();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_INPUTS) {
            throw new IllegalArgumentException("Too many inputs in machine_fill: " + n);
        }
        List<List<ItemStack>> inputs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int a = buf.readVarInt();
            if (a < 0 || a > FillGridPayload.MAX_ALTERNATIVES) {
                throw new IllegalArgumentException("Too many alternatives in machine_fill: " + a);
            }
            List<ItemStack> alts = new ArrayList<>(a);
            for (int j = 0; j < a; j++) {
                alts.add(ItemStack.STREAM_CODEC.decode(buf));
            }
            inputs.add(alts);
        }
        int m = buf.readVarInt();
        if (m < 0 || m > MAX_FLUIDS) {
            throw new IllegalArgumentException("Too many fluids in machine_fill: " + m);
        }
        List<FluidStack> fluids = new ArrayList<>(m);
        for (int i = 0; i < m; i++) {
            fluids.add(FluidStack.STREAM_CODEC.decode(buf));
        }
        return new MachineFillPayload(containerId, machine, inputs, fluids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
