package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceScreenHandler.class)
public class AbstractFurnaceScreenHandlerMixin {

    @Shadow @Final private RecipeType<? extends AbstractCookingRecipe> recipeType;
    @Shadow @Final protected World world;

    @Inject(method = "isSmeltable", at = @At("HEAD"), cancellable = true)
    private void onIsSmeltable(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (!itemStack.isEmpty() && this.recipeType != null && this.world != null) {
            if (CustomRecipeDispatcher.getCustomMatch(this.recipeType, new SingleStackRecipeInput(itemStack), this.world).isPresent()) {
                cir.setReturnValue(true);
            }
        }
    }
}
