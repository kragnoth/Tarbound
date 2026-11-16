package com.tarbound.item;

import com.tarbound.entity.StockTarget;
import com.tarbound.entity.Steward;
import com.tarbound.registry.ModDataComponents;
import com.tarbound.registry.ModEntities;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** A Steward, carried: shift-right-click a Scrying Font to tune it, then right-click a block to set it down there. */
public class StewardItem extends Item {
    public StewardItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult tuned = RadioTuning.tryTune(context);
        if (tuned != null) {
            return tuned;
        }
        Level level = context.getLevel();
        BlockPos at = context.getClickedPos();
        if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
            at = at.relative(context.getClickedFace());
        }
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        Steward steward = ModEntities.STEWARD.get().create(server, null, at, MobSpawnType.SPAWN_EGG, true, false);
        if (steward == null || !level.noCollision(steward, steward.getBoundingBox())) {
            return InteractionResult.FAIL;
        }
        Player player = context.getPlayer();
        float yaw = player != null ? player.getYRot() + 180.0F : 0.0F;
        steward.moveTo(steward.getX(), steward.getY(), steward.getZ(), yaw, 0.0F);
        steward.setYHeadRot(yaw);
        steward.yBodyRot = yaw;
        steward.tuneTo(stack.get(ModDataComponents.RADIO_LINK.get()), stack.get(ModDataComponents.RADIO_ID.get()));
        steward.setTargets(stack.getOrDefault(ModDataComponents.STEWARD_TARGETS.get(), List.of()));
        Component name = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        if (name != null) {
            steward.setCustomName(name);
        }
        server.addFreshEntityWithPassengers(steward);
        level.playSound(null, at, SoundEvents.HONEY_BLOCK_PLACE, SoundSource.NEUTRAL, 0.8F, 0.5F);
        level.gameEvent(player, GameEvent.ENTITY_PLACE, at);
        stack.consume(1, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        RadioTuning.appendTuning(stack, tooltip);
        List<StockTarget> targets = stack.getOrDefault(ModDataComponents.STEWARD_TARGETS.get(), List.of());
        if (!targets.isEmpty()) {
            tooltip.add(Component.translatable("tooltip.tarbound.steward.keeps", targets.size()).withStyle(ChatFormatting.AQUA));
            for (int i = 0; i < Math.min(4, targets.size()); i++) {
                tooltip.add(Component.translatable("tooltip.tarbound.steward.line", targets.get(i).keep(), targets.get(i).item().getHoverName())
                        .withStyle(ChatFormatting.GRAY));
            }
            if (targets.size() > 4) {
                tooltip.add(Component.translatable("tooltip.tarbound.steward.more", targets.size() - 4).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.tarbound.steward.what").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.tarbound.steward.how").withStyle(ChatFormatting.DARK_GRAY));
    }
}
