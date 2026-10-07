package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CampfireBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CampfireCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.stat.Stats;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(CampfireBlock.class)
public class CampfireBlockMixin {

    @Inject(method = "onUseWithItem", at = @At("HEAD"), cancellable = true)
    private void onUseWithItem(
            ItemStack stack,
            BlockState state,
            World world,
            BlockPos pos,
            PlayerEntity player,
            Hand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<ItemActionResult> cir
    ) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof CampfireBlockEntity campfireBlockEntity) {
            ItemStack itemStack = player.getStackInHand(hand);
            if (!itemStack.isEmpty()) {
                SingleStackRecipeInput input = new SingleStackRecipeInput(itemStack);
                Optional<RecipeEntry<CampfireCookingRecipe>> custom = CustomRecipeDispatcher.getCustomMatch(RecipeType.CAMPFIRE_COOKING, input, world);
                if (custom.isPresent()) {
                    if (world instanceof ServerWorld serverWorld) {
                        int cookTime = custom.get().value().getCookingTime();
                        if (campfireBlockEntity.addItem(player, itemStack, cookTime)) {
                            player.incrementStat(Stats.INTERACT_WITH_CAMPFIRE);
                            cir.setReturnValue(ItemActionResult.SUCCESS);
                        } else {
                            cir.setReturnValue(ItemActionResult.CONSUME);
                        }
                    } else {
                        cir.setReturnValue(ItemActionResult.CONSUME);
                    }
                    return;
                }
            }
        }
    }
}
