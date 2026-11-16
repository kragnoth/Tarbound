package com.tarbound.network;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * How a Reliquary reaches a bound machine's slots and tanks: the way the machine's own screen would, rather than
 * through whichever face a pipe happens to see.
 *
 * <p>Most machines hand out a full, writable view on the side-less ({@code null}) query, and that is used as is
 * (EnderIO, Oritech, Immersive Engineering's single-inventory machines, vanilla). The exceptions are handled here:
 * <ul>
 *   <li><b>Mekanism</b> (and its addons, which share its inventory API): the side-less view is read-only, and each
 *   face only sees what its side configuration allows, with face-relative slot numbers. We go to the machine's own
 *   slot and tank lists instead, acting as a player in its GUI would ({@code AutomationType.MANUAL}): inputs can go
 *   in and come back out, outputs can only come out. Reached by reflection, so Mekanism isn't needed to build or
 *   run Tarbound.</li>
 *   <li>Machines with <b>no side-less fluid handler</b> (Immersive Engineering's multiblocks register tanks on
 *   particular faces of particular blocks): every distinct face handler of the block, joined.</li>
 * </ul>
 */
public final class MachineAccess {
    private MachineAccess() {}

    /** The machine's slots as its GUI sees them, when the side-less capability isn't that; else null. */
    @Nullable
    public static IItemHandler items(@Nullable BlockEntity be) {
        return be == null ? null : Mekanism.items(be);
    }

    /**
     * The machine's tanks: Mekanism's own tanks, else the side-less handler, else every distinct face handler of the
     * block joined into one. Null if it has none.
     */
    @Nullable
    public static IFluidHandler fluids(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (be != null) {
            IFluidHandler mek = Mekanism.fluids(be);
            if (mek != null) {
                return mek;
            }
        }
        IFluidHandler all = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, null);
        if (all != null && all.getTanks() > 0) {
            return all;
        }
        List<IFluidHandler> faces = new ArrayList<>();
        for (Direction d : Direction.values()) {
            IFluidHandler h = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, be, d);
            if (h == null || h.getTanks() <= 0 || faces.stream().anyMatch(f -> f == h || f.equals(h) || sameTanks(f, h))) {
                continue;
            }
            faces.add(h);
        }
        if (faces.isEmpty()) {
            return all;
        }
        return faces.size() == 1 ? faces.get(0) : new Joined(faces);
    }

    /** Two face handlers over the very same tanks (the same fluid stack objects, in the same order). */
    private static boolean sameTanks(IFluidHandler a, IFluidHandler b) {
        int n = a.getTanks();
        if (n != b.getTanks()) {
            return false;
        }
        boolean sawFluid = false;
        for (int i = 0; i < n; i++) {
            FluidStack fa = a.getFluidInTank(i);
            if (fa != b.getFluidInTank(i)) {
                return false;
            }
            sawFluid |= !fa.isEmpty();
        }
        return sawFluid;
    }

    /** Several face handlers as one: tanks listed in turn, fills and drains tried on each in turn. */
    private record Joined(List<IFluidHandler> parts) implements IFluidHandler {
        @Override
        public int getTanks() {
            int n = 0;
            for (IFluidHandler p : parts) {
                n += p.getTanks();
            }
            return n;
        }

        private int[] locate(int tank) {
            for (int i = 0; i < parts.size(); i++) {
                int n = parts.get(i).getTanks();
                if (tank < n) {
                    return new int[] {i, tank};
                }
                tank -= n;
            }
            return null;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            int[] at = locate(tank);
            return at == null ? FluidStack.EMPTY : parts.get(at[0]).getFluidInTank(at[1]);
        }

        @Override
        public int getTankCapacity(int tank) {
            int[] at = locate(tank);
            return at == null ? 0 : parts.get(at[0]).getTankCapacity(at[1]);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            int[] at = locate(tank);
            return at != null && parts.get(at[0]).isFluidValid(at[1], stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            int done = 0;
            for (IFluidHandler p : parts) {
                if (done >= resource.getAmount()) {
                    break;
                }
                // Simulating: each part is asked separately, so a tank two faces share would be counted twice.
                // Only the first part that takes any is asked, which is exact for the usual one-input-tank machine.
                int in = p.fill(resource.copyWithAmount(resource.getAmount() - done), action);
                done += in;
                if (action.simulate() && in > 0) {
                    break;
                }
            }
            return done;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            int done = 0;
            for (IFluidHandler p : parts) {
                if (done >= resource.getAmount()) {
                    break;
                }
                FluidStack out = p.drain(resource.copyWithAmount(resource.getAmount() - done), action);
                done += out.getAmount();
                if (action.simulate() && !out.isEmpty()) {
                    break;
                }
            }
            return done <= 0 ? FluidStack.EMPTY : resource.copyWithAmount(done);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            for (IFluidHandler p : parts) {
                FluidStack out = p.drain(maxDrain, FluidAction.SIMULATE);
                if (!out.isEmpty()) {
                    return drain(out.copyWithAmount(maxDrain), action);
                }
            }
            return FluidStack.EMPTY;
        }
    }

    /**
     * Mekanism's inventory and tank API, by reflection. Looked up once from the first block entity that might be
     * Mekanism's; if anything is missing (Mekanism absent, or its API changed), this just stands down.
     */
    private static final class Mekanism {
        private static boolean tried;
        @Nullable
        private static Api api;

        private record Api(Class<?> inventory, Method hasInventory, Method slots, Method getStack, Method insertItem, Method extractItem,
                Method getLimit, Method isItemValid, @Nullable Method slotType, Object outputType,
                Class<?> fluidHandler, Method canHandleFluid, Method tanks, Method getFluid, Method getCapacity, Method isFluidValid,
                Method insertFluid, Method extractFluid,
                Object execute, Object simulate, Object manual) {}

        @Nullable
        private static Api api(BlockEntity be) {
            if (!tried) {
                tried = true;
                if (!net.neoforged.fml.ModList.get().isLoaded("mekanism")) {
                    return null;
                }
                try {
                    ClassLoader cl = be.getClass().getClassLoader();
                    Class<?> inv = Class.forName("mekanism.api.inventory.IMekanismInventory", false, cl);
                    Class<?> slot = Class.forName("mekanism.api.inventory.IInventorySlot", false, cl);
                    Class<?> action = Class.forName("mekanism.api.Action", false, cl);
                    Class<?> automation = Class.forName("mekanism.api.AutomationType", false, cl);
                    Class<?> fh = Class.forName("mekanism.api.fluid.IMekanismFluidHandler", false, cl);
                    Class<?> tank = Class.forName("mekanism.api.fluid.IExtendedFluidTank", false, cl);
                    Method slotType = null;
                    Object outputType = null;
                    try {
                        Class<?> basic = Class.forName("mekanism.common.inventory.slot.BasicInventorySlot", false, cl);
                        Class<?> types = Class.forName("mekanism.common.inventory.container.slot.ContainerSlotType", false, cl);
                        slotType = basic.getDeclaredMethod("getSlotType");
                        slotType.setAccessible(true);
                        outputType = enumValue(types, "OUTPUT");
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        slotType = null; // fall back to watching what the slot refuses
                    }
                    api = new Api(inv, inv.getMethod("hasInventory"), inv.getMethod("getInventorySlots", Direction.class),
                            slot.getMethod("getStack"), slot.getMethod("insertItem", ItemStack.class, action, automation),
                            slot.getMethod("extractItem", int.class, action, automation), slot.getMethod("getLimit", ItemStack.class),
                            slot.getMethod("isItemValid", ItemStack.class), slotType, outputType,
                            fh, fh.getMethod("canHandleFluid"), fh.getMethod("getFluidTanks", Direction.class),
                            tank.getMethod("getFluid"), tank.getMethod("getCapacity"), tank.getMethod("isFluidValid", FluidStack.class),
                            tank.getMethod("insert", FluidStack.class, action, automation), tank.getMethod("extract", int.class, action, automation),
                            enumValue(action, "EXECUTE"), enumValue(action, "SIMULATE"), enumValue(automation, "MANUAL"));
                } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                    api = null;
                }
            }
            return api;
        }

        private static Object enumValue(Class<?> type, String name) {
            for (Object c : type.getEnumConstants()) {
                if (((Enum<?>) c).name().equals(name)) {
                    return c;
                }
            }
            throw new IllegalStateException(type + " has no " + name);
        }

        private static Object call(Method m, Object on, Object... args) {
            try {
                return m.invoke(on, args);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }

        @Nullable
        static IItemHandler items(BlockEntity be) {
            Api a = api(be);
            if (a == null || !a.inventory().isInstance(be)) {
                return null;
            }
            try {
                if (!(Boolean) call(a.hasInventory(), be)) {
                    return null;
                }
                List<?> slots = (List<?>) call(a.slots(), be, (Object) null);
                return slots.isEmpty() ? null : new Items(a, List.copyOf(slots));
            } catch (RuntimeException e) {
                return null;
            }
        }

        @Nullable
        static IFluidHandler fluids(BlockEntity be) {
            Api a = api(be);
            if (a == null || !a.fluidHandler().isInstance(be)) {
                return null;
            }
            try {
                if (!(Boolean) call(a.canHandleFluid(), be)) {
                    return null;
                }
                List<?> tanks = (List<?>) call(a.tanks(), be, (Object) null);
                return tanks.isEmpty() ? null : new Fluids(a, List.copyOf(tanks));
            } catch (RuntimeException e) {
                return null;
            }
        }

        /** A Mekanism machine's slots, worked as a player in its GUI would. */
        private record Items(Api a, List<?> slots) implements IItemHandler {
            @Override
            public int getSlots() {
                return slots.size();
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return (ItemStack) call(a.getStack(), slots.get(slot));
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
                return (ItemStack) call(a.insertItem(), slots.get(slot), stack, simulate ? a.simulate() : a.execute(), a.manual());
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (amount <= 0) {
                    return ItemStack.EMPTY;
                }
                return (ItemStack) call(a.extractItem(), slots.get(slot), amount, simulate ? a.simulate() : a.execute(), a.manual());
            }

            @Override
            public int getSlotLimit(int slot) {
                return (Integer) call(a.getLimit(), slots.get(slot), ItemStack.EMPTY);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                Object s = slots.get(slot);
                if (a.slotType() != null && a.slotType().getDeclaringClass().isInstance(s) && call(a.slotType(), s) == a.outputType()) {
                    return false; // an output slot: nothing goes back in
                }
                return (Boolean) call(a.isItemValid(), s, stack);
            }
        }

        /** A Mekanism machine's fluid tanks (not its chemical tanks), worked as a player in its GUI would. */
        private record Fluids(Api a, List<?> tanks) implements IFluidHandler {
            @Override
            public int getTanks() {
                return tanks.size();
            }

            @Override
            public FluidStack getFluidInTank(int tank) {
                return (FluidStack) call(a.getFluid(), tanks.get(tank));
            }

            @Override
            public int getTankCapacity(int tank) {
                return (Integer) call(a.getCapacity(), tanks.get(tank));
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return (Boolean) call(a.isFluidValid(), tanks.get(tank), stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) {
                    return 0;
                }
                Object how = action.simulate() ? a.simulate() : a.execute();
                FluidStack rest = resource.copy();
                // Tanks already holding this fluid first, then empty ones, as Mekanism's own handler does.
                for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
                    for (Object t : tanks) {
                        FluidStack in = (FluidStack) call(a.getFluid(), t);
                        if ((pass == 0) == in.isEmpty()) {
                            continue;
                        }
                        rest = (FluidStack) call(a.insertFluid(), t, rest, how, a.manual());
                        if (rest.isEmpty() || action.simulate() && rest.getAmount() < resource.getAmount()) {
                            break; // simulating: one tank's answer, as tanks don't see each other's simulated fill
                        }
                    }
                    if (action.simulate() && rest.getAmount() < resource.getAmount()) {
                        break;
                    }
                }
                return resource.getAmount() - rest.getAmount();
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                if (resource.isEmpty()) {
                    return FluidStack.EMPTY;
                }
                Object how = action.simulate() ? a.simulate() : a.execute();
                int done = 0;
                for (Object t : tanks) {
                    if (done >= resource.getAmount()) {
                        break;
                    }
                    FluidStack in = (FluidStack) call(a.getFluid(), t);
                    if (in.isEmpty() || !FluidStack.isSameFluidSameComponents(in, resource)) {
                        continue;
                    }
                    FluidStack out = (FluidStack) call(a.extractFluid(), t, resource.getAmount() - done, how, a.manual());
                    done += out.getAmount();
                    if (action.simulate() && !out.isEmpty()) {
                        break;
                    }
                }
                return done <= 0 ? FluidStack.EMPTY : resource.copyWithAmount(done);
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                for (Object t : tanks) {
                    FluidStack in = (FluidStack) call(a.getFluid(), t);
                    if (!in.isEmpty()) {
                        return drain(in.copyWithAmount(maxDrain), action);
                    }
                }
                return FluidStack.EMPTY;
            }
        }
    }
}
