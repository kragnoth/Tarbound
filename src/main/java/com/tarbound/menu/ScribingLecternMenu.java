package com.tarbound.menu;

import com.tarbound.block.ScribingLecternBlockEntity;
import com.tarbound.contract.ContractData;
import com.tarbound.contract.CraftingContracts;
import com.tarbound.contract.StandingOrder;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import com.tarbound.registry.ModBlocks;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModItems;
import com.tarbound.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * The Scribing Lectern, a pattern terminal for Spirit Contracts. A blank contract goes in on the left; the
 * contract is laid out in ghost slots (by hand, by dragging from JEI/EMI, or with JEI/EMI's + button), in
 * crafting mode (a 3x3 grid and its result) or processing mode (machine inputs and outputs), and Write turns
 * the blank into it, on the right. A written contract with a single input can then be generalized to one of
 * that input's item tags, making it a standing contract.
 *
 * <p>The layout ({@link #draft}) lives on the client only; Write sends it to the server, which checks it.
 */
public class ScribingLecternMenu extends AbstractContainerMenu {
    public static final int IMAGE_WIDTH = 176;
    public static final int IMAGE_HEIGHT = 216;
    public static final int BLANK = 0;
    public static final int WRITTEN = 1;
    public static final int BLANK_X = 8;
    public static final int WRITTEN_X = 152;
    public static final int SLOT_Y = 20;
    /** The ghost grid (crafting grid, or processing inputs): 3x3 from here. */
    public static final int GRID_X = 30;
    public static final int GRID_Y = 20;
    /** Processing outputs: 2 across, 3 down from here; the crafting result sits in the middle of them. */
    public static final int OUT_X = 108;
    public static final int OUT_Y = 20;
    public static final int INV_Y = 134;

    /** The contract being laid out (client side; the server's copy is never used). */
    public final com.tarbound.contract.ContractDraft draft = new com.tarbound.contract.ContractDraft();

    private final ContainerLevelAccess access;
    private final IItemHandlerModifiable pages;

    public ScribingLecternMenu(int containerId, Inventory inventory, IItemHandlerModifiable pages, ContainerLevelAccess access) {
        super(ModMenus.SCRIBING_LECTERN.get(), containerId);
        this.access = access;
        this.pages = pages;
        addSlot(new SlotItemHandler(pages, BLANK, BLANK_X, SLOT_Y));
        // Written slot: filled by writing, or by hand with an existing contract to generalize.
        addSlot(new SlotItemHandler(pages, WRITTEN, WRITTEN_X, SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INV_Y + 58));
        }
    }

    public static ScribingLecternMenu fromNetwork(int containerId, Inventory inventory, RegistryFriendlyByteBuf buf) {
        return new ScribingLecternMenu(containerId, inventory, ScribingLecternBlockEntity.newPages(null), ContainerLevelAccess.NULL);
    }

    public boolean hasBlank() {
        return getSlot(BLANK).getItem().is(ModItems.BLANK_CONTRACT.get());
    }

    public boolean writtenSlotFree() {
        return getSlot(WRITTEN).getItem().isEmpty();
    }

    /**
     * Server: turn one blank contract into a written one. For a crafting-table recipe the contract is
     * built from the server's own copy of the recipe and remembers its id, so crafting stations can run it.
     */
    public void write(ServerPlayer player, ContractData data, @javax.annotation.Nullable ResourceLocation craftingRecipe) {
        if (craftingRecipe != null) {
            var holder = CraftingContracts.recipe(player.level(), craftingRecipe);
            if (holder == null) {
                player.displayClientMessage(Component.translatable("gui.tarbound.lectern.crafting"), true);
                return;
            }
            data = CraftingContracts.describe(holder.value(), player.level().registryAccess());
        } else {
            data = data.checkedTags(); // a client-written contract only keeps tags that fit what it lists
        }
        finish(player, data, craftingRecipe, false);
    }

    /**
     * Server: write the crafting recipe the ghost grid makes. With substitutes, the contract takes anything the
     * recipe accepts; without, only the exact items in the grid (and crafting stations keep to them).
     */
    public void writeCrafting(ServerPlayer player, java.util.List<ItemStack> grid, boolean substitutes) {
        var level = player.level();
        java.util.List<ItemStack> ones = grid.stream().map(s -> s.isEmpty() ? ItemStack.EMPTY : s.copyWithCount(1)).toList();
        var input = net.minecraft.world.item.crafting.CraftingInput.of(3, 3, ones);
        var found = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
        if (found.isEmpty()) {
            player.displayClientMessage(Component.translatable("gui.tarbound.lectern.no_recipe"), true);
            return;
        }
        var holder = CraftingContracts.recipe(level, found.get().id());
        if (holder == null) {
            player.displayClientMessage(Component.translatable("gui.tarbound.lectern.crafting"), true);
            return;
        }
        ContractData data = substitutes
                ? CraftingContracts.describe(holder.value(), level.registryAccess())
                : CraftingContracts.withReturns(com.tarbound.contract.ContractDraft.exactCrafting(ones, holder.value().getResultItem(level.registryAccess())),
                        holder.value(), input);
        finish(player, data, holder.id(), !substitutes);
    }

    private void finish(ServerPlayer player, ContractData data, @javax.annotation.Nullable ResourceLocation craftingRecipe, boolean exact) {
        if (!data.isValid()) {
            player.displayClientMessage(Component.translatable("gui.tarbound.lectern.nothing"), true);
            return;
        }
        if (!hasBlank()) {
            player.displayClientMessage(Component.translatable("gui.tarbound.lectern.need_blank"), true);
            return;
        }
        if (!writtenSlotFree()) {
            player.displayClientMessage(Component.translatable("gui.tarbound.lectern.take_written"), true);
            return;
        }
        ItemStack blank = pages.getStackInSlot(BLANK).copy();
        blank.shrink(1);
        pages.setStackInSlot(BLANK, blank.isEmpty() ? ItemStack.EMPTY : blank);
        ItemStack written = new ItemStack(ModItems.SPIRIT_CONTRACT.get());
        written.set(ModDataComponents.CONTRACT.get(), data);
        if (craftingRecipe != null) {
            written.set(ModDataComponents.CRAFTING_RECIPE.get(), craftingRecipe);
            if (exact) {
                written.set(ModDataComponents.EXACT_ITEMS.get(), true);
            }
        }
        pages.setStackInSlot(WRITTEN, written);
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.8F));
        broadcastChanges();
    }

    /** The written slot's contract, if it can be generalized (exactly one input). */
    public ContractData generalizable() {
        ItemStack written = getSlot(WRITTEN).getItem();
        if (written.has(ModDataComponents.CRAFTING_RECIPE.get())) {
            return null; // crafting-table contracts run on crafting stations, which only take exact recipes
        }
        ContractData data = written.is(ModItems.SPIRIT_CONTRACT.get()) ? written.get(ModDataComponents.CONTRACT.get()) : null;
        return data != null && data.isValid() && data.inputs().size() == 1 ? data : null;
    }

    /**
     * Item tags every alternative of the contract's one input belongs to, with more than one member.
     * Create's tags come first, then the common {@code c:} tags, then the rest alphabetically.
     */
    public static List<TagKey<Item>> candidateTags(ContractData data) {
        if (data == null || data.inputs().size() != 1 || data.inputs().get(0).isEmpty()) {
            return List.of();
        }
        List<ItemStack> alts = data.inputs().get(0);
        return alts.get(0).getTags()
                .filter(t -> alts.stream().allMatch(a -> a.is(t)))
                .filter(t -> BuiltInRegistries.ITEM.getTag(t).map(HolderSet::size).orElse(0) > 1)
                .sorted(Comparator.comparingInt((TagKey<Item> t) -> switch (t.location().getNamespace()) {
                    case "create" -> 0;
                    case "c" -> 1;
                    default -> 2;
                }).thenComparing(t -> t.location().toString()))
                .toList();
    }

    /** Server: turn the written one-input contract into a standing contract for {@code tagId}. */
    public void generalize(ServerPlayer player, ResourceLocation tagId) {
        ContractData data = generalizable();
        if (data == null) {
            return;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
        if (!candidateTags(data).contains(tag)) {
            player.displayClientMessage(Component.translatable("gui.tarbound.lectern.bad_tag", "#" + tagId), true);
            return;
        }
        ItemStack standing = new ItemStack(ModItems.SPIRIT_CONTRACT.get());
        standing.set(ModDataComponents.STANDING.get(), new StandingOrder(tag, Math.max(1, data.inputs().get(0).get(0).getCount())));
        pages.setStackInSlot(WRITTEN, standing);
        access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.6F));
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index <= WRITTEN) {
            if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(ModItems.BLANK_CONTRACT.get())) {
            if (!moveItemStackTo(stack, BLANK, BLANK + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(ModItems.SPIRIT_CONTRACT.get()) || !moveItemStackTo(stack, WRITTEN, WRITTEN + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.SCRIBING_LECTERN.get());
    }
}
