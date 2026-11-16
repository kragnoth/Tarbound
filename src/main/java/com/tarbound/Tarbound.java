package com.tarbound;

import com.mojang.logging.LogUtils;
import com.tarbound.net.TarboundPayloads;
import com.tarbound.registry.ModBlockEntities;
import com.tarbound.registry.ModBlocks;
import com.tarbound.registry.ModCreativeTabs;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModFluids;
import com.tarbound.registry.ModItems;
import com.tarbound.registry.ModMenus;
import com.tarbound.registry.ModSounds;
import com.tarbound.block.ReliquaryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;
import org.slf4j.Logger;

@Mod(Tarbound.MODID)
public final class Tarbound {
    public static final String MODID = "tarbound";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Tarbound(IEventBus modBus, ModContainer container) {
        ModDataComponents.COMPONENTS.register(modBus);
        ModFluids.FLUID_TYPES.register(modBus);
        ModFluids.FLUIDS.register(modBus);
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModItems.ITEMS.register(modBus);
        com.tarbound.registry.ModEntities.ENTITIES.register(modBus);
        com.tarbound.registry.ModRecipes.SERIALIZERS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModSounds.SOUNDS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        com.tarbound.registry.ModStructures.TYPES.register(modBus);

        modBus.addListener(TarboundPayloads::register);
        modBus.addListener(Tarbound::registerCapabilities);
        modBus.addListener(Tarbound::addToVanillaBlockEntities);
        modBus.addListener(com.tarbound.spirit.Spirit::registerDataMaps);
        modBus.addListener(com.tarbound.registry.ModEntities::registerAttributes);
        modBus.addListener(com.tarbound.registry.ModEntities::registerSpawnPlacements);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.entity.WeakenedBlaze::onIncomingDamage);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.entity.BlazeSpawnerConversion::onRightClickBlock);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.block.TransientAntennaBlock::onRightClickBlock);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.fluid.TarHazard::onEntityTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.fluid.TarHazard::onDeath);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.fluid.TarSwallow::onEntityTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.TarboundDebug::registerCommands);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.network.NetworkChanges::onNeighborNotify);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, com.tarbound.config.TarboundConfig.CLIENT_SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, com.tarbound.config.TarboundConfig.SERVER_SPEC);
        // Anti-magnet wards flag items as they spawn (both sides); see AntiMagnet.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.network.AntiMagnet::onItemJoin);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.net.TarboundPayloads::onLoggedOut);
    }

    /** Votive Skulls are vanilla skulls underneath: they use vanilla's skull block entity (and so its renderer). */
    private static void addToVanillaBlockEntities(net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent event) {
        event.modify(net.minecraft.world.level.block.entity.BlockEntityType.SKULL,
                com.tarbound.registry.ModBlocks.VOTIVE_SKULL.get(), com.tarbound.registry.ModBlocks.VOTIVE_WALL_SKULL.get());
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, com.tarbound.registry.ModBlockEntities.PITCH_CASK.get(), (be, side) -> be.tank());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, com.tarbound.registry.ModBlockEntities.BOTTOMLESS_SEEP.get(), (be, side) -> be.items());
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, com.tarbound.registry.ModBlockEntities.BOTTOMLESS_SEEP.get(), (be, side) -> be.fluids());
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new com.tarbound.item.PitchCaskItem.Handler(stack),
                com.tarbound.registry.ModItems.PITCH_CASK.get());
        // NeoForge only auto-wraps items whose class is exactly BucketItem; register ours explicitly
        // so tanks, pipes and other fluid handlers can fill/empty the tar bucket.
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new FluidBucketWrapper(stack), ModItems.TAR_BUCKET.get());

        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new FluidBucketWrapper(stack), ModItems.ESSENCE_BUCKET.get());

        // The radio's battery takes and gives FE through cables connected straight to it.
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, com.tarbound.registry.ModBlockEntities.SCRYING_FONT.get(),
                (font, side) -> font.battery());

        // The radio takes offerings (candles) from hoppers and pipes, into its Spirit store.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, com.tarbound.registry.ModBlockEntities.SCRYING_FONT.get(),
                (font, side) -> new com.tarbound.spirit.OfferingSlot(font));

        // A Reliquary passes pipes and hoppers through to the machine it faces, with exactly what the machine itself
        // offers on that face (0.59: no longer every slot, which let pipes reach outputs and inputs alike). The radio
        // works the machine through MachineAccess, not through this. (One facing a machine only; an all-sides
        // Reliquary works several, so it passes nothing through.)
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, be, side) -> {
            ReliquaryBlock.Attach attach = state.getValue(ReliquaryBlock.ATTACH);
            if (attach.dir == null) {
                return null;
            }
            BlockPos machine = pos.relative(attach.dir);
            if (!level.isLoaded(machine)) {
                return null; // never load a chunk just to answer a pipe
            }
            BlockState machineState = level.getBlockState(machine);
            if (machineState.getBlock() instanceof ReliquaryBlock) {
                return null;
            }
            // A chain of pass-through blocks (Reliquary, Entangled, Machine Extender) that loops back on itself
            // stops here, as Entangled's own guard does, rather than overflowing the stack.
            int depth = PASS_THROUGH_DEPTH.get()[0];
            if (depth >= 8) {
                return null;
            }
            PASS_THROUGH_DEPTH.get()[0] = depth + 1;
            try {
                return level.getCapability(Capabilities.ItemHandler.BLOCK, machine, machineState,
                        machineState.hasBlockEntity() ? level.getBlockEntity(machine) : null, attach.dir.getOpposite());
            } finally {
                PASS_THROUGH_DEPTH.get()[0] = depth;
            }
        }, ModBlocks.RELIQUARY.get());
    }

    /** How deep this thread is in Reliquary pass-through lookups (client and server run on different threads). */
    public static final ThreadLocal<int[]> PASS_THROUGH_DEPTH = ThreadLocal.withInitial(() -> new int[1]);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }
}
