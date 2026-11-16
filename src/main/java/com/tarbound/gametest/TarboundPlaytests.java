package com.tarbound.gametest;

import com.tarbound.Tarbound;
import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.contract.ContractData;
import com.tarbound.contract.ContractJob;
import com.tarbound.contract.CraftingContracts;
import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.network.ItemKey;
import com.tarbound.network.TarNetwork;
import com.tarbound.registry.ModBlocks;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A day-one playthrough, run headless by {@code ./gradlew runGameTestServer}: each test builds a small base the way a
 * player would (tar under chests, a radio, tuned Reliquaries placed against vanilla machines, contracts in their slots,
 * jobs and orders asked for through the radio's own menu code) and checks the spirits get on with it by themselves.
 *
 * <p>Dev only: the jar leaves this package and its template out. Layout of the playground (see
 * {@code dev/make_gametest_template.py}): 16x6x16, stone at template y 0-1, air above. Game tests place a template one
 * block above their origin, so in test coordinates the floor is y 1-2: tar goes in a trench at y 2 (it can't run), and
 * anything at y 3 straight above the trench touches it.
 */
@GameTestHolder(Tarbound.MODID)
@PrefixGameTestTemplate(false)
public final class TarboundPlaytests {
    private static final String PLAYGROUND = "gametest/playground";
    /** The tar trench: x 2..12 along z = 4, at y 2. */
    private static final int TRENCH_Z = 4;

    private TarboundPlaytests() {}

    // ------------------------------------------------------------------------------------------ building helpers

    private static void trench(GameTestHelper h, int x0, int x1) {
        for (int x = x0; x <= x1; x++) {
            h.setBlock(new BlockPos(x, 2, TRENCH_Z), ModBlocks.TAR.get());
        }
    }

    private static ScryingFontBlockEntity radio(GameTestHelper h, BlockPos rel) {
        h.setBlock(rel, ModBlocks.SCRYING_FONT.get());
        return h.getBlockEntity(rel);
    }

    private static Container chest(GameTestHelper h, BlockPos rel, ItemStack... items) {
        h.setBlock(rel, Blocks.CHEST);
        Container c = h.getBlockEntity(rel);
        for (int i = 0; i < items.length; i++) {
            c.setItem(i, items[i]);
        }
        return c;
    }

    private static Player player(GameTestHelper h, boolean sneaking) {
        Player p = h.makeMockPlayer(GameType.SURVIVAL);
        p.setShiftKeyDown(sneaking);
        return p;
    }

    /** Uses {@code stack} on the {@code face} of the block at {@code rel}, as a player would. @return the stack after */
    private static ItemStack useOn(GameTestHelper h, Player p, ItemStack stack, BlockPos rel, Direction face) {
        BlockPos abs = h.absolutePos(rel);
        p.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        stack.useOn(new UseOnContext(p, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, abs, false)));
        return p.getMainHandItem();
    }

    /** A stack of {@code item}, tuned to the radio at {@code radioRel} by shift-right-clicking it. */
    private static ItemStack tuned(GameTestHelper h, Item item, int count, BlockPos radioRel) {
        ItemStack s = useOn(h, player(h, true), new ItemStack(item, count), radioRel, Direction.UP);
        if (s.get(ModDataComponents.RADIO_LINK.get()) == null) {
            throw new IllegalStateException(item + " didn't take a tuning from the radio");
        }
        return s;
    }

    /** Places a tuned Reliquary against the {@code face} of the machine at {@code machineRel}. @return its position */
    private static BlockPos reliquaryOn(GameTestHelper h, BlockPos radioRel, BlockPos machineRel, Direction face) {
        ItemStack r = tuned(h, ModItems.RELIQUARY.get(), 1, radioRel);
        useOn(h, player(h, false), r, machineRel, face);
        BlockPos at = machineRel.relative(face);
        h.assertBlockPresent(ModBlocks.RELIQUARY.get(), at);
        return at;
    }

    /**
     * Whoever asks the radio for jobs: a NeoForge fake player (not in the player list, so mods that sync to every
     * player, like Create, leave it alone).
     */
    private static ServerPlayer requester(GameTestHelper h) {
        return net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),
                new com.mojang.authlib.GameProfile(java.util.UUID.fromString("7a6b0c1e-0000-4000-8000-00007a2b0e1d"), "[TarboundPlaytest]"));
    }

    private static ItemStack contractItem(ContractData data) {
        ItemStack s = new ItemStack(ModItems.SPIRIT_CONTRACT.get());
        s.set(ModDataComponents.CONTRACT.get(), data);
        return s;
    }

    private static ItemStack craftingContract(GameTestHelper h, String recipe) {
        RecipeHolder<CraftingRecipe> holder = CraftingContracts.recipe(h.getLevel(), ResourceLocation.withDefaultNamespace(recipe));
        if (holder == null) {
            throw new IllegalStateException("no crafting recipe " + recipe);
        }
        ItemStack s = contractItem(CraftingContracts.describe(holder.value(), h.getLevel().registryAccess()));
        s.set(ModDataComponents.CRAFTING_RECIPE.get(), holder.id());
        return s;
    }

    private static void install(GameTestHelper h, BlockPos reliquaryRel, ItemStack contract) {
        ReliquaryBlockEntity r = h.getBlockEntity(reliquaryRel);
        r.contractInventory().setStackInSlot(0, contract);
    }

    private static ContractData smelt(Item in, Item out) {
        return ContractData.of(List.of(List.of(new ItemStack(in))), List.of(new ItemStack(out)));
    }

    /** The radio's own menu, opened by a (fake) player, so jobs and orders go through the real request code. */
    private static ScryingFontMenu menu(GameTestHelper h, ServerPlayer sp, BlockPos radioRel) {
        BlockPos abs = h.absolutePos(radioRel);
        return new ScryingFontMenu(0, sp.getInventory(), ContainerLevelAccess.create(h.getLevel(), abs), abs);
    }

    private static long pool(ScryingFontBlockEntity font, Item item) {
        return font.network().countContents().getOrDefault(ItemKey.of(new ItemStack(item)), 0L);
    }

    /** Everything of {@code item} anywhere: the pool plus the given machines' slots (for conservation checks). */
    private static long everywhere(GameTestHelper h, ScryingFontBlockEntity font, Item item, BlockPos... machines) {
        long n = pool(font, item);
        for (BlockPos m : machines) {
            BlockEntity be = h.getBlockEntity(m);
            if (be instanceof Container c) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    if (c.getItem(i).is(item)) {
                        n += c.getItem(i).getCount();
                    }
                }
            }
        }
        return n;
    }

    private static void note(String test, String msg) {
        Tarbound.LOGGER.info("[playtest] {}: {}", test, msg);
    }

    private static String jobsLine(ScryingFontBlockEntity font) {
        StringBuilder b = new StringBuilder();
        for (ContractJob j : font.jobs()) {
            b.append(" #").append(j.id()).append(' ').append(j.contract().primaryOutput().getHoverName().getString()).append(' ')
                    .append(j.pushedOps()).append('/').append(j.totalOps()).append(" got ").append(j.received()).append('/')
                    .append(j.expected()).append(' ').append(j.status());
        }
        return b.length() == 0 ? " (none)" : b.toString();
    }

    // ------------------------------------------------------------------------------------------ 1. the pool

    /** Chests over tar join one pool; storing and taking go to and from them. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 100)
    public static void poolBasics(GameTestHelper h) {
        trench(h, 2, 12);
        ScryingFontBlockEntity font = radio(h, new BlockPos(2, 3, TRENCH_Z));
        Container a = chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.COBBLESTONE, 10), new ItemStack(Items.RAW_IRON, 5));
        Container b = chest(h, new BlockPos(9, 3, TRENCH_Z));
        h.runAfterDelay(5, () -> {
            TarNetwork net = font.network();
            h.assertTrue(pool(font, Items.COBBLESTONE) == 10, "pool should see 10 cobblestone, sees " + pool(font, Items.COBBLESTONE));
            ItemStack rest = net.insert(new ItemStack(Items.DIRT, 64));
            h.assertTrue(rest.isEmpty(), "64 dirt should fit, " + rest.getCount() + " left over");
            ItemStack got = net.extract(new ItemStack(Items.COBBLESTONE), 3);
            h.assertTrue(got.getCount() == 3, "should take 3 cobblestone, got " + got.getCount());
            int dirt = 0;
            for (Container c : new Container[] {a, b}) {
                for (int i = 0; i < c.getContainerSize(); i++) {
                    if (c.getItem(i).is(Items.DIRT)) {
                        dirt += c.getItem(i).getCount();
                    }
                }
            }
            h.assertTrue(dirt == 64, "the dirt should be in the chests, found " + dirt);
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------ 2. Spirit

    /** Candles left in storage are burned for Spirit when the radio runs low. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 200)
    public static void spiritFromStoredCandles(GameTestHelper h) {
        trench(h, 2, 8);
        ScryingFontBlockEntity font = radio(h, new BlockPos(2, 3, TRENCH_Z));
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.CANDLE, 3));
        h.succeedWhen(() -> {
            h.assertTrue(font.spirit() > 0, "no Spirit yet");
            h.assertTrue(pool(font, Items.CANDLE) < 3, "no candle burned");
            note("spiritFromStoredCandles", "spirit " + font.spirit() + ", candles left " + pool(font, Items.CANDLE));
        });
    }

    // ------------------------------------------------------------------------------------------ 3. a furnace job

    /**
     * Day one: a furnace with a Reliquary on it, a smelting contract, a job for 8 raw iron. The spirits keep the furnace
     * lit (no fuel anywhere), load it, drain the ingots back, and nothing is lost or made from nothing.
     */
    @GameTest(template = PLAYGROUND, timeoutTicks = 2600)
    public static void furnaceJob(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.CANDLE, 4));
        BlockPos furnace = new BlockPos(10, 3, 9);
        h.setBlock(furnace, Blocks.FURNACE);
        BlockPos rel = reliquaryOn(h, radioPos, furnace, Direction.UP);
        install(h, rel, contractItem(smelt(Items.RAW_IRON, Items.IRON_INGOT)));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> {
            menu(h, sp, radioPos).requestJob(sp, h.absolutePos(furnace), 0, 8);
            h.assertTrue(!font.jobs().isEmpty(), "the job request was refused (spirit " + font.spirit() + ")");
            note("furnaceJob", "job queued:" + jobsLine(font));
        });
        h.onEachTick(() -> {
            long ore = everywhere(h, font, Items.RAW_IRON, furnace);
            long ingots = everywhere(h, font, Items.IRON_INGOT, furnace);
            if (ore + ingots != 8) {
                h.fail("raw iron + ingots = " + (ore + ingots) + " (should stay 8)", furnace);
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.IRON_INGOT) == 8, "ingots in storage: " + pool(font, Items.IRON_INGOT) + ";" + jobsLine(font));
            h.assertTrue(font.jobs().isEmpty(), "job still listed:" + jobsLine(font));
            note("furnaceJob", "done at tick " + h.getTick() + ", spirit left " + font.spirit() + ", candles left " + pool(font, Items.CANDLE));
        });
    }

    // ------------------------------------------------------------------------------------------ 4. a crafting station

    /** A Reliquary on a crafting table crafts sticks from planks in storage. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 400)
    public static void craftingStation(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.OAK_PLANKS, 8), new ItemStack(Items.CANDLE, 2));
        BlockPos table = new BlockPos(10, 3, 9);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        BlockPos rel = reliquaryOn(h, radioPos, table, Direction.UP);
        install(h, rel, craftingContract(h, "stick"));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> {
            menu(h, sp, radioPos).requestJob(sp, h.absolutePos(table), 0, 4);
            h.assertTrue(!font.jobs().isEmpty(), "the stick job was refused");
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.STICK) == 16, "sticks: " + pool(font, Items.STICK) + ";" + jobsLine(font));
            h.assertTrue(pool(font, Items.OAK_PLANKS) == 0, "planks left: " + pool(font, Items.OAK_PLANKS));
            h.assertTrue(font.jobs().isEmpty(), "job still listed:" + jobsLine(font));
        });
    }

    // ------------------------------------------------------------------------------------------ 5. an order with a sub-craft

    /**
     * Order 16 stone bricks with only cobblestone in storage: the planner smelts the stone first (a sub-craft into the
     * order's Shade), then crafts the bricks. Needs a tuned Shade; checks nothing is lost on the way.
     */
    @GameTest(template = PLAYGROUND, timeoutTicks = 5200)
    public static void orderWithSubCraft(GameTestHelper h) {
        trench(h, 2, 10);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.COBBLESTONE, 16), new ItemStack(Items.CANDLE, 4));
        BlockPos furnace = new BlockPos(4, 3, 10);
        h.setBlock(furnace, Blocks.FURNACE);
        install(h, reliquaryOn(h, radioPos, furnace, Direction.UP), contractItem(smelt(Items.COBBLESTONE, Items.STONE)));
        BlockPos table = new BlockPos(10, 3, 10);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        install(h, reliquaryOn(h, radioPos, table, Direction.UP), craftingContract(h, "stone_bricks"));
        // A Shade, tuned and set down anywhere loaded.
        ItemStack shade = tuned(h, ModItems.SHADE.get(), 1, radioPos);
        useOn(h, player(h, false), shade, new BlockPos(7, 2, 12), Direction.UP);
        h.assertBlockPresent(ModBlocks.SHADE.get(), new BlockPos(7, 3, 12));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> {
            menu(h, sp, radioPos).orderPlan(sp, h.absolutePos(table), 0, 4);
            h.assertTrue(!font.orders().isEmpty(), "the order wasn't started:" + jobsLine(font));
            note("orderWithSubCraft", "order started:" + jobsLine(font));
        });
        h.onEachTick(() -> {
            if (h.getTick() % 400 == 0) {
                note("orderWithSubCraft", "t" + h.getTick() + jobsLine(font) + " | stone " + pool(font, Items.STONE) + " bricks " + pool(font, Items.STONE_BRICKS));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.STONE_BRICKS) == 16, "bricks: " + pool(font, Items.STONE_BRICKS) + ";" + jobsLine(font));
            h.assertTrue(font.orders().isEmpty() && font.jobs().isEmpty(), "order still open:" + jobsLine(font));
            h.assertTrue(pool(font, Items.COBBLESTONE) == 0 && pool(font, Items.STONE) == 0,
                    "leftovers: cobble " + pool(font, Items.COBBLESTONE) + ", stone " + pool(font, Items.STONE));
            note("orderWithSubCraft", "done at tick " + h.getTick());
        });
    }

    // ------------------------------------------------------------------------------------------ 6. antennas

    /** A tuned antenna on a chest away from the tar links it into the pool. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 100)
    public static void antennaLinksChest(GameTestHelper h) {
        trench(h, 2, 4);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        BlockPos far = new BlockPos(4, 3, 7);
        chest(h, far, new ItemStack(Items.APPLE, 7));
        ItemStack antenna = tuned(h, ModItems.TAR_ANTENNA.get(), 1, radioPos);
        useOn(h, player(h, false), antenna, far, Direction.UP);
        h.succeedWhen(() -> h.assertTrue(pool(font, Items.APPLE) == 7, "apples seen: " + pool(font, Items.APPLE)));
    }

    /** A Transient Antenna in push mode keeps feeding its filter item into a chest that isn't in the pool. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 200)
    public static void transientPush(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.COBBLESTONE, 32));
        BlockPos target = new BlockPos(10, 3, 10);
        Container out = chest(h, target);
        ItemStack ta = tuned(h, ModItems.TRANSIENT_ANTENNA.get(), 1, radioPos);
        useOn(h, player(h, false), ta, target, Direction.UP);
        BlockPos at = target.above();
        h.setBlock(at, h.getBlockState(at).setValue(com.tarbound.block.TransientAntennaBlock.MODE, com.tarbound.block.TransientAntennaBlock.Mode.PUSH));
        com.tarbound.block.TransientAntennaBlockEntity be = h.getBlockEntity(at);
        be.setFilter(new ItemStack(Items.COBBLESTONE));
        h.succeedWhen(() -> {
            int n = 0;
            for (int i = 0; i < out.getContainerSize(); i++) {
                n += out.getItem(i).is(Items.COBBLESTONE) ? out.getItem(i).getCount() : 0;
            }
            h.assertTrue(n == 32, "pushed " + n + " of 32");
            h.assertTrue(pool(font, Items.COBBLESTONE) == 0, "pool still has " + pool(font, Items.COBBLESTONE));
        });
    }

    // ------------------------------------------------------------------------------------------ 7. tar that swallows

    /** Items dropped into the radio's tar sink and end up in storage. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 200)
    public static void tarSwallowsDrops(GameTestHelper h) {
        trench(h, 2, 8);
        ScryingFontBlockEntity font = radio(h, new BlockPos(2, 3, TRENCH_Z));
        chest(h, new BlockPos(5, 3, TRENCH_Z));
        ItemEntity apples = h.spawnItem(Items.APPLE, new Vec3(7.5, 3.2, TRENCH_Z + 0.5));
        apples.getItem().setCount(5);
        h.onEachTick(() -> {
            if (h.getTick() % 40 == 0) {
                note("tarSwallowsDrops", "t" + h.getTick() + " apple at " + apples.position() + " alive " + apples.isAlive() + " inTar "
                        + apples.isInFluidType(com.tarbound.registry.ModFluids.TAR_TYPE.get()) + " data " + apples.getPersistentData()
                        + " pool " + pool(font, Items.APPLE));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.APPLE) == 5, "apples in storage: " + pool(font, Items.APPLE));
            h.assertTrue(!apples.isAlive(), "the apple entity is still there");
        });
    }

    /** A full pool spits items back up instead of deleting them. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 200)
    public static void tarSpitsWhenFull(GameTestHelper h) {
        trench(h, 2, 8);
        radio(h, new BlockPos(2, 3, TRENCH_Z));
        ItemStack[] full = new ItemStack[27];
        java.util.Arrays.fill(full, new ItemStack(Items.DIRT, 64));
        for (int i = 0; i < full.length; i++) {
            full[i] = new ItemStack(Items.DIRT, 64);
        }
        chest(h, new BlockPos(5, 3, TRENCH_Z), full);
        ItemEntity apples = h.spawnItem(Items.APPLE, new Vec3(7.5, 3.2, TRENCH_Z + 0.5));
        h.runAfterDelay(150, () -> {
            h.assertTrue(apples.isAlive() && apples.getItem().is(Items.APPLE), "the apple was lost with nowhere to put it");
            h.succeed();
        });
    }

    // ------------------------------------------------------------------------------------------ 8. Weeping Seep

    /** A Weeping Seep supplies cobblestone a job needs that storage hasn't got, for Spirit. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 1400)
    public static void weepingSeepFeedsJob(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.CANDLE, 2));
        h.setBlock(new BlockPos(7, 3, TRENCH_Z), ModBlocks.WEEPING_SEEP.get());
        BlockPos furnace = new BlockPos(10, 3, 9);
        h.setBlock(furnace, Blocks.FURNACE);
        install(h, reliquaryOn(h, radioPos, furnace, Direction.UP), contractItem(smelt(Items.COBBLESTONE, Items.STONE)));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> {
            menu(h, sp, radioPos).requestJob(sp, h.absolutePos(furnace), 0, 4);
            h.assertTrue(!font.jobs().isEmpty(), "job refused with a seep on the tar");
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.STONE) == 4, "stone: " + pool(font, Items.STONE) + ";" + jobsLine(font));
            h.assertTrue(pool(font, Items.COBBLESTONE) == 0, "seep cobblestone leaked into storage: " + pool(font, Items.COBBLESTONE));
        });
    }

    // ------------------------------------------------------------------------------------------ 9. save and load

    /** A radio with Spirit and a job, and a Reliquary with a contract, come back the same after saving. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 100)
    public static void saveRoundTrip(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        font.addSpirit(500);
        font.addJob(smelt(Items.RAW_GOLD, Items.GOLD_INGOT), 3);
        BlockPos furnace = new BlockPos(10, 3, 9);
        h.setBlock(furnace, Blocks.FURNACE);
        BlockPos rel = reliquaryOn(h, radioPos, furnace, Direction.UP);
        install(h, rel, contractItem(smelt(Items.RAW_GOLD, Items.GOLD_INGOT)));
        var reg = h.getLevel().registryAccess();
        var fontTag = font.saveWithFullMetadata(reg);
        ScryingFontBlockEntity copy = (ScryingFontBlockEntity) BlockEntity.loadStatic(font.getBlockPos(), font.getBlockState(), fontTag, reg);
        h.assertTrue(copy != null && copy.spirit() == font.spirit(), "Spirit didn't survive a save");
        h.assertTrue(copy.jobs().size() == 1 && copy.jobs().get(0).totalOps() == 3, "the job didn't survive a save");
        ReliquaryBlockEntity r = h.getBlockEntity(rel);
        ReliquaryBlockEntity rc = (ReliquaryBlockEntity) BlockEntity.loadStatic(r.getBlockPos(), r.getBlockState(), r.saveWithFullMetadata(reg), reg);
        h.assertTrue(rc != null && rc.contractSlots().get(0) != null && rc.contractSlots().get(0).sameAs(r.contractSlots().get(0)),
                "the Reliquary's contract didn't survive a save");
        h.assertTrue(rc.link() != null && rc.link().equals(r.link()), "the Reliquary forgot its radio");
        h.succeed();
    }

    // ------------------------------------------------------------------------------------------ 10. left alone for a while

    /**
     * The "leave it running" test: one base, three machines (two furnaces, a crafting station), one big order and a
     * plain job running at once for several in-game minutes. Checks every second that nothing is duplicated or lost,
     * and at the end that it all finished by itself.
     */
    @GameTest(template = PLAYGROUND, timeoutTicks = 9000)
    public static void leftAloneBase(GameTestHelper h) {
        trench(h, 2, 12);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.COBBLESTONE, 32), new ItemStack(Items.RAW_IRON, 12),
                new ItemStack(Items.CANDLE, 6));
        chest(h, new BlockPos(8, 3, TRENCH_Z));
        BlockPos furnaceA = new BlockPos(3, 3, 10);
        BlockPos furnaceB = new BlockPos(6, 3, 10);
        BlockPos table = new BlockPos(11, 3, 10);
        h.setBlock(furnaceA, Blocks.FURNACE);
        h.setBlock(furnaceB, Blocks.FURNACE);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        install(h, reliquaryOn(h, radioPos, furnaceA, Direction.UP), contractItem(smelt(Items.COBBLESTONE, Items.STONE)));
        install(h, reliquaryOn(h, radioPos, furnaceB, Direction.UP), contractItem(smelt(Items.RAW_IRON, Items.IRON_INGOT)));
        install(h, reliquaryOn(h, radioPos, table, Direction.UP), craftingContract(h, "stone_bricks"));
        ItemStack shade = tuned(h, ModItems.SHADE.get(), 1, radioPos);
        useOn(h, player(h, false), shade, new BlockPos(13, 2, 13), Direction.UP);
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> {
            ScryingFontMenu m = menu(h, sp, radioPos);
            m.orderPlan(sp, h.absolutePos(table), 0, 8); // 32 bricks, through 32 smelted stone
            m.requestJob(sp, h.absolutePos(furnaceB), 0, 12);
            note("leftAloneBase", "started:" + jobsLine(font));
            h.assertTrue(!font.orders().isEmpty(), "brick order refused");
        });
        BlockPos[] machines = {furnaceA, furnaceB};
        h.onEachTick(() -> {
            if (h.getTick() % 20 != 0) {
                return;
            }
            long stoneLine = everywhere(h, font, Items.COBBLESTONE, machines) + everywhere(h, font, Items.STONE, machines)
                    + shadeHeld(h, Items.COBBLESTONE) + shadeHeld(h, Items.STONE) + everywhere(h, font, Items.STONE_BRICKS, machines);
            long iron = everywhere(h, font, Items.RAW_IRON, machines) + everywhere(h, font, Items.IRON_INGOT, machines);
            // The crafting step takes 4 stone and makes 4 bricks at once, so the stone line stays 32 throughout.
            if (stoneLine != 32 || iron != 12) {
                h.fail("conservation broke at tick " + h.getTick() + ": stone line " + stoneLine + "/32, iron " + iron + "/12;" + jobsLine(font));
            }
            if (h.getTick() % 600 == 0) {
                note("leftAloneBase", "t" + h.getTick() + " spirit " + font.spirit() + jobsLine(font));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.STONE_BRICKS) == 32, "bricks " + pool(font, Items.STONE_BRICKS) + ";" + jobsLine(font));
            h.assertTrue(pool(font, Items.IRON_INGOT) == 12, "ingots " + pool(font, Items.IRON_INGOT) + ";" + jobsLine(font));
            h.assertTrue(font.jobs().isEmpty() && font.orders().isEmpty(), "still working:" + jobsLine(font));
            note("leftAloneBase", "all done at tick " + h.getTick() + ", spirit " + font.spirit() + ", candles " + pool(font, Items.CANDLE));
        });
    }

    private static long shadeHeld(GameTestHelper h, Item item) {
        long n = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(x, 3, z))) instanceof com.tarbound.block.ShadeBlockEntity s) {
                    n += s.held().getOrDefault(ItemKey.of(new ItemStack(item)), 0L);
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------------------------------ 13. more stations

    /** A Reliquary on a stonecutter cuts stone into slabs (one stone, two slabs) for a plain one-input contract. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 300)
    public static void stonecutterStation(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.STONE, 6), new ItemStack(Items.CANDLE, 1));
        BlockPos cutter = new BlockPos(10, 3, 9);
        h.setBlock(cutter, Blocks.STONECUTTER);
        BlockPos rel = reliquaryOn(h, radioPos, cutter, Direction.UP);
        install(h, rel, contractItem(ContractData.of(List.of(List.of(new ItemStack(Items.STONE))), List.of(new ItemStack(Items.STONE_SLAB, 2)))));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> {
            menu(h, sp, radioPos).requestJob(sp, h.absolutePos(cutter), 0, 6);
            h.assertTrue(!font.jobs().isEmpty(), "the stonecutter job was refused");
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.STONE_SLAB) == 12, "slabs: " + pool(font, Items.STONE_SLAB) + ";" + jobsLine(font));
            h.assertTrue(pool(font, Items.STONE) == 0, "stone left: " + pool(font, Items.STONE));
            h.assertTrue(font.jobs().isEmpty(), "job still listed:" + jobsLine(font));
        });
    }

    /** An order through a furnace and then a stonecutter: cobblestone to stone bricks with no crafting table. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 2400)
    public static void orderThroughStonecutter(GameTestHelper h) {
        trench(h, 2, 10);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.COBBLESTONE, 6), new ItemStack(Items.CANDLE, 2));
        BlockPos furnace = new BlockPos(4, 3, 10);
        h.setBlock(furnace, Blocks.FURNACE);
        install(h, reliquaryOn(h, radioPos, furnace, Direction.UP), contractItem(smelt(Items.COBBLESTONE, Items.STONE)));
        BlockPos cutter = new BlockPos(10, 3, 10);
        h.setBlock(cutter, Blocks.STONECUTTER);
        install(h, reliquaryOn(h, radioPos, cutter, Direction.UP),
                contractItem(ContractData.of(List.of(List.of(new ItemStack(Items.STONE))), List.of(new ItemStack(Items.STONE_BRICKS)))));
        ItemStack shade = tuned(h, ModItems.SHADE.get(), 1, radioPos);
        useOn(h, player(h, false), shade, new BlockPos(7, 2, 12), Direction.UP);
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> {
            menu(h, sp, radioPos).orderPlan(sp, h.absolutePos(cutter), 0, 6);
            h.assertTrue(!font.orders().isEmpty(), "the order wasn't started:" + jobsLine(font));
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.STONE_BRICKS) == 6, "bricks: " + pool(font, Items.STONE_BRICKS) + ";" + jobsLine(font));
            h.assertTrue(font.orders().isEmpty() && font.jobs().isEmpty(), "order still open:" + jobsLine(font));
        });
    }

    // ------------------------------------------------------------------------------------------ Steward

    /** Sets a tuned Steward down on the floor block at {@code floorRel} (it stands on top) and gives it a list. */
    private static com.tarbound.entity.Steward steward(GameTestHelper h, BlockPos radioRel, BlockPos floorRel,
            com.tarbound.entity.StockTarget... list) {
        ItemStack item = tuned(h, ModItems.STEWARD.get(), 1, radioRel);
        item.set(ModDataComponents.STEWARD_TARGETS.get(), List.of(list));
        ItemStack left = useOn(h, player(h, false), item, floorRel, Direction.UP);
        h.assertTrue(left.isEmpty(), "the Steward wasn't set down");
        var found = h.getLevel().getEntitiesOfClass(com.tarbound.entity.Steward.class,
                new net.minecraft.world.phys.AABB(h.absolutePos(floorRel.above())).inflate(1));
        h.assertTrue(found.size() == 1, "Stewards found: " + found.size());
        return found.get(0);
    }

    /** A Steward keeping 10 sticks, with a crafting station that makes them: one job of 3 crafts, then it rests. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 600)
    public static void stewardKeepsStock(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.OAK_PLANKS, 8), new ItemStack(Items.CANDLE, 2));
        BlockPos table = new BlockPos(10, 3, 9);
        h.setBlock(table, Blocks.CRAFTING_TABLE);
        install(h, reliquaryOn(h, radioPos, table, Direction.UP), craftingContract(h, "stick"));
        var steward = steward(h, radioPos, new BlockPos(7, 2, 12),
                new com.tarbound.entity.StockTarget(new ItemStack(Items.STICK), 10),
                new com.tarbound.entity.StockTarget(new ItemStack(Items.DIAMOND), 1));
        h.runAfterDelay(200, () -> {
            h.assertTrue(pool(font, Items.STICK) == 12, "sticks: " + pool(font, Items.STICK) + ";" + jobsLine(font));
            h.assertTrue(pool(font, Items.OAK_PLANKS) == 2, "planks: " + pool(font, Items.OAK_PLANKS));
            h.assertTrue(font.jobs().isEmpty(), "still working:" + jobsLine(font));
            h.assertTrue(steward.status(0) == com.tarbound.entity.Steward.Status.STOCKED, "sticks row: " + steward.status(0));
            h.assertTrue(steward.status(1) == com.tarbound.entity.Steward.Status.NO_CONTRACT, "diamond row: " + steward.status(1));
            // Take some away: it tops them up again.
            font.network().extract(new ItemStack(Items.STICK), 6);
        });
        h.runAfterDelay(400, () -> {
            h.assertTrue(pool(font, Items.STICK) == 10, "sticks after topping up: " + pool(font, Items.STICK) + ";" + jobsLine(font));
            h.assertTrue(pool(font, Items.OAK_PLANKS) == 0, "planks: " + pool(font, Items.OAK_PLANKS));
            h.succeed();
        });
    }

    /** No Shade anywhere: a Steward keeping stone bricks watches the furnace-then-stonecutter order itself. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 2400)
    public static void stewardWatchesItsOrder(GameTestHelper h) {
        trench(h, 2, 10);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.COBBLESTONE, 6), new ItemStack(Items.CANDLE, 2));
        BlockPos furnace = new BlockPos(4, 3, 10);
        h.setBlock(furnace, Blocks.FURNACE);
        install(h, reliquaryOn(h, radioPos, furnace, Direction.UP), contractItem(smelt(Items.COBBLESTONE, Items.STONE)));
        BlockPos cutter = new BlockPos(10, 3, 10);
        h.setBlock(cutter, Blocks.STONECUTTER);
        install(h, reliquaryOn(h, radioPos, cutter, Direction.UP),
                contractItem(ContractData.of(List.of(List.of(new ItemStack(Items.STONE))), List.of(new ItemStack(Items.STONE_BRICKS)))));
        var steward = steward(h, radioPos, new BlockPos(7, 2, 12), new com.tarbound.entity.StockTarget(new ItemStack(Items.STONE_BRICKS), 4));
        boolean[] sawOrder = {false};
        h.onEachTick(() -> {
            if (!font.orders().isEmpty()) {
                sawOrder[0] = true;
                h.assertTrue(font.orders().get(0).vesselEntity() != null && steward.orderId() == font.orders().get(0).id(),
                        "the order isn't the Steward's");
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(sawOrder[0], "no order was started;" + jobsLine(font));
            h.assertTrue(pool(font, Items.STONE_BRICKS) == 4, "bricks: " + pool(font, Items.STONE_BRICKS) + ";" + jobsLine(font));
            h.assertTrue(font.orders().isEmpty() && font.jobs().isEmpty(), "order still open:" + jobsLine(font));
            h.assertTrue(steward.orderId() == 0 && steward.held().isEmpty(), "the Steward still holds " + steward.held());
            h.assertTrue(pool(font, Items.COBBLESTONE) == 2 && pool(font, Items.STONE) == 0,
                    "cobble " + pool(font, Items.COBBLESTONE) + ", stone " + pool(font, Items.STONE));
        });
    }

    /** Shift-right-click with an empty hand: the Steward comes back as an item with its tuning and list. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 60)
    public static void stewardPickedUp(GameTestHelper h) {
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        radio(h, radioPos);
        var steward = steward(h, radioPos, new BlockPos(7, 2, 12), new com.tarbound.entity.StockTarget(new ItemStack(Items.TORCH), 32));
        steward.edit(5, new ItemStack(Items.LANTERN), 3);
        h.assertTrue(steward.targets().size() == 2, "edit past the end should add a row");
        steward.edit(0, new ItemStack(Items.LANTERN), 7);
        h.assertTrue(steward.targets().size() == 2 && steward.targets().get(1).keep() == 7, "an item already listed keeps its row");
        // Hurting it does nothing.
        h.assertFalse(steward.hurt(h.getLevel().damageSources().generic(), 100), "it took damage");
        ServerPlayer sp = requester(h);
        sp.getInventory().clearContent();
        sp.setShiftKeyDown(true);
        sp.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        steward.interact(sp, InteractionHand.MAIN_HAND);
        h.assertTrue(steward.isRemoved(), "still standing");
        ItemStack got = ItemStack.EMPTY;
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            if (sp.getInventory().getItem(i).is(ModItems.STEWARD.get())) {
                got = sp.getInventory().getItem(i);
            }
        }
        h.assertTrue(!got.isEmpty(), "no Steward item");
        h.assertTrue(got.get(ModDataComponents.RADIO_LINK.get()) != null, "lost its tuning");
        var list = got.getOrDefault(ModDataComponents.STEWARD_TARGETS.get(), List.<com.tarbound.entity.StockTarget>of());
        h.assertTrue(list.size() == 2 && list.get(0).item().is(Items.TORCH) && list.get(0).keep() == 32, "lost its list: " + list);
        sp.setShiftKeyDown(false);
        sp.getInventory().clearContent();
        h.succeed();
    }

    /** A Reliquary on a composter, no contract: it composts the seeds in storage and brings the bone meal back. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 1200)
    public static void composterStation(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.WHEAT_SEEDS, 64), new ItemStack(Items.WHEAT_SEEDS, 64), new ItemStack(Items.OAK_LOG, 8), new ItemStack(Items.CANDLE, 1));
        BlockPos composter = new BlockPos(10, 3, 9);
        h.setBlock(composter, Blocks.COMPOSTER);
        reliquaryOn(h, radioPos, composter, Direction.UP);
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.BONE_MEAL) >= 2, "bone meal: " + pool(font, Items.BONE_MEAL) + ", seeds left " + pool(font, Items.WHEAT_SEEDS));
            h.assertTrue(pool(font, Items.OAK_LOG) == 8, "it composted logs that aren't fodder");
            note("composterStation", "t" + h.getTick() + " bone meal " + pool(font, Items.BONE_MEAL) + ", seeds left " + pool(font, Items.WHEAT_SEEDS));
        });
    }

    /** A job short of an input says which one and how many, and points at the job that's making it. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 200)
    public static void jobSaysWhatItWaitsFor(GameTestHelper h) {
        trench(h, 2, 10);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.CANDLE, 2));
        BlockPos cutter = new BlockPos(10, 3, 10);
        h.setBlock(cutter, Blocks.STONECUTTER);
        install(h, reliquaryOn(h, radioPos, cutter, Direction.UP),
                contractItem(ContractData.of(List.of(List.of(new ItemStack(Items.STONE))), List.of(new ItemStack(Items.STONE_BRICKS)))));
        BlockPos furnace = new BlockPos(4, 3, 10);
        h.setBlock(furnace, Blocks.FURNACE);
        install(h, reliquaryOn(h, radioPos, furnace, Direction.UP), contractItem(smelt(Items.COBBLESTONE, Items.STONE)));
        ContractJob[] bricks = new ContractJob[1];
        ContractJob[] stone = new ContractJob[1];
        h.runAfterDelay(5, () -> {
            bricks[0] = font.addJob(ContractData.of(List.of(List.of(new ItemStack(Items.STONE))), List.of(new ItemStack(Items.STONE_BRICKS))), 4);
            stone[0] = font.addJob(smelt(Items.COBBLESTONE, Items.STONE), 4); // no cobblestone either: both wait
        });
        h.succeedWhen(() -> {
            h.assertTrue(bricks[0] != null && bricks[0].status() == ContractJob.Status.WAITING_INPUTS, "bricks job:" + jobsLine(font));
            h.assertTrue(bricks[0].waitItem().is(Items.STONE) && bricks[0].waitItem().getCount() == 1, "bricks job waits for " + bricks[0].waitItem());
            h.assertTrue(bricks[0].waitFeeder() == stone[0].id(), "bricks job should point at the stone job, points at " + bricks[0].waitFeeder());
            h.assertTrue(stone[0].waitItem().is(Items.COBBLESTONE) && stone[0].waitFeeder() == 0, "stone job waits for " + stone[0].waitItem());
        });
    }

    // ------------------------------------------------------------------------------------------ 14. Create

    /**
     * Create's Mechanical Crafter (only with Create installed): a 3x5 crafter grid, a Reliquary on one crafter, an
     * Extendo Grip contract (a pattern with gaps and no mirroring). Tarbound lays the pattern onto the grid, Create
     * crafts, and the grips come out of the last crafter into a chest the radio sees.
     */
    @GameTest(template = PLAYGROUND, timeoutTicks = 2400)
    public static void createCrafterGrid(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) {
            h.succeed(); // nothing to test without Create
            return;
        }
        var reg = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        net.minecraft.world.level.block.Block crafterBlock = reg.get(ResourceLocation.fromNamespaceAndPath("create", "mechanical_crafter"));
        net.minecraft.world.level.block.Block motor = reg.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        Item hand = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "brass_hand"));
        Item mech = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "precision_mechanism"));
        Item brass = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "brass_ingot"));
        Item grip = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("create", "extendo_grip"));

        for (int x = 2; x <= 5; x++) {
            h.setBlock(new BlockPos(x, 2, 10), ModBlocks.TAR.get()); // tar along the grid's row, under radio, store and output
        }
        BlockPos radioPos = new BlockPos(2, 3, 10);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(3, 3, 10), new ItemStack(hand, 2), new ItemStack(mech, 2), new ItemStack(brass, 2),
                new ItemStack(Items.STICK, 12), new ItemStack(Items.CANDLE, 2));

        // The grid: x 6..8, y 3..7, z 10, facing south (fronts toward +z). Columns hand down; the bottom row hands west
        // to the last crafter at x 6, which puts the grip into a chest at x 5.
        Direction facing = Direction.SOUTH;
        net.minecraft.world.level.block.state.BlockState base = crafterBlock.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, facing);
        String down = pointingToward(base, Direction.DOWN);
        String west = pointingToward(base, Direction.WEST);
        h.assertTrue(down != null && west != null, "couldn't work out the crafter's pointing values");
        for (int x = 6; x <= 8; x++) {
            for (int y = 3; y <= 7; y++) {
                h.setBlock(new BlockPos(x, y, 10), withPointing(base, y == 3 ? west : down));
            }
        }
        // Crafters turn like small cogs: a cogwheel beside the grid (same axis), driven by a creative motor behind it.
        net.minecraft.world.level.block.Block cog = reg.get(ResourceLocation.fromNamespaceAndPath("create", "cogwheel"));
        h.setBlock(new BlockPos(9, 3, 10), cog.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                net.minecraft.core.Direction.Axis.Z));
        h.setBlock(new BlockPos(9, 3, 9), motor.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.SOUTH));
        BlockPos out = new BlockPos(5, 3, 10);
        chest(h, out); // over the tar: the grips land straight in storage

        BlockPos crafter = new BlockPos(7, 5, 10);
        BlockPos rel = reliquaryOn(h, radioPos, crafter, Direction.NORTH);
        List<List<ItemStack>> ins = List.of(List.of(new ItemStack(brass)), List.of(new ItemStack(mech)), List.of(new ItemStack(Items.STICK, 6)),
                List.of(new ItemStack(hand)));
        install(h, rel, contractItem(ContractData.of(ins, List.of(new ItemStack(grip)))));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(20, () -> {
            menu(h, sp, radioPos).requestJob(sp, h.absolutePos(crafter), 0, 2);
            h.assertTrue(!font.jobs().isEmpty(), "the crafter job was refused:" + jobsLine(font));
        });
        h.onEachTick(() -> {
            if (h.getTick() % 100 == 0) {
                ContractJob j = font.jobs().isEmpty() ? null : font.jobs().get(0);
                note("createCrafterGrid", "t" + h.getTick() + jobsLine(font) + (j != null && j.refusal() != null ? " refusal: " + j.refusal().getString() : "")
                        + " grips " + pool(font, grip));
                StringBuilder sp2 = new StringBuilder();
                for (BlockPos p : List.of(new BlockPos(9, 3, 9), new BlockPos(9, 3, 10), new BlockPos(7, 3, 10), new BlockPos(7, 5, 10))) {
                    BlockEntity be = h.getLevel().getBlockEntity(h.absolutePos(p));
                    try {
                        sp2.append(p.toShortString()).append('=').append(be == null ? "none" : be.getClass().getMethod("getSpeed").invoke(be)).append(' ');
                    } catch (ReflectiveOperationException e) {
                        sp2.append(p.toShortString()).append("=? ");
                    }
                }
                Container oc = h.getBlockEntity(out);
                StringBuilder inv = new StringBuilder();
                for (int i = 0; i < oc.getContainerSize(); i++) {
                    if (!oc.getItem(i).isEmpty()) {
                        inv.append(oc.getItem(i)).append(' ');
                    }
                }
                StringBuilder ents = new StringBuilder();
                for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(0, 0, 0))).inflate(20))) {
                    ents.append(e.getItem()).append('@').append(h.relativePos(e.blockPosition()).toShortString()).append(' ');
                }
                note("createCrafterGrid", "speeds " + sp2 + "| out chest: " + inv + "| loose items: " + ents + "| sticks " + pool(font, Items.STICK));
            }
        });
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, grip) == 2, "grips: " + pool(font, grip) + ";" + jobsLine(font));
            h.assertTrue(pool(font, Items.STICK) == 0 && pool(font, brass) == 0, "leftovers: sticks " + pool(font, Items.STICK));
        });
    }

    /**
     * Handedness: a lopsided pattern that may not be mirrored (a dev-only recipe, dirt+cobble over sand -> sponge) on a
     * 2x2 grid with one crafter left empty. Laid out the wrong way round, Create would throw the items out instead.
     */
    @GameTest(template = PLAYGROUND, timeoutTicks = 1200)
    public static void createCrafterHandedness(GameTestHelper h) {
        if (!net.neoforged.fml.ModList.get().isLoaded("create")) {
            h.succeed();
            return;
        }
        var reg = net.minecraft.core.registries.BuiltInRegistries.BLOCK;
        net.minecraft.world.level.block.Block crafterBlock = reg.get(ResourceLocation.fromNamespaceAndPath("create", "mechanical_crafter"));
        net.minecraft.world.level.block.Block motor = reg.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        net.minecraft.world.level.block.Block cog = reg.get(ResourceLocation.fromNamespaceAndPath("create", "cogwheel"));
        for (int x = 2; x <= 5; x++) {
            h.setBlock(new BlockPos(x, 2, 10), ModBlocks.TAR.get());
        }
        BlockPos radioPos = new BlockPos(2, 3, 10);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(3, 3, 10), new ItemStack(Items.DIRT, 3), new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.SAND, 3),
                new ItemStack(Items.CANDLE, 1));
        // Facing north this time (the other handedness from the grip test). Column x 6..7, y 3..4; the bottom row hands
        // west into the last crafter at x 6, which puts the sponge into the chest at x 5.
        net.minecraft.world.level.block.state.BlockState base = crafterBlock.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        String down = pointingToward(base, Direction.DOWN);
        String west = pointingToward(base, Direction.WEST);
        for (int x = 6; x <= 7; x++) {
            for (int y = 3; y <= 4; y++) {
                h.setBlock(new BlockPos(x, y, 10), withPointing(base, y == 3 ? west : down));
            }
        }
        h.setBlock(new BlockPos(8, 3, 10), cog.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,
                net.minecraft.core.Direction.Axis.Z));
        h.setBlock(new BlockPos(8, 3, 11), motor.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, Direction.NORTH));
        chest(h, new BlockPos(5, 3, 10));
        BlockPos crafter = new BlockPos(7, 4, 10);
        BlockPos rel = reliquaryOn(h, radioPos, crafter, Direction.UP);
        List<List<ItemStack>> ins = List.of(List.of(new ItemStack(Items.DIRT)), List.of(new ItemStack(Items.COBBLESTONE)), List.of(new ItemStack(Items.SAND)));
        install(h, rel, contractItem(ContractData.of(ins, List.of(new ItemStack(Items.SPONGE)))));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(20, () -> menu(h, sp, radioPos).requestJob(sp, h.absolutePos(crafter), 0, 3));
        h.failIfEver(() -> {
            for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(7, 3, 10))).inflate(4))) {
                h.fail("the grid threw " + e.getItem() + " out: the pattern was laid the wrong way round");
            }
        });
        h.succeedWhen(() -> h.assertTrue(pool(font, Items.SPONGE) == 3, "sponges: " + pool(font, Items.SPONGE) + ";" + jobsLine(font)
                + (font.jobs().isEmpty() || font.jobs().get(0).refusal() == null ? "" : " refusal: " + font.jobs().get(0).refusal().getString())));
    }

    /**
     * A big mechanical crafting pattern from JEI/EMI arrives one ghost per grid cell (21 for a crushing wheel); the
     * lectern folds it into one input per ingredient with a count, instead of keeping the first 9 cells.
     */
    @GameTest(template = PLAYGROUND, timeoutTicks = 20)
    public static void bigPatternsFoldIntoContracts(GameTestHelper h) {
        List<com.tarbound.contract.ContractDraft.Ghost> cells = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            cells.add(com.tarbound.contract.ContractDraft.Ghost.of(new ItemStack(Items.ANDESITE)));
        }
        for (int i = 0; i < 4; i++) {
            cells.add(com.tarbound.contract.ContractDraft.Ghost.of(new ItemStack(Items.OAK_PLANKS)));
        }
        cells.add(com.tarbound.contract.ContractDraft.Ghost.of(new ItemStack(Items.STONE)));
        var fitted = com.tarbound.contract.ContractDraft.fitInputs(cells);
        h.assertTrue(fitted != null && fitted.size() == 3, "folded into " + (fitted == null ? "nothing" : fitted.size() + " inputs"));
        h.assertTrue(fitted.get(0).item().is(Items.ANDESITE) && fitted.get(0).item().getCount() == 16, "andesite: " + fitted.get(0).item());
        h.assertTrue(fitted.get(1).item().getCount() == 4 && fitted.get(2).item().getCount() == 1, "planks/stone counts wrong");
        List<com.tarbound.contract.ContractDraft.Ghost> small = cells.subList(0, 5);
        h.assertTrue(com.tarbound.contract.ContractDraft.fitInputs(small) == small, "a recipe that fits should be left slot by slot");
        List<com.tarbound.contract.ContractDraft.Ghost> many = new ArrayList<>();
        Item[] kinds = {Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.CLAY_BALL, Items.FLINT, Items.STICK, Items.APPLE, Items.BONE, Items.STRING};
        for (Item k : kinds) {
            many.add(com.tarbound.contract.ContractDraft.Ghost.of(new ItemStack(k)));
            many.add(com.tarbound.contract.ContractDraft.Ghost.of(new ItemStack(k)));
        }
        h.assertTrue(com.tarbound.contract.ContractDraft.fitInputs(many) == null, "10 different ingredients can't fit 9 inputs");
        h.succeed();
    }

    /** The crafter "pointing" value whose target is {@code dir} for this facing, by asking Create. */
    @javax.annotation.Nullable
    private static String pointingToward(net.minecraft.world.level.block.state.BlockState base, Direction dir) {
        try {
            var prop = base.getBlock().getStateDefinition().getProperty("pointing");
            var m = Class.forName("com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlock").getMethod("getTargetDirection",
                    net.minecraft.world.level.block.state.BlockState.class);
            for (String v : List.of("up", "down", "left", "right")) {
                if (m.invoke(null, withPointing(base, v)) == dir) {
                    return v;
                }
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
        return null;
    }

    private static net.minecraft.world.level.block.state.BlockState withPointing(net.minecraft.world.level.block.state.BlockState base, String v) {
        var prop = base.getBlock().getStateDefinition().getProperty("pointing");
        return set(base, prop, v);
    }

    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState set(net.minecraft.world.level.block.state.BlockState s,
            net.minecraft.world.level.block.state.properties.Property<T> p, String v) {
        return p.getValue(v).map(val -> s.setValue(p, val)).orElse(s);
    }

    // ------------------------------------------------------------------------------------------ 12. things going wrong

    /** Out of Spirit: the job waits (HUNGRY) without touching anything, then finishes once a candle turns up. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 1400)
    public static void hungryThenFed(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        Container store = chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.RAW_COPPER, 4));
        BlockPos furnace = new BlockPos(10, 3, 9);
        h.setBlock(furnace, Blocks.FURNACE);
        install(h, reliquaryOn(h, radioPos, furnace, Direction.UP), contractItem(smelt(Items.RAW_COPPER, Items.COPPER_INGOT)));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> {
            menu(h, sp, radioPos).requestJob(sp, h.absolutePos(furnace), 0, 4);
            note("hungryThenFed", "after request:" + jobsLine(font));
        });
        h.runAfterDelay(120, () -> {
            note("hungryThenFed", "t120:" + jobsLine(font) + " spirit " + font.spirit());
            h.assertTrue(pool(font, Items.RAW_COPPER) == 4, "a starved radio still loaded the furnace");
            store.setItem(5, new ItemStack(Items.CANDLE));
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 120, "too early");
            h.assertTrue(pool(font, Items.COPPER_INGOT) == 4, "copper ingots: " + pool(font, Items.COPPER_INGOT) + ";" + jobsLine(font));
        });
    }

    /** Storage fills up mid-job: ingots wait in the furnace (never deleted, never dropped), and drain once there's room. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 2400)
    public static void storageFullThenFreed(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        ItemStack[] stuff = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            stuff[i] = new ItemStack(Items.DIRT, 64);
        }
        stuff[0] = new ItemStack(Items.RAW_IRON, 4);
        stuff[1] = new ItemStack(Items.CANDLE, 2);
        Container store = chest(h, new BlockPos(5, 3, TRENCH_Z), stuff);
        BlockPos furnace = new BlockPos(10, 3, 9);
        h.setBlock(furnace, Blocks.FURNACE);
        install(h, reliquaryOn(h, radioPos, furnace, Direction.UP), contractItem(smelt(Items.RAW_IRON, Items.IRON_INGOT)));
        ServerPlayer sp = requester(h);
        boolean[] filled = {false};
        h.runAfterDelay(5, () -> menu(h, sp, radioPos).requestJob(sp, h.absolutePos(furnace), 0, 4));
        h.onEachTick(() -> {
            // Once the raw iron and candles have left the chest, plug the gaps so the ingots have nowhere to go.
            if (!filled[0] && store.getItem(0).isEmpty() && store.getItem(1).isEmpty()) {
                store.setItem(0, new ItemStack(Items.DIRT, 64));
                store.setItem(1, new ItemStack(Items.DIRT, 64));
                filled[0] = true;
                note("storageFullThenFreed", "storage plugged at t" + h.getTick());
            }
            long ingots = everywhere(h, font, Items.IRON_INGOT, furnace) + everywhere(h, font, Items.RAW_IRON, furnace);
            if (ingots != 4) {
                h.fail("iron went missing or doubled: " + ingots + " of 4");
            }
        });
        h.runAfterDelay(1300, () -> {
            h.assertTrue(filled[0], "never got to plug the chest");
            Container f = h.getBlockEntity(furnace);
            note("storageFullThenFreed", "t1300 furnace out " + f.getItem(2) + ", pool ingots " + pool(font, Items.IRON_INGOT) + jobsLine(font));
            h.assertTrue(pool(font, Items.IRON_INGOT) == 0, "ingots got into a full chest?");
            store.setItem(0, ItemStack.EMPTY); // make room
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 1300, "too early");
            h.assertTrue(pool(font, Items.IRON_INGOT) == 4, "ingots: " + pool(font, Items.IRON_INGOT) + ";" + jobsLine(font));
        });
    }

    /** The Reliquary is broken mid-job: the job waits; a new Reliquary with the same contract picks it back up. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 3000)
    public static void reliquaryBrokenMidJob(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.RAW_GOLD, 6), new ItemStack(Items.CANDLE, 3));
        BlockPos furnace = new BlockPos(10, 3, 9);
        h.setBlock(furnace, Blocks.FURNACE);
        BlockPos rel = reliquaryOn(h, radioPos, furnace, Direction.UP);
        install(h, rel, contractItem(smelt(Items.RAW_GOLD, Items.GOLD_INGOT)));
        ServerPlayer sp = requester(h);
        h.runAfterDelay(5, () -> menu(h, sp, radioPos).requestJob(sp, h.absolutePos(furnace), 0, 6));
        h.runAfterDelay(300, () -> {
            h.getLevel().destroyBlock(h.absolutePos(rel), true);
            note("reliquaryBrokenMidJob", "broken at t300:" + jobsLine(font));
        });
        h.runAfterDelay(400, () -> {
            note("reliquaryBrokenMidJob", "t400 (no Reliquary):" + jobsLine(font));
            BlockPos again = reliquaryOn(h, radioPos, furnace, Direction.UP);
            install(h, again, contractItem(smelt(Items.RAW_GOLD, Items.GOLD_INGOT)));
        });
        h.succeedWhen(() -> {
            h.assertTrue(h.getTick() > 400, "too early");
            long gold = everywhere(h, font, Items.GOLD_INGOT, furnace);
            long raw = everywhere(h, font, Items.RAW_GOLD, furnace);
            h.assertTrue(gold + raw == 6, "gold went missing: " + gold + " ingots + " + raw + " raw");
            h.assertTrue(pool(font, Items.GOLD_INGOT) == 6, "ingots: " + pool(font, Items.GOLD_INGOT) + ";" + jobsLine(font));
            h.assertTrue(font.jobs().isEmpty(), "job still listed:" + jobsLine(font));
        });
    }

    /** A standing contract keeps its furnace fed from everything in a tag (raw ores), with no job asked for. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 2400)
    public static void standingContract(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(5, 3, TRENCH_Z), new ItemStack(Items.RAW_IRON, 3), new ItemStack(Items.RAW_GOLD, 3), new ItemStack(Items.CANDLE, 2));
        BlockPos furnace = new BlockPos(10, 3, 9);
        h.setBlock(furnace, Blocks.FURNACE);
        ItemStack standing = new ItemStack(ModItems.SPIRIT_CONTRACT.get());
        standing.set(ModDataComponents.STANDING.get(), new com.tarbound.contract.StandingOrder(net.neoforged.neoforge.common.Tags.Items.RAW_MATERIALS, 1));
        install(h, reliquaryOn(h, radioPos, furnace, Direction.UP), standing);
        h.succeedWhen(() -> {
            h.assertTrue(pool(font, Items.IRON_INGOT) == 3 && pool(font, Items.GOLD_INGOT) == 3,
                    "iron " + pool(font, Items.IRON_INGOT) + " gold " + pool(font, Items.GOLD_INGOT) + " raw left "
                            + (pool(font, Items.RAW_IRON) + pool(font, Items.RAW_GOLD)));
        });
    }

    /** A gold nugget wakes the Reliquary's hoard: dropped items nearby are pinned, and tar leaves pinned items alone. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 300)
    public static void hoardPinsAndTarRespectsIt(GameTestHelper h) {
        trench(h, 2, 8);
        BlockPos radioPos = new BlockPos(2, 3, TRENCH_Z);
        ScryingFontBlockEntity font = radio(h, radioPos);
        chest(h, new BlockPos(3, 3, TRENCH_Z));
        BlockPos furnace = new BlockPos(7, 3, 6);
        h.setBlock(furnace, Blocks.FURNACE);
        BlockPos rel = reliquaryOn(h, radioPos, furnace, Direction.UP);
        Player p = player(h, false);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GOLD_NUGGET, 2));
        h.useBlock(rel, p);
        ReliquaryBlockEntity r = h.getBlockEntity(rel);
        h.assertTrue(r.antiMagnet(), "a gold nugget didn't wake the hoard");
        ItemEntity pinned = h.spawnItem(Items.EMERALD, new Vec3(7.5, 3.2, TRENCH_Z + 0.5)); // in the tar, inside the ward
        h.runAfterDelay(150, () -> {
            h.assertTrue(pinned.getPersistentData().getBoolean("PreventRemoteMovement"), "the emerald isn't pinned");
            h.assertTrue(pinned.isAlive() && pool(font, Items.EMERALD) == 0, "tar swallowed an item the hoard was guarding");
            h.succeed();
        });
    }

    /** A lit Votive Rack beside the radio raises its job limit. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 100)
    public static void votiveRackRaisesLimit(GameTestHelper h) {
        trench(h, 2, 8);
        ScryingFontBlockEntity font = radio(h, new BlockPos(2, 3, TRENCH_Z));
        int before = font.jobLimit();
        h.setBlock(new BlockPos(2, 3, TRENCH_Z + 2), ModBlocks.VOTIVE_RACK.get().defaultBlockState().setValue(com.tarbound.block.VotiveRackBlock.LIT, true));
        h.succeedWhen(() -> h.assertTrue(font.racks() == 1 && font.jobLimit() > before,
                "racks " + font.racks() + ", limit " + font.jobLimit() + " (was " + before + ")"));
    }

    /** Day one, in survival: the tar bucket pours, and 8 soul sand + flint and steel really craft a Weakened Soul. */
    @GameTest(template = PLAYGROUND, timeoutTicks = 60)
    public static void dayOneRecipes(GameTestHelper h) {
        var level = h.getLevel();
        List<ItemStack> grid = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            grid.add(new ItemStack(Items.SOUL_SAND));
        }
        grid.add(new ItemStack(Items.FLINT_AND_STEEL));
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, grid);
        var soul = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
        h.assertTrue(soul.isPresent() && soul.get().value().assemble(input, level.registryAccess()).is(ModItems.WEAKENED_SOUL.get()),
                "8 soul sand + flint and steel didn't make a Weakened Soul");
        var left = soul.get().value().getRemainingItems(input);
        boolean toolBack = left.stream().anyMatch(st -> st.is(Items.FLINT_AND_STEEL) && st.getDamageValue() == 1);
        h.assertTrue(toolBack, "the flint and steel didn't come back worn: " + left);
        // Buckets pour where the player looks (Item#use), so pour it the way that ends up doing.
        BlockPos at = h.absolutePos(new BlockPos(8, 3, 8));
        boolean poured = ModItems.TAR_BUCKET.get().emptyContents(player(h, false), level, at,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at.below(), false), new ItemStack(ModItems.TAR_BUCKET.get()));
        h.assertTrue(poured, "the tar bucket wouldn't pour");
        h.succeedWhen(() -> h.assertTrue(com.tarbound.network.TarNetwork.isTar(h.getLevel().getFluidState(h.absolutePos(new BlockPos(8, 3, 8)))),
                "no tar where the bucket was poured"));
    }

    // ------------------------------------------------------------------------------------------ 11. can you get there?

    /**
     * Recipe reachability from vanilla: starting with every vanilla item, repeatedly apply every recipe whose ingredients
     * are all reachable, and report the Tarbound items no recipe chain reaches (they'd need loot, mob drops or creative).
     */
    @GameTest(template = PLAYGROUND, timeoutTicks = 100)
    public static void recipesReachable(GameTestHelper h) {
        var level = h.getLevel();
        java.util.Set<Item> have = new java.util.HashSet<>();
        for (Item i : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i).getNamespace().equals("minecraft")) {
                have.add(i);
            }
        }
        List<RecipeHolder<?>> recipes = new ArrayList<>(level.getRecipeManager().getRecipes());
        boolean grew = true;
        while (grew) {
            grew = false;
            for (RecipeHolder<?> r : recipes) {
                ItemStack out = r.value().getResultItem(level.registryAccess());
                if (out.isEmpty() || have.contains(out.getItem())) {
                    continue;
                }
                boolean ok = true;
                for (var ing : r.value().getIngredients()) {
                    if (ing.isEmpty()) {
                        continue;
                    }
                    boolean any = false;
                    for (ItemStack s : ing.getItems()) {
                        if (have.contains(s.getItem())) {
                            any = true;
                            break;
                        }
                    }
                    if (!any) {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    have.add(out.getItem());
                    grew = true;
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (Item i : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            var id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(i);
            if (id.getNamespace().equals(Tarbound.MODID) && !have.contains(i)) {
                missing.add(id.getPath());
            }
        }
        note("recipesReachable", "not craftable from vanilla items: " + missing);
        // Expected non-craftables: the creative seep, the spawn egg, the fluids' own blocks and Weakened Souls' sources
        // are mobs/recipes this check can't see (the flint-and-steel tool recipe is a custom type).
        h.succeed();
    }

    /** Lists, for the log, which payloads and screens a real player would also touch (not runnable headless). */
    @SuppressWarnings("unused")
    private static final Map<String, String> NOT_COVERED = Map.of(
            "screens", "radio/lectern/reliquary/ossuary GUIs and client payloads",
            "jei/emi", "recipe transfer into the lectern",
            "worldgen", "seeps, cabins, shrines, crypts (checked: datapack loads clean)");
}
