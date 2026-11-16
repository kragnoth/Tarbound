package com.tarbound.compat.ponder;

import com.tarbound.block.TarAntennaBlock;
import com.tarbound.network.PoolAccess;
import com.tarbound.registry.ModItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Tarbound's ponder scenes, in the order a new player meets them. Each scene's text lives in the
 * lang file as {@code tarbound.ponder.<scene>.header} and {@code .text_1}, {@code .text_2}... in the
 * order shown; {@code dev/gen_ponder_lang.py} rebuilds those keys from the strings in this file, so
 * edit the English here and re-run it. Schematics are built by {@code dev/make_ponder_nbt.py}, and the
 * coordinates in each method's comment must match it.
 */
public final class TarboundScenes {
    private TarboundScenes() {}

    private static ItemStack stack(net.minecraft.world.level.ItemLike item) {
        return new ItemStack(item);
    }


    // ------------------------------------------------------------------------------------------ 1

    /**
     * "tar_pool" (5x5): a line of tar at z=2, x=1..3; barrels touching it at (1,1,3) and (3,1,3); the
     * radio at (2,1,1); a crafting table at (0,1,2).
     */
    public static void tarPool(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("tar_pool", "Binding Storage with Tar");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        Selection tar = util.select().fromTo(1, 1, 2, 3, 1, 2);
        Selection barrels = util.select().position(1, 1, 3).add(util.select().position(3, 1, 3));
        Selection radio = util.select().position(2, 1, 1);
        Selection table = util.select().position(0, 1, 2);

        scene.world().showSection(tar, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Black tar binds every inventory it touches into one shared pool")
                .pointAt(util.vector().topOf(2, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(barrels, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, "storage", barrels, 70);
        scene.overlay().showText(70)
                .text("Chests, barrels and other storage touching the tar join the pool")
                .pointAt(util.vector().topOf(3, 1, 3))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(radio, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("A Scrying Font within 3 blocks of the tar reads the whole pool")
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showText(70)
                .text("Open it to search, take and insert items from every bound inventory at once")
                .pointAt(util.vector().topOf(2, 1, 1))
                .placeNearTarget();
        scene.idle(80);

        scene.world().showSection(table, Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("A crafting table touching the tar unlocks the radio's crafting grid")
                .pointAt(util.vector().topOf(0, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 2

    /**
     * "tar_antenna" (5x5): the radio at (1,1,1); a barrel at (3,1,3) with rabbit ears on top at (3,2,3).
     * Every block is in the schematic: scenes only show and hide, never place into air (Ponder doesn't
     * draw blocks set where the schematic had nothing).
     */
    public static void antennas(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("tar_antenna", "Reaching Storage with Antennas");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos radio = util.grid().at(1, 1, 1);
        BlockPos barrel = util.grid().at(3, 1, 3);
        BlockPos ears = util.grid().at(3, 2, 3);

        scene.world().showSection(util.select().position(radio), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Tar Antennas link inventories to a Scrying Font without running tar to them")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(radio), Pointing.DOWN, 60).rightClick().whileSneaking()
                .withItem(stack(ModItems.TAR_ANTENNA.get()));
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Shift-right-click the radio with antennas in hand to tune the whole stack to it")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(80);

        scene.world().showSection(util.select().position(barrel), Direction.DOWN);
        scene.idle(20);
        scene.world().showSection(util.select().position(ears), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showOutline(PonderPalette.GREEN, "bound", util.select().position(barrel), 80);
        scene.overlay().showText(80)
                .text("Mount a tuned antenna on any face of an inventory: whatever touches the antenna joins the pool")
                .pointAt(util.vector().topOf(ears))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().topOf(radio), util.vector().centerOf(ears), 80);
        scene.overlay().showText(80)
                .text("The radio reaches any tuned antenna within 3 blocks of it")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(90);
        scene.overlay().showText(80)
                .text("Tar, crafting tables and inventories touching any piece of an antenna count as touching the tar")
                .pointAt(util.vector().centerOf(ears))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 2b

    /**
     * "antenna_range" (9x9): the radio at (1,1,1); a broadcast mast (three pieces) on the ground at
     * (3,1..3,2), closest to the radio; a rooftop aerial (two pieces) on the ground at (8,1..2,6);
     * rabbit ears (one piece) on a barrel at (5,1,8), the ears at (5,2,8).
     * Reaches: radio 3, ears 3, aerial 6, tower 9. Links (cube distance between mounted ends):
     * radio-ears 7 (no), aerial-radio 7 (no), ears-aerial 3 (yes), tower-radio 2 (yes), tower-aerial 5 (yes).
     */
    public static void antennaRange(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("antenna_range", "Extending Range with Antennas");
        scene.configureBasePlate(0, 0, 9);
        scene.scaleSceneView(0.75F);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos radio = util.grid().at(1, 1, 1);
        BlockPos barrel = util.grid().at(5, 1, 8);
        BlockPos ears = util.grid().at(5, 2, 8);
        BlockPos aerial = util.grid().at(8, 1, 6);
        BlockPos tower = util.grid().at(3, 1, 2);

        scene.world().showSection(util.select().position(radio), Direction.DOWN);
        scene.idle(20);

        // Rabbit ears: one piece, out of reach.
        scene.world().showSection(util.select().position(barrel), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(ears), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showBigLine(PonderPalette.RED, util.vector().topOf(radio), util.vector().centerOf(ears), 90);
        scene.overlay().showText(90)
                .text("One piece makes rabbit ears, with 3 blocks of reach. This barrel is too far from the radio to link")
                .pointAt(util.vector().centerOf(ears))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        // Rooftop aerial: two pieces, on bare ground.
        scene.world().showSection(util.select().position(aerial), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(aerial.above()), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(90)
                .text("Two pieces make a rooftop aerial: 6 blocks. An antenna doesn't need an inventory; on bare ground it just relays")
                .pointAt(util.vector().topOf(aerial.above()))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().centerOf(ears), util.vector().centerOf(aerial.above()), 80);
        scene.overlay().showText(80)
                .text("The ears and the aerial link: two antennas connect when they're within the larger of their two reaches")
                .pointAt(util.vector().centerOf(aerial.above()))
                .placeNearTarget();
        scene.idle(90);

        // Broadcast mast: three pieces, next to the radio.
        scene.world().showSection(util.select().position(tower), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(tower.above()), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(util.select().position(tower.above(2)), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(90)
                .text("Three pieces make a broadcast mast: 9 blocks. Place antennas onto an antenna to stack it, up to three")
                .pointAt(util.vector().topOf(tower.above(2)))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);

        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().topOf(radio), util.vector().centerOf(tower), 100);
        scene.idle(10);
        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().centerOf(tower.above(2)), util.vector().centerOf(aerial.above()), 90);
        scene.idle(10);
        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().centerOf(aerial.above()), util.vector().centerOf(ears), 80);
        scene.overlay().showOutline(PonderPalette.GREEN, "bound", util.select().position(barrel), 80);
        scene.overlay().showText(90)
                .text("Now the chain reaches the radio: tower, aerial, ears. The barrel joins the pool")
                .pointAt(util.vector().topOf(barrel))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(100);
        scene.overlay().showText(80)
                .text("Distance is measured between the mounted ends, and every antenna in the chain must be tuned to the same radio")
                .pointAt(util.vector().centerOf(tower))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 3

    /**
     * "antenna_modes" (5x5): the radio at (4,1,0); barrels at (1,1,2), (2,1,2) and (3,1,2), each with
     * an antenna on top at y=2.
     */
    public static void antennaModes(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("antenna_modes", "Antenna Modes and Priority");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos left = util.grid().at(1, 2, 2);
        BlockPos mid = util.grid().at(2, 2, 2);
        BlockPos right = util.grid().at(3, 2, 2);
        scene.world().showSection(util.select().position(4, 1, 0), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 3, 1, 2), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().fromTo(1, 2, 2, 3, 2, 2), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("An antenna's mode decides how the radio may use the inventories it touches")
                .pointAt(util.vector().topOf(mid))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showOutline(PonderPalette.RED, "commune", util.select().position(1, 1, 2), 70);
        scene.overlay().showText(70)
                .text("Commune (red): the radio takes from it and stores into it, the same as touching tar")
                .pointAt(util.vector().topOf(left))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(mid), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        scene.world().modifyBlock(mid, s -> s.setValue(TarAntennaBlock.MODE, PoolAccess.SCRY), false);
        scene.overlay().showOutline(PonderPalette.OUTPUT, "scry", util.select().position(2, 1, 2), 70);
        scene.overlay().showText(70)
                .text("Right-click with an empty hand to change mode. Scry (gold): the radio sees and takes, but never stores. For output chests")
                .pointAt(util.vector().topOf(mid))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(right), Pointing.DOWN, 30).rightClick();
        scene.idle(10);
        scene.world().modifyBlock(right, s -> s.setValue(TarAntennaBlock.MODE, PoolAccess.SCRY), false);
        scene.idle(10);
        scene.world().modifyBlock(right, s -> s.setValue(TarAntennaBlock.MODE, PoolAccess.TITHE), false);
        scene.overlay().showOutline(PonderPalette.FAST, "tithe", util.select().position(3, 1, 2), 70);
        scene.overlay().showText(70)
                .text("Tithe (violet): the radio stores into it, but can't see or take it back. For feed and void chests")
                .pointAt(util.vector().topOf(right))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(left), Pointing.DOWN, 40).rightClick().whileSneaking();
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Shift-right-click with an empty hand to set a priority from -99 to 99: higher fills first and empties last")
                .pointAt(util.vector().topOf(left))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(70)
                .text("At the same priority, a Tithe inventory only gets what visible storage can't hold")
                .pointAt(util.vector().topOf(right))
                .placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 4

    /**
     * "reliquary" (5x5): the radio at (1,1,1); tar at (1,1,2) and (2,1,2); a barrel at (1,1,3);
     * a furnace touching the tar at (3,1,2); a Reliquary on it at (3,2,2).
     */
    public static void reliquary(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("reliquary", "Binding Machines with a Reliquary");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos radio = util.grid().at(1, 1, 1);
        BlockPos furnace = util.grid().at(3, 1, 2);
        BlockPos reliquary = util.grid().at(3, 2, 2);
        scene.world().showSection(util.select().position(radio), Direction.DOWN);
        scene.world().showSection(util.select().fromTo(1, 1, 2, 2, 1, 2).add(util.select().position(1, 1, 3)), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(furnace), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showOutline(PonderPalette.RED, "unbound", util.select().position(furnace), 60);
        scene.overlay().showText(70)
                .text("Machines don't join the pool by touching tar: their slots would mix with your storage")
                .pointAt(util.vector().topOf(furnace))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(radio), Pointing.DOWN, 50).rightClick().whileSneaking()
                .withItem(stack(ModItems.RELIQUARY.get()));
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Tune a Reliquary to the radio with a shift-right-click, like an antenna")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(70);

        scene.world().showSection(util.select().position(reliquary), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showOutline(PonderPalette.GREEN, "bound", util.select().position(furnace), 70);
        scene.overlay().showText(80)
                .text("Set it on top of a machine. The machine is bound to that radio from anywhere in the dimension, while both are loaded")
                .pointAt(util.vector().topOf(reliquary))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(70)
                .text("Bound machines appear in the radio's machine window, slot by slot, to load and empty from there")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(80);

        scene.world().modifyBlock(furnace, s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.overlay().showText(70)
                .text("Its spirit keeps a bound furnace lit with no fuel while there is something to cook, paid for in the radio's Spirit")
                .pointAt(util.vector().blockSurface(furnace, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showText(70)
                .text("Once a second, finished products drain out of bound machines into the pool")
                .pointAt(util.vector().topOf(1, 1, 3))
                .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(reliquary), Pointing.DOWN, 40).rightClick()
                .withItem(stack(Items.GOLD_NUGGET));
        scene.idle(10);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            Vec3 at = util.vector().centerOf(reliquary).add(Math.cos(a) * 3.5, -0.5, Math.sin(a) * 3.5);
            scene.effects().emitParticles(at, scene.effects().simpleParticleEmitter(com.tarbound.block.ReliquaryBlock.HOARD_GOLD, Vec3.ZERO), 1, 10);
        }
        scene.overlay().showText(80)
                .text("Toss it a gold nugget and its spirits hoard what lands nearby: other mods' magnets can't take it. Again to let go")
                .pointAt(util.vector().topOf(reliquary))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(70)
                .text("Name a Reliquary in an anvil, or at the top of its screen, to tell look-alike machines apart")
                .pointAt(util.vector().topOf(reliquary))
                .placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 5

    /**
     * "contracts" (5x5): the radio at (1,1,1); tar at (2,1,1); a barrel at (2,1,0); furnaces at
     * (3,1,2) and (4,1,2) with Reliquaries on top; a Scribing Lectern at (1,1,3).
     */
    public static void contracts(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("contracts", "Spirit Contracts and Jobs");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos radio = util.grid().at(1, 1, 1);
        BlockPos lectern = util.grid().at(1, 1, 3);
        BlockPos furnace1 = util.grid().at(3, 1, 2);
        BlockPos furnace2 = util.grid().at(4, 1, 2);

        scene.world().showSection(util.select().position(lectern), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("A Spirit Contract tells a machine spirit what one operation takes in and gives back")
                .pointAt(util.vector().topOf(lectern))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(lectern), Pointing.DOWN, 50).rightClick()
                .withItem(stack(ModItems.BLANK_CONTRACT.get()));
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Put blank contracts in the Scribing Lectern's left slot")
                .pointAt(util.vector().topOf(lectern))
                .placeNearTarget();
        scene.idle(80);
        scene.overlay().showText(80)
                .text("With the lectern open, find a recipe in JEI or EMI and press + to lay it out (or drag items in), then press Write. The contract appears in the right slot")
                .pointAt(util.vector().topOf(lectern))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().position(furnace1), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(furnace1.above()), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(furnace1.above()), Pointing.DOWN, 50).rightClick()
                .withItem(stack(ModItems.SPIRIT_CONTRACT.get()));
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Give the contract to the Reliquary on the machine that runs it. Feed it candles to open more contract slots")
                .pointAt(util.vector().topOf(furnace1.above()))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(util.select().position(radio).add(util.select().fromTo(2, 1, 0, 2, 1, 1)), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("In the radio, anything a contract can make has a cyan mark. Click a dimmed ghost, or Ctrl-click an item, to order an amount")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().modifyBlock(furnace1, s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.overlay().showText(80)
                .text("The job loads the machine from the pool a few operations at a time, and counts the products as they come back")
                .pointAt(util.vector().blockSurface(furnace1, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(util.select().position(furnace2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(furnace2.above()), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlock(furnace2, s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.overlay().showText(70)
                .text("Every Reliquary holding the same contract shares the job, so more machines finish sooner")
                .pointAt(util.vector().topOf(furnace2.above()))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showText(80)
                .text("Jobs run with the screen closed and are saved with the world. Watch or cancel them in the radio's second tab")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 6

    /**
     * "crafting_station" (5x5): the radio at (1,1,1); tar at (1,1,2) and (2,1,2); barrels at (1,1,3)
     * and (2,1,3); crafting tables at (3,1,0) and (4,1,0) with Reliquaries on top.
     */
    public static void craftingStation(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("crafting_station", "Crafting Stations");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos table1 = util.grid().at(3, 1, 0);
        BlockPos table2 = util.grid().at(4, 1, 0);
        scene.world().showSection(util.select().position(1, 1, 1)
                .add(util.select().fromTo(1, 1, 2, 2, 1, 3)), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(table1), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(table1.above()), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("A tuned Reliquary on a crafting table makes a crafting station")
                .pointAt(util.vector().topOf(table1.above()))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(table1.above()), Pointing.DOWN, 50).rightClick()
                .withItem(stack(ModItems.SPIRIT_CONTRACT.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Give it a contract written from a crafting recipe. It has no slots: it takes the ingredients from the pool and puts the result back")
                .pointAt(util.vector().topOf(table1.above()))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(util.select().position(table2), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(table2.above()), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .text("Stations holding the same contract split a job evenly: two tables craft twice as fast")
                .pointAt(util.vector().topOf(table2.above()))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showOutline(PonderPalette.RED, "full", util.select().fromTo(1, 1, 3, 2, 1, 3), 70);
        scene.overlay().showText(70)
                .text("If the pool has no room for the result, stations wait instead of crafting, and the job says Pool full")
                .pointAt(util.vector().topOf(2, 1, 3))
                .placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 7

    /**
     * "standing_contracts" (5x5): the radio at (1,1,1); tar at (1,1,2); a barrel at (1,1,3); a blast
     * furnace at (3,1,1) with a Reliquary on top; a Scribing Lectern at (3,1,3).
     */
    public static void standingContracts(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("standing_contracts", "Standing Contracts");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos lectern = util.grid().at(3, 1, 3);
        BlockPos machine = util.grid().at(3, 1, 1);
        BlockPos radio = util.grid().at(1, 1, 1);
        scene.world().showSection(util.select().position(radio).add(util.select().fromTo(1, 1, 2, 1, 1, 3)), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(lectern), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("A standing contract keeps a machine fed with anything in an item tag, with no job to order")
                .pointAt(util.vector().topOf(lectern))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showText(80)
                .text("Leave a one-input contract in the lectern's right slot, pick one of its item's tags with < and >, and press Generalize")
                .pointAt(util.vector().topOf(lectern))
                .placeNearTarget();
        scene.idle(90);

        scene.world().showSection(util.select().position(machine), Direction.DOWN);
        scene.idle(5);
        scene.world().showSection(util.select().position(machine.above()), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(machine.above()), Pointing.DOWN, 50).rightClick()
                .withItem(stack(ModItems.SPIRIT_CONTRACT.get()));
        scene.idle(10);
        scene.world().modifyBlock(machine, s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.overlay().showText(80)
                .text("In a Reliquary, it loads every matching item in the pool into the machine, keeping a small buffer topped up")
                .pointAt(util.vector().blockSurface(machine, Direction.SOUTH))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(70)
                .text("Requested jobs get first pick. Pause or resume standing contracts in the lower half of the radio's jobs tab")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(80);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 8

    /**
     * "spirit_board" (5x5): the radio at (2,1,2); tar at (2,1,3); barrels at (1,1,3) and (3,1,3).
     */
    public static void spiritBoard(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("spirit_board", "Spirit Board: Remote Access");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos radio = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(radio).add(util.select().fromTo(1, 1, 3, 3, 1, 3)), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(70)
                .text("A Spirit Board opens a radio's screen from afar")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.overlay().showControls(util.vector().topOf(radio), Pointing.DOWN, 50).rightClick().whileSneaking()
                .withItem(stack(ModItems.SPIRIT_BOARD.get()));
        scene.idle(10);
        scene.overlay().showText(60)
                .text("Shift-right-click the radio to tune the board to it")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(70);
        scene.overlay().showControls(util.vector().of(0.5, 2.5, 0.5), Pointing.DOWN, 50).rightClick()
                .withItem(stack(ModItems.SPIRIT_BOARD.get()));
        scene.idle(10);
        scene.overlay().showText(80)
                .text("Then right-click anywhere in the same dimension: the pool, crafting grid, machines and jobs, all there")
                .pointAt(util.vector().of(0.5, 2.5, 0.5))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(80)
                .text("The radio's chunk must be loaded. Past your view distance, keep it loaded with a chunk loader")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(90);
        scene.markAsFinished();
    }

    // ------------------------------------------------------------------------------------------ 9

    /**
     * "feeding_spirits" (5x5): the radio at (2,1,2) with a hopper on top at (2,2,2); tar at (2,1,3)
     * between barrels at (1,1,3) and (3,1,3); a comparator reading the radio at (3,1,2) into a
     * redstone lamp at (4,1,2).
     */
    public static void feedingSpirits(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("feeding_spirits", "Feeding the Spirits");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        BlockPos radio = util.grid().at(2, 1, 2);
        scene.world().showSection(util.select().position(radio), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("A radio's automation runs on Spirit, held in a store you can see on its screen. Storage itself is always free")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.overlay().showControls(util.vector().topOf(radio), Pointing.DOWN, 50).rightClick()
                .withItem(stack(Items.CANDLE));
        scene.idle(10);
        scene.overlay().showText(70)
                .text("Right-click the radio with candles to feed the spirits")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);

        scene.world().showSection(util.select().position(2, 2, 2), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("Hoppers and pipes can feed candles straight in. The radio takes nothing else, and gives nothing back")
                .pointAt(util.vector().topOf(2, 2, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);

        scene.world().showSection(util.select().fromTo(1, 1, 3, 3, 1, 3), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(80)
                .text("With the candle on its screen lit, the radio burns candles kept in the pool whenever Spirit runs below half")
                .pointAt(util.vector().topOf(1, 1, 3))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(90);
        scene.overlay().showText(90)
                .text("Loading machines, crafting at stations, Spirit Fire, machine power and Spirit Boards spend it. Out of Spirit, they wait")
                .pointAt(util.vector().topOf(radio))
                .placeNearTarget();
        scene.idle(100);

        scene.world().showSection(util.select().fromTo(3, 1, 2, 4, 1, 2), Direction.DOWN);
        scene.idle(15);
        scene.world().modifyBlock(util.grid().at(3, 1, 2), s -> s.setValue(BlockStateProperties.POWERED, true), false);
        scene.world().modifyBlock(util.grid().at(4, 1, 2), s -> s.setValue(BlockStateProperties.LIT, true), false);
        scene.overlay().showText(70)
                .text("A comparator reads how full the store is, to sound an alarm or start a candle line")
                .pointAt(util.vector().topOf(3, 1, 2))
                .placeNearTarget()
                .attachKeyFrame();
        scene.idle(80);
        scene.markAsFinished();
    }
}
