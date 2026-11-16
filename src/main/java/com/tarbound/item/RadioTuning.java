package com.tarbound.item;

import com.tarbound.block.ScryingFontBlock;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModSounds;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Tuning items to a Scrying Font: shift-right-click the radio with Tar Antennas or Reliquaries in hand
 * and the whole stack is tuned to it. Tuned blocks bind to that radio from anywhere in its dimension
 * while loaded; they find each other through a registry, never by searching the world.
 */
public final class RadioTuning {
    private RadioTuning() {}

    /** @return a result if this use was a tuning click (handled), otherwise null */
    @Nullable
    public static InteractionResult tryTune(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (player == null || !player.isSecondaryUseActive() || !(level.getBlockState(pos).getBlock() instanceof ScryingFontBlock)) {
            return null;
        }
        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            GlobalPos target = GlobalPos.of(level.dimension(), pos.immutable());
            java.util.UUID id = com.tarbound.network.RadioRegistry.idAt(level, pos);
            boolean already = target.equals(stack.get(ModDataComponents.RADIO_LINK.get()))
                    && java.util.Objects.equals(id, stack.get(ModDataComponents.RADIO_ID.get()));
            stack.set(ModDataComponents.RADIO_LINK.get(), target);
            if (id != null) {
                stack.set(ModDataComponents.RADIO_ID.get(), id); // follows the radio if it's moved
            } else {
                stack.remove(ModDataComponents.RADIO_ID.get());
            }
            level.playSound(null, pos, ModSounds.FONT_TUNE.get(), SoundSource.BLOCKS, 0.6F, 1.3F);
            player.displayClientMessage(Component.translatable(
                    already ? "message.tarbound.already_tuned" : "message.tarbound.tuned",
                    stack.getHoverName(), pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.DARK_AQUA), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Tooltip line saying which radio (if any) a stack is tuned to. */
    public static void appendTuning(ItemStack stack, List<Component> tooltip) {
        GlobalPos link = stack.get(ModDataComponents.RADIO_LINK.get());
        if (link == null) {
            tooltip.add(Component.translatable("tooltip.tarbound.untuned").withStyle(ChatFormatting.GRAY));
        } else {
            BlockPos p = link.pos();
            tooltip.add(Component.translatable("tooltip.tarbound.tuned", p.getX(), p.getY(), p.getZ(),
                    link.dimension().location().toString()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
