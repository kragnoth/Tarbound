package com.tarbound.registry;

import com.tarbound.Tarbound;
import com.tarbound.item.ReliquaryItem;
import com.tarbound.item.SpiritContractItem;
import com.tarbound.item.TarAntennaItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Tarbound.MODID);

    public static final DeferredItem<BucketItem> TAR_BUCKET = ITEMS.register("tar_bucket", () -> new BucketItem(ModFluids.TAR.get(),
            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<BlockItem> SCRYING_FONT = ITEMS.registerSimpleBlockItem("scrying_font", ModBlocks.SCRYING_FONT);

    public static final DeferredItem<ReliquaryItem> RELIQUARY = ITEMS.register("reliquary",
            () -> new ReliquaryItem(ModBlocks.RELIQUARY.get(), new Item.Properties()));

    public static final DeferredItem<TarAntennaItem> TAR_ANTENNA = ITEMS.register("tar_antenna",
            () -> new TarAntennaItem(ModBlocks.TAR_ANTENNA.get(), new Item.Properties()));

    /** Rendered animal fat; pigs and cows drop it. Candle wax, like honeycomb. */
    public static final DeferredItem<Item> TALLOW = ITEMS.registerSimpleItem("tallow", new Item.Properties());

    /** Vegan candle wax, mixed from seeds in a heated Create mixer. */
    public static final DeferredItem<Item> PLANT_WAX = ITEMS.registerSimpleItem("plant_wax", new Item.Properties());

    public static final DeferredItem<com.tarbound.item.SpiritBoardItem> SPIRIT_BOARD = ITEMS.register("spirit_board",
            () -> new com.tarbound.item.SpiritBoardItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> BLANK_CONTRACT = ITEMS.registerSimpleItem("blank_contract", new Item.Properties());

    public static final DeferredItem<SpiritContractItem> SPIRIT_CONTRACT = ITEMS.register("spirit_contract",
            () -> new SpiritContractItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> SCRIBING_LECTERN = ITEMS.registerSimpleBlockItem("scribing_lectern", ModBlocks.SCRIBING_LECTERN);

    /** A scrap of soul, burned out of blazes or ground from soul sand. Makes tar, and the radio eats it. */
    public static final DeferredItem<Item> WEAKENED_SOUL = ITEMS.registerSimpleItem("weakened_soul", new Item.Properties());

    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> WEAKENED_BLAZE_SPAWN_EGG = ITEMS.register(
            "weakened_blaze_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.WEAKENED_BLAZE,
                    0x8FB8C2, 0x3E6E7A, new Item.Properties()));

    public static final DeferredItem<BucketItem> ESSENCE_BUCKET = ITEMS.register("essence_of_the_void_bucket", () -> new BucketItem(ModFluids.ESSENCE.get(),
            new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<com.tarbound.item.TarConduitItem> TAR_SOAKED_LOG = ITEMS.register("tar_soaked_log",
            () -> new com.tarbound.item.TarConduitItem(ModBlocks.TAR_SOAKED_LOG.get(), new Item.Properties()));
    public static final DeferredItem<com.tarbound.item.TarConduitItem> TAR_SOAKED_PLANKS = ITEMS.register("tar_soaked_planks",
            () -> new com.tarbound.item.TarConduitItem(ModBlocks.TAR_SOAKED_PLANKS.get(), new Item.Properties()));

    public static final DeferredItem<com.tarbound.item.OssuaryItem> OSSUARY = ITEMS.register("ossuary",
            () -> new com.tarbound.item.OssuaryItem(ModBlocks.OSSUARY.get(), new Item.Properties()));

    public static final DeferredItem<com.tarbound.item.ShadeItem> SHADE = ITEMS.register("shade",
            () -> new com.tarbound.item.ShadeItem(ModBlocks.SHADE.get(), new Item.Properties()));

    public static final DeferredItem<com.tarbound.item.TransientAntennaItem> TRANSIENT_ANTENNA = ITEMS.register("transient_antenna",
            () -> new com.tarbound.item.TransientAntennaItem(ModBlocks.TRANSIENT_ANTENNA.get(), new Item.Properties()));

    public static final DeferredItem<com.tarbound.item.PitchCaskItem> PITCH_CASK = ITEMS.register("pitch_cask",
            () -> new com.tarbound.item.PitchCaskItem(ModBlocks.PITCH_CASK.get(), new Item.Properties().stacksTo(16)));

    /** A mob head with a candle burning on it: fed to a Shade, it raises its rank by one. */
    public static final DeferredItem<net.minecraft.world.item.StandingAndWallBlockItem> VOTIVE_SKULL = ITEMS.register("votive_skull",
            () -> new net.minecraft.world.item.StandingAndWallBlockItem(ModBlocks.VOTIVE_SKULL.get(), ModBlocks.VOTIVE_WALL_SKULL.get(),
                    new Item.Properties().stacksTo(16), net.minecraft.core.Direction.DOWN));

    public static final DeferredItem<BlockItem> VOTIVE_RACK = ITEMS.register("votive_rack", () -> new BlockItem(ModBlocks.VOTIVE_RACK.get(), new Item.Properties()) {
        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines,
                net.minecraft.world.item.TooltipFlag flag) {
            lines.add(net.minecraft.network.chat.Component.translatable("item.tarbound.votive_rack.tip")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });

    public static final DeferredItem<BlockItem> WEEPING_SEEP = ITEMS.register("weeping_seep", () -> new BlockItem(ModBlocks.WEEPING_SEEP.get(),
            new Item.Properties()) {
        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines,
                net.minecraft.world.item.TooltipFlag flag) {
            lines.add(net.minecraft.network.chat.Component.translatable("item.tarbound.weeping_seep.tip")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });

    public static final DeferredItem<BlockItem> BOTTOMLESS_SEEP = ITEMS.register("bottomless_seep", () -> new BlockItem(ModBlocks.BOTTOMLESS_SEEP.get(),
            new Item.Properties().rarity(net.minecraft.world.item.Rarity.EPIC)) {
        @Override
        public void appendHoverText(net.minecraft.world.item.ItemStack stack, TooltipContext context, java.util.List<net.minecraft.network.chat.Component> lines,
                net.minecraft.world.item.TooltipFlag flag) {
            lines.add(net.minecraft.network.chat.Component.translatable("item.tarbound.bottomless_seep.tip")
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
    });

    public static final DeferredItem<com.tarbound.item.StewardItem> STEWARD = ITEMS.register("steward",
            () -> new com.tarbound.item.StewardItem(new Item.Properties().stacksTo(1).rarity(net.minecraft.world.item.Rarity.UNCOMMON)));

    private ModItems() {}
}
