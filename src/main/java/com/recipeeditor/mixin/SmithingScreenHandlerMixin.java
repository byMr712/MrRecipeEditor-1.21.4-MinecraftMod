package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.recipe.RecipePropertySet;
import net.minecraft.screen.SmithingScreenHandler;
import net.minecraft.screen.slot.ForgingSlotsManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SmithingScreenHandler.class)
public abstract class SmithingScreenHandlerMixin {

    @Shadow @Final private RecipePropertySet templatePropertySet;
    @Shadow @Final private RecipePropertySet basePropertySet;
    @Shadow @Final private RecipePropertySet additionPropertySet;

    @Inject(method = "createForgingSlotsManager", at = @At("HEAD"), cancellable = true)
    private static void onCreateForgingSlotsManager(RecipeManager recipeManager, CallbackInfoReturnable<ForgingSlotsManager> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        RecipePropertySet base = recipeManager.getPropertySet(RecipePropertySet.SMITHING_BASE);
        RecipePropertySet template = recipeManager.getPropertySet(RecipePropertySet.SMITHING_TEMPLATE);
        RecipePropertySet addition = recipeManager.getPropertySet(RecipePropertySet.SMITHING_ADDITION);

        ForgingSlotsManager manager = ForgingSlotsManager.builder()
                .input(0, 8, 48, stack -> template.canUse(stack) || CustomRecipeDispatcher.isCustomSmithingTemplate(stack))
                .input(1, 26, 48, stack -> base.canUse(stack) || CustomRecipeDispatcher.isCustomSmithingBase(stack))
                .input(2, 44, 48, stack -> addition.canUse(stack) || CustomRecipeDispatcher.isCustomSmithingAddition(stack))
                .output(3, 98, 48)
                .build();
        cir.setReturnValue(manager);
    }

    @Inject(method = "isValidIngredient", at = @At("HEAD"), cancellable = true)
    private void onIsValidIngredient(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        if (stack == null || stack.isEmpty()) return;

        SmithingScreenHandler handler = (SmithingScreenHandler) (Object) this;
        if ((this.templatePropertySet.canUse(stack) || CustomRecipeDispatcher.isCustomSmithingTemplate(stack)) && !handler.getSlot(0).hasStack()) {
            cir.setReturnValue(true);
            return;
        }
        if ((this.basePropertySet.canUse(stack) || CustomRecipeDispatcher.isCustomSmithingBase(stack)) && !handler.getSlot(1).hasStack()) {
            cir.setReturnValue(true);
            return;
        }
        if ((this.additionPropertySet.canUse(stack) || CustomRecipeDispatcher.isCustomSmithingAddition(stack)) && !handler.getSlot(2).hasStack()) {
            cir.setReturnValue(true);
            return;
        }
    }
}
