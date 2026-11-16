package com.tarbound.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Tarbound's two config files:
 * <ul>
 *   <li><b>tarbound-client.toml</b> (per player): sounds and particles.</li>
 *   <li><b>tarbound-server.toml</b> (per world, in serverconfig/; synced to clients): reach, limits,
 *       timing and the placeholder free power.</li>
 * </ul>
 */
public final class TarboundConfig {
    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        ModConfigSpec.Builder c = new ModConfigSpec.Builder();
        CLIENT = new Client(c);
        CLIENT_SPEC = c.build();
        ModConfigSpec.Builder s = new ModConfigSpec.Builder();
        SERVER = new Server(s);
        SERVER_SPEC = s.build();
    }

    private TarboundConfig() {}

    /** What the radio's pool view lists. */
    public enum PoolView {
        ITEMS, FLUIDS, BOTH;

        public PoolView next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public boolean items() {
            return this != FLUIDS;
        }

        public boolean fluids() {
            return this != ITEMS;
        }
    }

    public static final class Client {
        public final ModConfigSpec.BooleanValue radioSounds;
        public final ModConfigSpec.BooleanValue radioParticles;
        public final ModConfigSpec.BooleanValue reliquarySounds;
        public final ModConfigSpec.BooleanValue reliquaryParticles;
        public final ModConfigSpec.BooleanValue wardParticles;
        public final ModConfigSpec.BooleanValue antennaParticles;
        public final ModConfigSpec.BooleanValue tarSwallowParticles;
        public final ModConfigSpec.BooleanValue tarGulpSound;
        public final ModConfigSpec.BooleanValue syncViewerSearch;
        public final ModConfigSpec.BooleanValue craftablesFirst;
        public final ModConfigSpec.EnumValue<PoolView> poolView;

        Client(ModConfigSpec.Builder b) {
            b.push("sounds");
            radioSounds = b.comment("The Scrying Font's idle static and the tune-in sound when opened.")
                    .define("radioSounds", true);
            reliquarySounds = b.comment("The Reliquary's occasional soul sound.")
                    .define("reliquarySounds", true);
            tarGulpSound = b.comment("The gulp when tar swallows an item, and the spit when the pool can't take it.")
                    .define("tarGulpSound", true);
            b.pop();
            b.push("particles");
            radioParticles = b.comment("Smoke and souls drifting from the radio's speaker.")
                    .define("radioParticles", true);
            reliquaryParticles = b.comment("The Reliquary's candle flame, smoke and soul particles.")
                    .define("reliquaryParticles", true);
            wardParticles = b.comment("Wisps and a gold glint around a Reliquary whose spirits are hoarding (the anti-magnet ward).")
                    .define("wardParticles", true);
            antennaParticles = b.comment("Smoke and souls at the tip of a tuned antenna.")
                    .define("antennaParticles", true);
            tarSwallowParticles = b.comment("Bubbles and soul wisps around items sinking in tar.")
                    .define("tarSwallowParticles", true);
            b.pop();
            b.push("screen");
            syncViewerSearch = b.comment("Keep the radio's search box and JEI's / EMI's search in step (both ways).",
                            "Also toggled with the ⇄ button beside the radio's search box.")
                    .define("syncViewerSearch", false);
            craftablesFirst = b.comment("List items a contract can make at the top of the radio's pool, whatever the sort.",
                            "Also toggled with the ◆ button beside the search box.")
                    .define("craftablesFirst", false);
            poolView = b.comment("What the radio's pool shows: ITEMS, FLUIDS or BOTH (fluids first).",
                            "Also cycled with the button left of the crafting grid.")
                    .defineEnum("poolView", PoolView.BOTH);
            b.pop();
        }
    }

    public static final class Server {
        public final ModConfigSpec.IntValue radioReach;
        public final ModConfigSpec.IntValue maxTarBlocks;
        public final ModConfigSpec.IntValue antennaReachPerPiece;
        public final ModConfigSpec.IntValue maxJobs;
        public final ModConfigSpec.IntValue maxOpsPerJob;
        public final ModConfigSpec.IntValue opsPerPass;
        public final ModConfigSpec.IntValue jobInterval;
        public final ModConfigSpec.IntValue networkRescanSeconds;
        public final ModConfigSpec.BooleanValue jobNotices;
        public final ModConfigSpec.IntValue maxRacks;
        public final ModConfigSpec.IntValue rackJobs;
        public final ModConfigSpec.IntValue rackPlanDepth;
        public final ModConfigSpec.IntValue rackPlanNodes;
        public final ModConfigSpec.IntValue rackSpiritCost;
        public final ModConfigSpec.IntValue rackReach;
        public final ModConfigSpec.IntValue rackSkullReach;
        public final ModConfigSpec.IntValue weepingSeepPerPass;
        public final ModConfigSpec.IntValue idleInterval;
        public final ModConfigSpec.IntValue standingBufferOps;
        public final ModConfigSpec.IntValue stationCraftsPerPass;
        public final ModConfigSpec.IntValue jobStallSeconds;
        public final ModConfigSpec.IntValue maxPlanDepth;
        public final ModConfigSpec.IntValue maxPlanNodes;
        public final ModConfigSpec.IntValue freeFePerTick;
        public final ModConfigSpec.BooleanValue spiritFire;
        public final ModConfigSpec.BooleanValue antiMagnet;
        public final ModConfigSpec.DoubleValue antiMagnetRadius;
        public final ModConfigSpec.IntValue spiritBoardRange;
        public final ModConfigSpec.IntValue shiftCraftStacks;
        public final ModConfigSpec.BooleanValue spiritCosts;
        public final ModConfigSpec.IntValue spiritCapacity;
        public final ModConfigSpec.IntValue jobOpCost;
        public final ModConfigSpec.IntValue stationCraftCost;
        public final ModConfigSpec.IntValue standingOpCost;
        public final ModConfigSpec.IntValue spiritFireCost;
        public final ModConfigSpec.IntValue boardCostPerSecond;
        public final ModConfigSpec.BooleanValue machinePower;
        public final ModConfigSpec.IntValue fePerSpirit;
        public final ModConfigSpec.BooleanValue vitaeFeed;
        public final ModConfigSpec.IntValue vitaePerSpirit;
        public final ModConfigSpec.IntValue vitaePerSecond;
        public final ModConfigSpec.IntValue vitaeTopUp;
        public final ModConfigSpec.IntValue batteryCapacity;
        public final ModConfigSpec.IntValue transientInterval;
        public final ModConfigSpec.IntValue transientItemsPerOp;
        public final ModConfigSpec.IntValue transientFluidPerOp;
        public final ModConfigSpec.IntValue transientFePerOp;
        public final ModConfigSpec.BooleanValue tarHarmsMobs;
        public final ModConfigSpec.DoubleValue tarDamage;
        public final ModConfigSpec.DoubleValue tarSpiritPerHealth;
        public final ModConfigSpec.BooleanValue tarSwallows;
        public final ModConfigSpec.BooleanValue traceLoading;
        public final ModConfigSpec.IntValue tarSwallowTicks;
        public final ModConfigSpec.IntValue tarSwallowRetryTicks;

        Server(ModConfigSpec.Builder b) {
            b.push("network");
            radioReach = b.comment("How far (in blocks, a cube) the radio reaches to touch tar, and to link antennas.")
                    .defineInRange("radioReach", 3, 1, 16);
            maxTarBlocks = b.comment("Most tar blocks one scan walks, so a flooded area can't stall the server.")
                    .defineInRange("maxTarBlocks", 8192, 64, 1_000_000);
            antennaReachPerPiece = b.comment("Antenna link reach per stacked piece (1 piece = this, 3 pieces = 3x this).")
                    .defineInRange("antennaReachPerPiece", 3, 1, 32);
            b.pop();

            b.push("jobs");
            maxJobs = b.comment("Most single jobs plus orders one radio runs at once. An order counts once, however many",
                            "sub-crafts it has (like an AE2 crafting CPU); how big an order can be is up to its Shade's rank.")
                    .defineInRange("maxJobs", 16, 1, 256);
            maxOpsPerJob = b.comment("Most operations one job may be ordered for.")
                    .defineInRange("maxOpsPerJob", 4096, 1, 1_000_000);
            opsPerPass = b.comment("Operations a job (or standing contract) may load per pass, across all its machines.")
                    .defineInRange("opsPerPass", 8, 1, 256);
            maxRacks = b.comment("Most lit Votive Racks one radio counts.")
                    .defineInRange("maxRacks", 4, 0, 16);
            rackJobs = b.comment("Extra jobs and orders a radio may run at once per lit Votive Rack.")
                    .defineInRange("rackJobs", 4, 0, 64);
            rackPlanDepth = b.comment("Extra levels of sub-crafts an order may plan per lit Votive Rack (never past 32 in all).")
                    .defineInRange("rackPlanDepth", 2, 0, 16);
            rackPlanNodes = b.comment("Extra plan lines an order may have per lit Votive Rack.")
                    .defineInRange("rackPlanNodes", 128, 0, 4096);
            rackSpiritCost = b.comment("Spirit each lit Votive Rack burns per radio pass while jobs or orders run (a radio that can't pay snuffs them).")
                    .defineInRange("rackSpiritCost", 1, 0, 100);
            rackReach = b.comment("How far from the radio a Votive Rack counts, sideways (it may sit 1 below to 2 above).")
                    .defineInRange("rackReach", 3, 1, 8);
            rackSkullReach = b.comment("The same with a Votive Skull set on the rack.")
                    .defineInRange("rackSkullReach", 5, 1, 8);
            weepingSeepPerPass = b.comment("Items each Weeping Seep adds per radio pass to what it may make for that radio's jobs and orders (each still",
                    "costs Spirit). Unused amounts bank up to four passes (always at least a stack), so a big single load still goes through.")
                    .defineInRange("weepingSeepPerPass", 16, 0, 4096);
            jobNotices = b.comment("Tell the player who asked (in chat, with a chime) when their job or order is done.")
                    .define("jobNotices", true);
            networkRescanSeconds = b.comment("A radio keeps its scan of the tar network until something next to the tar, a Reliquary or an antenna",
                    "changes, or a bound block is broken or replaced; and rescans anyway after this many seconds. 0 rescans every pass.")
                    .defineInRange("networkRescanSeconds", 5, 0, 600);
            jobInterval = b.comment("Ticks between radio passes while jobs or standing contracts are running.")
                    .defineInRange("jobInterval", 5, 1, 200);
            idleInterval = b.comment("Ticks between radio passes when idle (draining machine outputs).")
                    .defineInRange("idleInterval", 20, 1, 1200);
            standingBufferOps = b.comment("A standing contract stops topping a machine up once it holds this many operations' worth.")
                    .defineInRange("standingBufferOps", 16, 1, 1024);
            stationCraftsPerPass = b.comment("Crafts one crafting station (a Reliquary on a crafting table) makes per pass.",
                            "Jobs spread across every station holding the contract, so more stations craft faster.")
                    .defineInRange("stationCraftsPerPass", 2, 1, 64);
            jobStallSeconds = b.comment("Once a job has loaded everything, how long (seconds) it waits with no new outputs",
                            "before closing itself anyway (chance outputs, items taken as they arrive, a stuck machine).")
                    .defineInRange("jobStallSeconds", 120, 10, 3600);
            maxPlanDepth = b.comment("How many levels deep an order may plan sub-crafts (ingredients of ingredients...).",
                            "A safety bound so a huge recipe tree can't stall the server; an AE2 256k cell needs about 10.")
                    .defineInRange("maxPlanDepth", 16, 1, 32);
            maxPlanNodes = b.comment("Most lines (items to take or craft) one order's plan may have. An AE2 256k cell needs about 80.")
                    .defineInRange("maxPlanNodes", 512, 4, 4096);
            b.pop();

            b.push("reliquary");
            freeFePerTick = b.comment("Most FE per tick a Reliquary pushes into the machine beneath it (paid in Spirit, see spirit.fePerSpirit).")
                    .defineInRange("freeFePerTick", 4096, 0, Integer.MAX_VALUE);
            spiritFire = b.comment("Reliquaries keep the furnace beneath them lit without fuel.")
                    .define("spiritFire", true);
            antiMagnet = b.comment("Allow the spirits' hoard on Reliquaries (a gold nugget: other mods' magnets can't pull drops near it).")
                    .define("antiMagnet", true);
            antiMagnetRadius = b.comment("Radius (in blocks, a sphere) of a Reliquary's hoard. 4 covers a Create fan's full processing run.")
                    .defineInRange("antiMagnetRadius", 4.0, 1.0, 16.0);
            b.pop();

            b.push("spirit_board");
            spiritBoardRange = b.comment("How far (in blocks) a Spirit Board reaches its radio. 0 = anywhere in the same dimension.")
                    .defineInRange("spiritBoardRange", 0, 0, 100_000);
            b.pop();

            b.push("crafting_grid");
            shiftCraftStacks = b.comment("Shift-clicking the radio's crafting result crafts at most this many stacks of it at once.",
                            "(Right-click always crafts up to one stack onto the cursor.)")
                    .defineInRange("shiftCraftStacks", 1, 1, 36);
            b.pop();

            b.push("spirit");
            spiritCosts = b.comment("Automation costs Spirit, which the radio holds and you feed with candles (or anything in the",
                            "tarbound:spirit_offerings data map). Storage access is always free. False makes everything free.")
                    .define("spiritCosts", true);
            spiritCapacity = b.comment("Most Spirit one radio holds.")
                    .defineInRange("spiritCapacity", 4000, 100, 1_000_000);
            jobOpCost = b.comment("Spirit per job operation loaded into a machine.")
                    .defineInRange("jobOpCost", 1, 0, 10_000);
            stationCraftCost = b.comment("Spirit per craft at a crafting station.")
                    .defineInRange("stationCraftCost", 1, 0, 10_000);
            standingOpCost = b.comment("Spirit per standing-contract operation.")
                    .defineInRange("standingOpCost", 1, 0, 10_000);
            spiritFireCost = b.comment("Spirit per smelt's worth (200 ticks) of Spirit Fire in a bound furnace.")
                    .defineInRange("spiritFireCost", 4, 0, 10_000);
            boardCostPerSecond = b.comment("Spirit per second while a Spirit Board holds a radio's screen open.")
                    .defineInRange("boardCostPerSecond", 1, 0, 10_000);
            machinePower = b.comment("Reliquaries feed FE to the machine beneath them (see reliquary.freeFePerTick for the rate).")
                    .define("machinePower", true);
            fePerSpirit = b.comment("FE a Reliquary delivers for one Spirit, once the radio's battery is empty.")
                    .defineInRange("fePerSpirit", 20_000, 1, Integer.MAX_VALUE);
            vitaeFeed = b.comment("NeoVitae: Reliquaries feed Essentia Vitae to the Ara Vitae beneath them while it crafts (just what the craft",
                    "needs), and keep stations in the tarbound:vitae_fed tag (the Athanor) topped up.")
                    .define("vitaeFeed", true);
            vitaePerSpirit = b.comment("Vitae (LP / mB) one Spirit buys. Vitae already in the pool is matched mB for mB by bought Vitae, so it",
                    "arrives doubled: with pool Vitae, each Spirit goes twice as far.")
                    .defineInRange("vitaePerSpirit", 10, 1, 1_000_000);
            vitaePerSecond = b.comment("Most Vitae per second a Reliquary feeds one station.")
                    .defineInRange("vitaePerSecond", 500, 0, 1_000_000);
            vitaeTopUp = b.comment("mB of Vitae a Reliquary keeps in a tarbound:vitae_fed station's tank (the Athanor uses 100 per disenchant).")
                    .defineInRange("vitaeTopUp", 1000, 0, 1_000_000);
            b.pop();

            b.push("energy");
            batteryCapacity = b.comment("FE the radio's built-in battery holds. It shows in the pool as Essence of the Void (1 FE = 1 mB),",
                            "and Essence in the pool's tanks tops the battery back up.")
                    .defineInRange("batteryCapacity", 1000, 0, Integer.MAX_VALUE);
            b.pop();

            b.push("transient_antenna");
            transientInterval = b.comment("Ticks between a Transient Antenna's pulls or pushes.")
                    .defineInRange("transientInterval", 10, 1, 200);
            transientItemsPerOp = b.comment("Most items a Transient Antenna moves per operation.")
                    .defineInRange("transientItemsPerOp", 64, 1, 4096);
            transientFluidPerOp = b.comment("Most mB of fluid a Transient Antenna moves per operation.")
                    .defineInRange("transientFluidPerOp", 1000, 1, 1_000_000);
            transientFePerOp = b.comment("Most FE a Transient Antenna moves per operation.")
                    .defineInRange("transientFePerOp", 10_000, 1, Integer.MAX_VALUE);
            b.pop();

            b.push("tar");
            tarHarmsMobs = b.comment("Mobs standing in tar are poisoned and slowly hurt (players never are).")
                    .define("tarHarmsMobs", true);
            tarDamage = b.comment("Damage per second to a mob in tar.")
                    .defineInRange("tarDamage", 1.0, 0.0, 100.0);
            tarSpiritPerHealth = b.comment("A mob that dies in a radio's tar feeds that radio this much Spirit per point of its max health",
                            "(a zombie has 20). 0 turns it off.")
                    .defineInRange("tarSpiritPerHealth", 1.0, 0.0, 100.0);
            b.pop();
            b.push("debug");
            traceLoading = b.comment("Write what radios try to load into machines (items and fluids, and why a load is refused), pool tank",
                            "drains, and Transient Antenna pushes and pulls to logs/tarbound-debug.log, for bug reports. Can be",
                            "switched on while the game runs. Leave off normally.")
                    .define("traceLoading", false);
            b.pop();
            b.push("tar");
            tarSwallows = b.comment("Items lying in a radio's tar sink and are swallowed into its pool (what doesn't fit is spat back up).",
                            "Items a hoard pins are left alone. Wild tar no radio holds only drags items down.")
                    .define("tarSwallows", true);
            tarSwallowTicks = b.comment("Ticks an item sinks in tar before it's swallowed.")
                    .defineInRange("tarSwallowTicks", 40, 5, 6000);
            tarSwallowRetryTicks = b.comment("Ticks before tar tries again to swallow an item it spat up because the pool was full.")
                    .defineInRange("tarSwallowRetryTicks", 100, 5, 6000);
            b.pop();
        }
    }
}
