package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.block.ScribingLecternBlockEntity;
import com.tarbound.block.ReliquaryBlockEntity;
import com.tarbound.block.TarAntennaBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Tarbound.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScryingFontBlockEntity>> SCRYING_FONT = BLOCK_ENTITIES.register("scrying_font",
            () -> BlockEntityType.Builder.of(ScryingFontBlockEntity::new, ModBlocks.SCRYING_FONT.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ReliquaryBlockEntity>> RELIQUARY = BLOCK_ENTITIES.register("reliquary",
            () -> BlockEntityType.Builder.of(ReliquaryBlockEntity::new, ModBlocks.RELIQUARY.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScribingLecternBlockEntity>> SCRIBING_LECTERN = BLOCK_ENTITIES.register("scribing_lectern",
            () -> BlockEntityType.Builder.of(ScribingLecternBlockEntity::new, ModBlocks.SCRIBING_LECTERN.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TarAntennaBlockEntity>> TAR_ANTENNA = BLOCK_ENTITIES.register("tar_antenna",
            () -> BlockEntityType.Builder.of(TarAntennaBlockEntity::new, ModBlocks.TAR_ANTENNA.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.tarbound.block.TransientAntennaBlockEntity>> TRANSIENT_ANTENNA =
            BLOCK_ENTITIES.register("transient_antenna", () -> BlockEntityType.Builder.of(com.tarbound.block.TransientAntennaBlockEntity::new,
                    ModBlocks.TRANSIENT_ANTENNA.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.tarbound.block.ShadeBlockEntity>> SHADE =
            BLOCK_ENTITIES.register("shade", () -> BlockEntityType.Builder.of(com.tarbound.block.ShadeBlockEntity::new,
                    ModBlocks.SHADE.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.tarbound.block.OssuaryBlockEntity>> OSSUARY =
            BLOCK_ENTITIES.register("ossuary", () -> BlockEntityType.Builder.of(com.tarbound.block.OssuaryBlockEntity::new,
                    ModBlocks.OSSUARY.get()).build(null));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.tarbound.block.PitchCaskBlockEntity>> PITCH_CASK =
            BLOCK_ENTITIES.register("pitch_cask", () -> BlockEntityType.Builder.of(com.tarbound.block.PitchCaskBlockEntity::new,
                    ModBlocks.PITCH_CASK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.tarbound.block.BottomlessSeepBlockEntity>> BOTTOMLESS_SEEP =
            BLOCK_ENTITIES.register("bottomless_seep", () -> BlockEntityType.Builder.of(com.tarbound.block.BottomlessSeepBlockEntity::new,
                    ModBlocks.BOTTOMLESS_SEEP.get()).build(null));

    private ModBlockEntities() {}
}
