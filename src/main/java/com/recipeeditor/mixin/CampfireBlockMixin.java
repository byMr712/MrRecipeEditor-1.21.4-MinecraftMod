package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(CampfireBlock.class)
public class CampfireBlockMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void onUseItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        if (!state.getValue(CampfireBlock.LIT)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof CampfireBlockEntity campfireBlockEntity) {
            if (player.isShiftKeyDown()) {
                return;
            }
            ItemStack itemStack = player.getItemInHand(hand);
            if (!itemStack.isEmpty()) {
                SingleRecipeInput input = new SingleRecipeInput(itemStack);
                Optional<RecipeHolder<CampfireCookingRecipe>> custom = CustomRecipeDispatcher.getCustomMatch(RecipeType.CAMPFIRE_COOKING, input, level);
                if (custom.isPresent()) {
                    if (level instanceof ServerLevel serverLevel) {
                        if (campfireBlockEntity.placeFood(serverLevel, player, itemStack)) {
                            player.awardStat(Stats.INTERACT_WITH_CAMPFIRE);
                            cir.setReturnValue(InteractionResult.SUCCESS_SERVER);
                            return;
                        } else {
                            cir.setReturnValue(InteractionResult.TRY_WITH_EMPTY_HAND);
                            return;
                        }
                    } else {
                        boolean hasEmptySlot = false;
                        for (ItemStack cooked : campfireBlockEntity.getItems()) {
                            if (cooked.isEmpty()) {
                                hasEmptySlot = true;
                                break;
                            }
                        }
                        if (hasEmptySlot) {
                            cir.setReturnValue(InteractionResult.CONSUME);
                        } else {
                            cir.setReturnValue(InteractionResult.TRY_WITH_EMPTY_HAND);
                        }
                        return;
                    }
                }
            }
        }
    }
}
