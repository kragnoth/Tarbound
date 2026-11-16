package com.tarbound.item;

import com.tarbound.block.ScryingFontBlock;
import com.tarbound.block.TarAntennaBlock;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModSounds;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Tar Antenna item. Shift-right-click a Scrying Font to tune the stack to it; place it on any face.
 * Clicking an existing antenna column adds a piece to its free end (up to three).
 */
public class TarAntennaItem extends BlockItem {
    public TarAntennaItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult tuned = RadioTuning.tryTune(context);
        return tuned != null ? tuned : super.useOn(context);
    }

    /** Clicking any part of an antenna column with room to grow places the new piece at its free end. */
    @Override
    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.replacingClickedOnBlock()
                ? context.getClickedPos()
                : context.getClickedPos().relative(context.getClickedFace().getOpposite());
        BlockState state = level.getBlockState(clicked);
        if (!(state.getBlock() instanceof TarAntennaBlock)) {
            return context;
        }
        Direction facing = state.getValue(TarAntennaBlock.FACING);
        if (state.getValue(TarAntennaBlock.STAGE).size >= TarAntennaBlock.MAX_SEGMENTS) {
            return null;
        }
        BlockPos target = TarAntennaBlock.tipOf(level, clicked, state).relative(facing);
        if (!level.getBlockState(target).canBeReplaced(context)) {
            return null;
        }
        return BlockPlaceContext.at(context, target, facing);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RadioTuning.appendTuning(stack, tooltip);
        tooltip.add(Component.translatable("tooltip.tarbound.antenna.reach").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.tarbound.antenna.mode").withStyle(ChatFormatting.DARK_GRAY));
    }
}
