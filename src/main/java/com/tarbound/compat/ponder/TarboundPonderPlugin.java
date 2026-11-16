package com.tarbound.compat.ponder;

import com.tarbound.Tarbound;
import com.tarbound.registry.ModItems;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Ponder (optional, client): hold the ponder key over Tarbound items to watch how they work. Ships with
 * Create 6, or as the standalone Ponder library. Only loaded when Ponder is installed.
 */
public class TarboundPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return Tarbound.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation font = ModItems.SCRYING_FONT.getId();
        ResourceLocation bucket = ModItems.TAR_BUCKET.getId();
        ResourceLocation antenna = ModItems.TAR_ANTENNA.getId();
        ResourceLocation reliquary = ModItems.RELIQUARY.getId();
        ResourceLocation lectern = ModItems.SCRIBING_LECTERN.getId();
        ResourceLocation blank = ModItems.BLANK_CONTRACT.getId();
        ResourceLocation contract = ModItems.SPIRIT_CONTRACT.getId();
        ResourceLocation board = ModItems.SPIRIT_BOARD.getId();

        // Registration order is the order an item's scenes play in. The radio walks the whole story.
        helper.forComponents(font, bucket).addStoryBoard("tar_pool", TarboundScenes::tarPool);
        helper.forComponents(font).addStoryBoard("feeding_spirits", TarboundScenes::feedingSpirits);
        helper.forComponents(antenna, font).addStoryBoard("tar_antenna", TarboundScenes::antennas);
        helper.forComponents(antenna, font).addStoryBoard("antenna_range", TarboundScenes::antennaRange);
        helper.forComponents(antenna).addStoryBoard("antenna_modes", TarboundScenes::antennaModes);
        helper.forComponents(reliquary, font).addStoryBoard("reliquary", TarboundScenes::reliquary);
        helper.forComponents(lectern, blank, contract, reliquary, font).addStoryBoard("contracts", TarboundScenes::contracts);
        helper.forComponents(reliquary, contract).addStoryBoard("crafting_station", TarboundScenes::craftingStation);
        helper.forComponents(lectern, contract).addStoryBoard("standing_contracts", TarboundScenes::standingContracts);
        helper.forComponents(board, font).addStoryBoard("spirit_board", TarboundScenes::spiritBoard);
    }

    /** One "Tarbound" category in the ponder index, holding every item with a scene. */
    @Override
    public void registerTags(PonderTagRegistrationHelper<ResourceLocation> helper) {
        ResourceLocation tag = Tarbound.id("tarbound");
        helper.registerTag(tag)
                .addToIndex()
                .item(ModItems.SCRYING_FONT.get(), true, false)
                .title("Tarbound")
                .description("Tar pools, haunted radios and machine spirits")
                .register();
        helper.addToTag(tag)
                .add(ModItems.SCRYING_FONT.getId())
                .add(ModItems.TAR_BUCKET.getId())
                .add(ModItems.TAR_ANTENNA.getId())
                .add(ModItems.RELIQUARY.getId())
                .add(ModItems.SCRIBING_LECTERN.getId())
                .add(ModItems.BLANK_CONTRACT.getId())
                .add(ModItems.SPIRIT_CONTRACT.getId())
                .add(ModItems.SPIRIT_BOARD.getId());
    }
}
