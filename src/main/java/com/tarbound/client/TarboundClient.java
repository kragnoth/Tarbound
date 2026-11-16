package com.tarbound.client;

import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.tarbound.Tarbound;
import com.tarbound.registry.ModFluids;
import com.tarbound.registry.ModMenus;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Vector3f;

@Mod(value = Tarbound.MODID, dist = Dist.CLIENT)
public final class TarboundClient {
    private static final ResourceLocation TAR_STILL = Tarbound.id("block/tar_still");
    private static final ResourceLocation TAR_FLOW = Tarbound.id("block/tar_flow");
    private static final ResourceLocation ESSENCE_STILL = Tarbound.id("block/essence_of_the_void_still");
    private static final ResourceLocation ESSENCE_FLOW = Tarbound.id("block/essence_of_the_void_flow");

    public TarboundClient(IEventBus modBus) {
        modBus.addListener(TarboundClient::registerScreens);
        modBus.addListener(TarboundClient::registerClientExtensions);
        modBus.addListener(TarboundClient::clientSetup);
        modBus.addListener(TarboundClient::addPacks);
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(com.tarbound.registry.ModEntities.WEAKENED_BLAZE.get(), WeakenedBlazeRenderer::new));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e) ->
                e.registerLayerDefinition(WeakenedBlazeModel.LAYER, WeakenedBlazeModel::createBodyLayer));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerEntityRenderer(com.tarbound.registry.ModEntities.STEWARD.get(), StewardRenderer::new));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions e) ->
                e.registerLayerDefinition(StewardModel.LAYER, StewardModel::createBodyLayer));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(com.tarbound.registry.ModBlockEntities.RELIQUARY.get(), ReliquaryRenderer::new));
        modBus.addListener((net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers e) ->
                e.registerBlockEntityRenderer(com.tarbound.registry.ModBlockEntities.SHADE.get(), ShadeRenderer::new));
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(MachineHighlight::onRenderLevel);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.tarbound.fluid.TarSwallow::onPlaySound);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BindingHighlight::onRenderLevel);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BindingHighlight::onClientTick);
    }

    private static void clientSetup(FMLClientSetupEvent event) {
        // Ponder scenes (the "hold W" tutorials) only when Ponder is installed (it ships with Create).
        // PonderCompat is only touched behind this check, so its Ponder imports never load without it.
        if (ModList.get().isLoaded("ponder")) {
            event.enqueueWork(com.tarbound.compat.ponder.PonderCompat::init);
        }
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SCRYING_FONT.get(), ScryingFontScreen::new);
        event.register(ModMenus.RELIQUARY.get(), ReliquaryScreen::new);
        event.register(ModMenus.SCRIBING_LECTERN.get(), ScribingLecternScreen::new);
        event.register(ModMenus.OSSUARY.get(), OssuaryScreen::new);
        event.register(ModMenus.STEWARD.get(), StewardScreen::new);
    }

    private static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return TAR_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return TAR_FLOW;
            }

            @Override
            public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                return new Vector3f(0.04F, 0.03F, 0.025F);
            }

            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape) {
                RenderSystem.setShaderFogStart(0.0F);
                RenderSystem.setShaderFogEnd(1.5F);
                RenderSystem.setShaderFogShape(FogShape.SPHERE);
            }
        }, ModFluids.TAR_TYPE.get());

        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return ESSENCE_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return ESSENCE_FLOW;
            }

            @Override
            public Vector3f modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorldAmount, Vector3f fluidFogColor) {
                return new Vector3f(0.06F, 0.02F, 0.12F);
            }

            @Override
            public void modifyFogRender(Camera camera, FogRenderer.FogMode mode, float renderDistance, float partialTick, float nearDistance, float farDistance, FogShape shape) {
                RenderSystem.setShaderFogStart(0.0F);
                RenderSystem.setShaderFogEnd(4.0F);
                RenderSystem.setShaderFogShape(FogShape.SPHERE);
            }
        }, ModFluids.ESSENCE_TYPE.get());
    }

    /**
     * "Tarbound: Classic Cubes", shipped inside the mod (resourcepacks/classic_cubes): square radio, simple
     * antennas, flatter item icons. Off by default; players turn it on in the Resource Packs screen.
     */
    private static void addPacks(net.neoforged.neoforge.event.AddPackFindersEvent event) {
        event.addPackFinders(com.tarbound.Tarbound.id("resourcepacks/classic_cubes"),
                net.minecraft.server.packs.PackType.CLIENT_RESOURCES,
                net.minecraft.network.chat.Component.literal("Tarbound: Classic Cubes"),
                net.minecraft.server.packs.repository.PackSource.BUILT_IN, false,
                net.minecraft.server.packs.repository.Pack.Position.TOP);
    }
}
