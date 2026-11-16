package com.tarbound.item;

import com.tarbound.block.ScryingFontBlockEntity;
import com.tarbound.config.TarboundConfig;
import com.tarbound.menu.ScryingFontMenu;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModItems;
import com.tarbound.registry.ModSounds;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Spirit Board: a talking board tuned to a Scrying Font. Shift-right-click the radio to tune it, then
 * right-click anywhere to open that radio's screen from afar: the pool, the crafting grid, machines and
 * jobs. Works in the radio's dimension while the radio is loaded, within the server's configured range
 * (anywhere, by default).
 */
public class SpiritBoardItem extends Item {
    public SpiritBoardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult tuned = RadioTuning.tryTune(context);
        if (tuned != null) {
            return tuned;
        }
        // Not tuning: use it like a right-click in the air.
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        return use(context.getLevel(), player, context.getHand()).getResult();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        GlobalPos link = stack.get(ModDataComponents.RADIO_LINK.get());
        if (link != null && !level.isClientSide) {
            link = follow(level, stack, link);
        }
        if (link == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("tooltip.tarbound.untuned").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        Component problem = reachProblem(player, link);
        if (problem != null) {
            if (!level.isClientSide) {
                player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        BlockPos pos = link.pos();
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof com.tarbound.block.ScryingFontBlockEntity font
                && !font.canPay(TarboundConfig.SERVER.boardCostPerSecond.get())) {
            player.displayClientMessage(Component.translatable("message.tarbound.board.hungry").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide) {
            if (TarboundConfig.CLIENT.radioSounds.get()) {
                player.playSound(ModSounds.FONT_TUNE.get(), 0.4F, 1.2F);
            }
        } else {
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, p) -> new ScryingFontMenu(id, inventory, ContainerLevelAccess.create(level, pos), pos, true),
                    Component.translatable("container.tarbound.scrying_font")), pos);
            com.tarbound.TarboundAdvancements.award(player, com.tarbound.TarboundAdvancements.SPIRIT_BOARD);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /**
     * If the board's radio was moved (broken and placed again) within this dimension, points the board
     * at where it is now. Cheap: one lookup by the radio's id among loaded radios.
     */
    private static GlobalPos follow(Level level, ItemStack stack, GlobalPos link) {
        java.util.UUID id = stack.get(ModDataComponents.RADIO_ID.get());
        if (id == null) {
            return link;
        }
        BlockPos now = com.tarbound.network.RadioRegistry.find(level, id);
        if (now == null || (now.equals(link.pos()) && link.dimension().equals(level.dimension()))) {
            return link;
        }
        GlobalPos moved = GlobalPos.of(level.dimension(), now);
        stack.set(ModDataComponents.RADIO_LINK.get(), moved);
        return moved;
    }

    /** Why the board can't reach its radio right now, or null if it can. */
    @Nullable
    public static Component reachProblem(Player player, GlobalPos link) {
        Level level = player.level();
        if (!link.dimension().equals(level.dimension())) {
            return Component.translatable("message.tarbound.board.other_dimension");
        }
        int range = TarboundConfig.SERVER.spiritBoardRange.get();
        if (range > 0 && player.blockPosition().distSqr(link.pos()) > (double) range * range) {
            return Component.translatable("message.tarbound.board.too_far", range);
        }
        if (!level.isClientSide && (!level.isLoaded(link.pos()) || !(level.getBlockEntity(link.pos()) instanceof ScryingFontBlockEntity))) {
            return Component.translatable("message.tarbound.board.silent");
        }
        return null;
    }

    /** The player is carrying a Spirit Board tuned to the radio at {@code radio} (and can still reach it). */
    public static boolean carriesBoardFor(Player player, BlockPos radio) {
        GlobalPos target = GlobalPos.of(player.level().dimension(), radio);
        for (ItemStack s : player.getInventory().items) {
            if (s.is(ModItems.SPIRIT_BOARD.get()) && target.equals(s.get(ModDataComponents.RADIO_LINK.get()))) {
                return reachProblem(player, target) == null;
            }
        }
        ItemStack off = player.getOffhandItem();
        return off.is(ModItems.SPIRIT_BOARD.get()) && target.equals(off.get(ModDataComponents.RADIO_LINK.get()))
                && reachProblem(player, target) == null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RadioTuning.appendTuning(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.tarbound.spirit_board").withStyle(ChatFormatting.DARK_GRAY));
    }
}
