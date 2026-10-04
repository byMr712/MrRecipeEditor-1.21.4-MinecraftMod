package com.recipeeditor.mixin;

import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.inspector.RecipeInspector;
import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.screen.AbstractFurnaceScreenHandler;
import net.minecraft.server.world.ServerWorld;
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

    @Inject(method = "isSmeltable", at = @At("RETURN"), cancellable = true)
    private void onIsSmeltable(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (!itemStack.isEmpty() && this.recipeType != null && this.world != null) {
            boolean hasCustomMatch = CustomRecipeDispatcher.getCustomMatch(this.recipeType, new SingleStackRecipeInput(itemStack), this.world).isPresent();
            if (hasCustomMatch) {
                cir.setReturnValue(true);
                return;
            }

            if (cir.getReturnValue()) {
                if (this.world instanceof ServerWorld serverWorld) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    var match = serverWorld.getRecipeManager().getFirstMatch((RecipeType) this.recipeType, new SingleStackRecipeInput(itemStack), serverWorld);
                    if (match.isEmpty()) {
                        cir.setReturnValue(false);
                    }
                } else {
                    RecipeTypeEnum typeEnum = RecipeTypeEnum.fromRecipeType(this.recipeType);
                    if (typeEnum != null && RecipeInspector.isCookingInputOverridden(typeEnum, itemStack.getItem(), this.world)) {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }
}
