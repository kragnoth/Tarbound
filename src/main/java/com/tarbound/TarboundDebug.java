package com.tarbound;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Optional trace of what radios try to move, for bug reports: machine loads (items and fluids, and why one is
 * refused), pool tank drains, and Transient Antenna pushes and pulls. Off by default (server config
 * {@code debug.traceLoading}); when on, lines go to {@code logs/tarbound-debug.log} in the game folder, a file
 * small enough to upload. A message that repeats for the same block is written once every 10 seconds at most.
 */
public final class TarboundDebug {
    private static final Path FILE = Path.of("logs", "tarbound-debug.log");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final long REPEAT_MS = 10_000;
    private static final Map<String, Seen> LAST = new ConcurrentHashMap<>();
    private static boolean started;

    private record Seen(String msg, long at) {}

    private TarboundDebug() {}

    /** {@code /tarbound trace} (ops): flips {@code debug.traceLoading} and says where the file is. */
    public static void registerCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("tarbound")
                .requires(src -> src.hasPermission(2))
                .then(net.minecraft.commands.Commands.literal("trace").executes(ctx -> {
                    boolean now = !on();
                    com.tarbound.config.TarboundConfig.SERVER.traceLoading.set(now);
                    if (now) {
                        LAST.clear();
                    }
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal(now
                            ? "Tarbound trace ON: writing to " + FILE.toAbsolutePath().normalize() + " (run /tarbound trace again to stop)"
                            : "Tarbound trace OFF. The file is " + FILE.toAbsolutePath().normalize()), true);
                    return 1;
                })));
    }

    public static boolean on() {
        try {
            return com.tarbound.config.TarboundConfig.SERVER.traceLoading.get();
        } catch (IllegalStateException e) {
            return false; // config not loaded yet
        }
    }

    /** Writes {@code msg} about the block at {@code pos} (skipped if it said exactly this there in the last 10 s). */
    public static void log(Level level, BlockPos pos, String what, String msg) {
        if (!on() || level == null || level.isClientSide) {
            return;
        }
        String key = level.dimension().location() + "|" + pos.asLong() + "|" + what;
        long now = System.currentTimeMillis();
        Seen seen = LAST.get(key);
        if (seen != null && seen.msg.equals(msg) && now - seen.at < REPEAT_MS) {
            return;
        }
        LAST.put(key, new Seen(msg, now));
        write(LocalTime.now().format(TIME) + " [t" + level.getGameTime() + "] " + level.dimension().location().getPath()
                + " " + pos.toShortString() + " " + what + ": " + msg);
    }

    private static synchronized void write(String line) {
        try {
            Files.createDirectories(FILE.getParent());
            if (!started) {
                started = true;
                append("==== Tarbound " + net.neoforged.fml.ModList.get().getModContainerById(Tarbound.MODID)
                        .map(c -> c.getModInfo().getVersion().toString()).orElse("?") + " trace started " + java.time.LocalDateTime.now() + " ====");
            }
            append(line);
        } catch (IOException e) {
            Tarbound.LOGGER.warn("Tarbound: can't write {}: {}", FILE, e.toString());
        }
    }

    private static void append(String line) throws IOException {
        try (Writer w = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(line);
            w.write(System.lineSeparator());
        }
    }

    // ------------------------------------------------------------------------------------------ formatting helpers

    public static String block(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
    }

    public static String fluid(FluidStack f) {
        if (f.isEmpty()) {
            return "empty";
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(f.getFluid()).toString();
        return f.getAmount() + "mB " + id + (f.getComponentsPatch().isEmpty() ? "" : " " + f.getComponentsPatch());
    }

    /** Every tank of a handler: contents, capacity, and whether it says {@code probe} is valid there. */
    public static String tanks(IFluidHandler h, FluidStack probe) {
        if (h == null) {
            return "no fluid handler";
        }
        StringBuilder b = new StringBuilder(h.getClass().getName()).append(" tanks=").append(h.getTanks());
        for (int i = 0; i < h.getTanks(); i++) {
            b.append(" [").append(i).append(": ").append(fluid(h.getFluidInTank(i))).append(" / ").append(h.getTankCapacity(i));
            if (!probe.isEmpty()) {
                b.append(", valid=").append(h.isFluidValid(i, probe));
            }
            b.append(']');
        }
        if (!probe.isEmpty()) {
            b.append(" simFill(").append(probe.getAmount()).append(")=").append(h.fill(probe.copy(), IFluidHandler.FluidAction.SIMULATE));
        }
        return b.toString();
    }
}
