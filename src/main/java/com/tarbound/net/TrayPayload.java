package com.tarbound.net;

import com.tarbound.Tarbound;
import com.tarbound.contract.ContractData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server → client: the bound machines bound to the font, each with every slot and a guess at what
 * each slot is for.
 *
 * @param outOfRange tuned antennas the radio can't reach, directly or through other antennas
 * @param unbound machines touching the tar without a reliquary (not bound)
 * @param freeShades the most steps a free Shade tuned to this radio can watch (0: none free)
 * @param planDepth  the server's plan limits, so the order panel's preview plans the way the server will
 * @param planNodes  (see {@code maxPlanDepth}, {@code maxPlanNodes})
 * @param weepingSeep a Weeping Seep touches this radio's network, so orders can count on its filler
 */
public record TrayPayload(int containerId, int outOfRange, int unbound, int freeShades, int planDepth, int planNodes, boolean weepingSeep, List<Machine> machines,
        List<JobView> jobs) implements CustomPacketPayload {

    /** A running single-step job, as the radio's screen shows it. */
    /**
     * What a job is held up by: the input storage is short of (and by how many, for one operation), another job here
     * that's making it (its id, or 0), or the machine's reason for refusing a load.
     */
    public record Wait(ItemStack item, FluidStack fluid, int feeder, @javax.annotation.Nullable net.minecraft.network.chat.Component note) {
        public static final Wait NONE = new Wait(ItemStack.EMPTY, FluidStack.EMPTY, 0, null);

        boolean sameAs(Wait o) {
            return ItemStack.matches(item, o.item) && FluidStack.matches(fluid, o.fluid) && feeder == o.feeder
                    && java.util.Objects.equals(note, o.note);
        }
    }

    /** A standing contract in a reliquary slot, as the radio's jobs pane lists it. */
    public record StandingView(int slot, String tag, int count, boolean paused) {}

    /** @param fluid what a fluid job makes (then {@code output} is empty, and the counts are mB) */
    /**
     * @param order       the order this job is a step of (0 = a single job)
     * @param root        the order's last step (what was ordered), not a sub-craft
     * @param orderOutput what the order is for (empty for a fluid order, or no order)
     * @param orderFluid  the fluid the order is for (empty otherwise)
     * @param orderAmount how many items (or mB) were ordered
     */
    public record JobView(int id, ItemStack output, FluidStack fluid, int totalOps, int pushedOps, long received, long expected, byte status,
            int order, boolean root, ItemStack orderOutput, FluidStack orderFluid, long orderAmount, Wait hold) {
        public JobView(int id, ItemStack output, FluidStack fluid, int totalOps, int pushedOps, long received, long expected, byte status) {
            this(id, output, fluid, totalOps, pushedOps, received, expected, status, 0, false, ItemStack.EMPTY, FluidStack.EMPTY, 0, Wait.NONE);
        }

        public JobView(int id, ItemStack output, FluidStack fluid, int totalOps, int pushedOps, long received, long expected, byte status,
                int order, boolean root, ItemStack orderOutput, FluidStack orderFluid, long orderAmount) {
            this(id, output, fluid, totalOps, pushedOps, received, expected, status, order, root, orderOutput, orderFluid, orderAmount, Wait.NONE);
        }

        public JobView withWait(Wait w) {
            return new JobView(id, output, fluid, totalOps, pushedOps, received, expected, status, order, root, orderOutput, orderFluid, orderAmount, w);
        }

        boolean sameAs(JobView o) {
            return id == o.id && totalOps == o.totalOps && pushedOps == o.pushedOps && received == o.received
                    && expected == o.expected && status == o.status && ItemStack.matches(output, o.output)
                    && FluidStack.isSameFluidSameComponents(fluid, o.fluid) && order == o.order && root == o.root
                    && orderAmount == o.orderAmount && hold.sameAs(o.hold);
        }

        public boolean isFluid() {
            return !fluid.isEmpty();
        }
    }

    /**
     * @param roles      one {@code BoundInventory.Role} ordinal per slot
     * @param spiritFed  the reliquary keeps this machine burning (no fuel needed)
     * @param contracts  the reliquary's contract slots, null where empty (or holding a standing contract)
     * @param name       the spirit's name, "" if unnamed
     * @param standing   the reliquary's standing contracts
     * @param tanks      the machine's fluid tanks (shown, not clickable)
     */
    public record Machine(BlockPos pos, BlockState state, List<ItemStack> slots, byte[] roles, boolean spiritFed, List<ContractData> contracts,
            String name, List<StandingView> standing, List<Tank> tanks) {
        /** The spirit's name if it has one, else the machine's block name. */
        public Component displayName() {
            return name.isEmpty() ? state.getBlock().getName() : Component.literal(name);
        }

        public boolean sameAs(Machine o) {
            if (!pos.equals(o.pos) || state != o.state || spiritFed != o.spiritFed || !name.equals(o.name) || !standing.equals(o.standing)
                    || !Arrays.equals(roles, o.roles) || slots.size() != o.slots.size()
                    || contracts.size() != o.contracts.size() || !Tank.same(tanks, o.tanks)) {
                return false;
            }
            for (int i = 0; i < contracts.size(); i++) {
                ContractData a = contracts.get(i);
                ContractData b = o.contracts.get(i);
                if (a == null ? b != null : !a.sameAs(b)) {
                    return false;
                }
            }
            for (int i = 0; i < slots.size(); i++) {
                if (!ItemStack.matches(slots.get(i), o.slots.get(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    /** One fluid tank of a machine: what's in it (possibly empty) and how much it holds, in mB. */
    public record Tank(FluidStack fluid, int capacity) {
        public static final int MAX = 8;

        static boolean same(List<Tank> a, List<Tank> b) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (a.get(i).capacity != b.get(i).capacity || !FluidStack.matches(a.get(i).fluid, b.get(i).fluid)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static final Type<TrayPayload> TYPE = new Type<>(Tarbound.id("tray"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrayPayload> STREAM_CODEC =
            StreamCodec.ofMember(TrayPayload::write, TrayPayload::read);

    public boolean sameAs(TrayPayload o) {
        if (o == null || outOfRange != o.outOfRange || unbound != o.unbound || freeShades != o.freeShades || planDepth != o.planDepth || planNodes != o.planNodes || weepingSeep != o.weepingSeep || machines.size() != o.machines.size()
                || jobs.size() != o.jobs.size()) {
            return false;
        }
        for (int i = 0; i < jobs.size(); i++) {
            if (!jobs.get(i).sameAs(o.jobs.get(i))) {
                return false;
            }
        }
        for (int i = 0; i < machines.size(); i++) {
            if (!machines.get(i).sameAs(o.machines.get(i))) {
                return false;
            }
        }
        return true;
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(containerId);
        buf.writeVarInt(outOfRange);
        buf.writeVarInt(unbound);
        buf.writeVarInt(freeShades);
        buf.writeVarInt(planDepth);
        buf.writeVarInt(planNodes);
        buf.writeBoolean(weepingSeep);
        buf.writeVarInt(machines.size());
        for (Machine m : machines) {
            buf.writeBlockPos(m.pos());
            buf.writeVarInt(Block.getId(m.state()));
            buf.writeBoolean(m.spiritFed());
            buf.writeVarInt(m.slots().size());
            for (int i = 0; i < m.slots().size(); i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, m.slots().get(i));
                buf.writeByte(m.roles()[i]);
            }
            buf.writeVarInt(m.contracts().size());
            for (ContractData c : m.contracts()) {
                buf.writeBoolean(c != null);
                if (c != null) {
                    ContractData.STREAM_CODEC.encode(buf, c);
                }
            }
            buf.writeUtf(m.name(), 256);
            buf.writeVarInt(m.standing().size());
            for (StandingView v : m.standing()) {
                buf.writeVarInt(v.slot());
                buf.writeUtf(v.tag(), 256);
                buf.writeVarInt(v.count());
                buf.writeBoolean(v.paused());
            }
            buf.writeVarInt(m.tanks().size());
            for (Tank t : m.tanks()) {
                FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, t.fluid());
                buf.writeVarInt(t.capacity());
            }
        }
        buf.writeVarInt(jobs.size());
        for (JobView j : jobs) {
            buf.writeVarInt(j.id());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, j.output());
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, j.fluid());
            buf.writeVarInt(j.totalOps());
            buf.writeVarInt(j.pushedOps());
            buf.writeVarLong(j.received());
            buf.writeVarLong(j.expected());
            buf.writeByte(j.status());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, j.hold().item());
            FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, j.hold().fluid());
            buf.writeVarInt(j.hold().feeder());
            net.minecraft.network.chat.ComponentSerialization.OPTIONAL_STREAM_CODEC.encode(buf, java.util.Optional.ofNullable(j.hold().note()));
            buf.writeVarInt(j.order());
            if (j.order() != 0) {
                buf.writeBoolean(j.root());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, j.orderOutput());
                FluidStack.OPTIONAL_STREAM_CODEC.encode(buf, j.orderFluid());
                buf.writeVarLong(j.orderAmount());
            }
        }
    }

    private static TrayPayload read(RegistryFriendlyByteBuf buf) {
        int containerId = buf.readVarInt();
        int outOfRange = buf.readVarInt();
        int unbound = buf.readVarInt();
        int freeShades = buf.readVarInt();
        int planDepth = buf.readVarInt();
        int planNodes = buf.readVarInt();
        boolean weepingSeep = buf.readBoolean();
        int n = buf.readVarInt();
        List<Machine> machines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos pos = buf.readBlockPos();
            BlockState state = Block.stateById(buf.readVarInt());
            boolean fed = buf.readBoolean();
            int size = buf.readVarInt();
            List<ItemStack> slots = new ArrayList<>(size);
            byte[] roles = new byte[size];
            for (int s = 0; s < size; s++) {
                slots.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
                roles[s] = buf.readByte();
            }
            int nc = buf.readVarInt();
            if (nc < 0 || nc > 64) {
                throw new IllegalArgumentException("Bad contract slot count " + nc);
            }
            List<ContractData> contracts = new ArrayList<>(nc);
            for (int c = 0; c < nc; c++) {
                contracts.add(buf.readBoolean() ? ContractData.STREAM_CODEC.decode(buf) : null);
            }
            String name = buf.readUtf(256);
            int ns = buf.readVarInt();
            if (ns < 0 || ns > 64) {
                throw new IllegalArgumentException("Bad standing count " + ns);
            }
            List<StandingView> standing = new ArrayList<>(ns);
            for (int s = 0; s < ns; s++) {
                standing.add(new StandingView(buf.readVarInt(), buf.readUtf(256), buf.readVarInt(), buf.readBoolean()));
            }
            int nt = buf.readVarInt();
            if (nt < 0 || nt > Tank.MAX) {
                throw new IllegalArgumentException("Bad tank count " + nt);
            }
            List<Tank> tanks = new ArrayList<>(nt);
            for (int t = 0; t < nt; t++) {
                tanks.add(new Tank(FluidStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt()));
            }
            machines.add(new Machine(pos, state, slots, roles, fed, contracts, name, standing, tanks));
        }
        int nj = buf.readVarInt();
        List<JobView> jobs = new ArrayList<>(nj);
        for (int j = 0; j < nj; j++) {
            int id = buf.readVarInt();
            ItemStack output = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            FluidStack fluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buf);
            int total = buf.readVarInt();
            int pushed = buf.readVarInt();
            long received = buf.readVarLong();
            long expected = buf.readVarLong();
            byte status = buf.readByte();
            Wait wait = new Wait(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), FluidStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(),
                    net.minecraft.network.chat.ComponentSerialization.OPTIONAL_STREAM_CODEC.decode(buf).orElse(null));
            int order = buf.readVarInt();
            if (order == 0) {
                jobs.add(new JobView(id, output, fluid, total, pushed, received, expected, status).withWait(wait));
            } else {
                boolean root = buf.readBoolean();
                ItemStack orderOutput = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                FluidStack orderFluid = FluidStack.OPTIONAL_STREAM_CODEC.decode(buf);
                long orderAmount = buf.readVarLong();
                jobs.add(new JobView(id, output, fluid, total, pushed, received, expected, status, order, root, orderOutput, orderFluid, orderAmount, wait));
            }
        }
        return new TrayPayload(containerId, outOfRange, unbound, freeShades, planDepth, planNodes, weepingSeep, machines, jobs);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
