package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.block.ScryingFontBlock;
import com.tarbound.block.ScribingLecternBlock;
import com.tarbound.block.ReliquaryBlock;
import com.tarbound.block.TarAntennaBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Tarbound.MODID);

    public static final DeferredBlock<LiquidBlock> TAR = BLOCKS.register("tar", () -> new LiquidBlock(ModFluids.TAR.get(),
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)));

    public static final DeferredBlock<ScryingFontBlock> SCRYING_FONT = BLOCKS.registerBlock("scrying_font", ScryingFontBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASEDRUM)
                    .requiresCorrectToolForDrops()
                    .strength(2.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .noOcclusion()
                    // The radio's hitbox is under the size vanilla counts as "solid", which would let
                    // flowing tar wash it away like a torch. It has to sit in tar, so force it solid.
                    .forceSolidOn()
                    .lightLevel(state -> 4));

    public static final DeferredBlock<ReliquaryBlock> RELIQUARY = BLOCKS.registerBlock("reliquary", ReliquaryBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.TERRACOTTA_WHITE)
                    .requiresCorrectToolForDrops()
                    .strength(3.0F, 6.0F)
                    .sound(SoundType.BONE_BLOCK)
                    .noOcclusion()
                    .forceSolidOn()
                    .lightLevel(state -> 6));

    public static final DeferredBlock<TarAntennaBlock> TAR_ANTENNA = BLOCKS.registerBlock("tar_antenna", TarAntennaBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    // Thin enough that flowing tar would wash it away; keep it bolted on.
                    .forceSolidOn()
                    .pushReaction(PushReaction.DESTROY));

    public static final DeferredBlock<ScribingLecternBlock> SCRIBING_LECTERN = BLOCKS.registerBlock("scribing_lectern", ScribingLecternBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .ignitedByLava());

    public static final DeferredBlock<LiquidBlock> ESSENCE_OF_THE_VOID = BLOCKS.register("essence_of_the_void",
            () -> new LiquidBlock(ModFluids.ESSENCE.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .lightLevel(state -> 10)
                    .sound(SoundType.EMPTY)));

    public static final DeferredBlock<com.tarbound.block.TransientAntennaBlock> TRANSIENT_ANTENNA = BLOCKS.registerBlock("transient_antenna",
            com.tarbound.block.TransientAntennaBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.LANTERN)
                    .noOcclusion()
                    .forceSolidOn()
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(com.tarbound.block.TransientAntennaBlock.MODE) == com.tarbound.block.TransientAntennaBlock.Mode.IDLE ? 0 : 7));

    public static final DeferredBlock<com.tarbound.block.ShadeBlock> SHADE = BLOCKS.registerBlock("shade", com.tarbound.block.ShadeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.0F)
                    .sound(SoundType.SCULK)
                    .noOcclusion()
                    .noCollission()
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> 5));

    public static final DeferredBlock<com.tarbound.block.OssuaryBlock> OSSUARY = BLOCKS.registerBlock("ossuary", com.tarbound.block.OssuaryBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .instrument(NoteBlockInstrument.XYLOPHONE)
                    .requiresCorrectToolForDrops()
                    .strength(2.0F, 6.0F)
                    .sound(SoundType.BONE_BLOCK)
                    .noOcclusion()
                    .lightLevel(state -> 9));

    /** Tar-soaked wood: solid blocks that carry tar (tag tarbound:tar_conduits), so links can be hidden in floors and walls. */
    public static final DeferredBlock<net.minecraft.world.level.block.RotatedPillarBlock> TAR_SOAKED_LOG = BLOCKS.registerBlock("tar_soaked_log",
            net.minecraft.world.level.block.RotatedPillarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD));

    public static final DeferredBlock<net.minecraft.world.level.block.Block> TAR_SOAKED_PLANKS = BLOCKS.registerSimpleBlock("tar_soaked_planks",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD));

    /** A tar-sealed oak cask holding 8 buckets of any fluid: the pack's tank if nothing else brings one. */
    public static final DeferredBlock<com.tarbound.block.PitchCaskBlock> PITCH_CASK = BLOCKS.registerBlock("pitch_cask",
            com.tarbound.block.PitchCaskBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .ignitedByLava());

    /** A skeleton skull with a candle on its crown (vanilla's skull and candle drawn together); feeds a Shade a rank. */
    public static final DeferredBlock<com.tarbound.block.VotiveSkullBlock> VOTIVE_SKULL = BLOCKS.registerBlock("votive_skull",
            com.tarbound.block.VotiveSkullBlock::new,
            BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SKELETON_SKULL)
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 3 : 0));

    public static final DeferredBlock<com.tarbound.block.VotiveWallSkullBlock> VOTIVE_WALL_SKULL = BLOCKS.register("votive_wall_skull",
            () -> new com.tarbound.block.VotiveWallSkullBlock(BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SKELETON_WALL_SKULL)
                    .lightLevel(state -> state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT) ? 3 : 0)
                    .dropsLike(VOTIVE_SKULL.get())));

    /** A tar-soaked rack of candles round a radio: more jobs and deeper orders while it burns. */
    public static final DeferredBlock<com.tarbound.block.VotiveRackBlock> VOTIVE_RACK = BLOCKS.registerBlock("votive_rack",
            com.tarbound.block.VotiveRackBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(1.5F, 2.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(com.tarbound.block.VotiveRackBlock.LIT) ? 10 : 0));

    /** Creative only: hands its radio endless raw materials (anything no contract there makes), for testing deep orders. */
    public static final DeferredBlock<com.tarbound.block.BottomlessSeepBlock> BOTTOMLESS_SEEP = BLOCKS.registerBlock("bottomless_seep",
            com.tarbound.block.BottomlessSeepBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .lightLevel(state -> 4));

    /** Survival: weeps cheap filler into its radio's jobs and orders, for Spirit (the {@code seep_yields} data map). */
    public static final DeferredBlock<com.tarbound.block.WeepingSeepBlock> WEEPING_SEEP = BLOCKS.registerBlock("weeping_seep",
            com.tarbound.block.WeepingSeepBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> 2));

    private ModBlocks() {}
}
