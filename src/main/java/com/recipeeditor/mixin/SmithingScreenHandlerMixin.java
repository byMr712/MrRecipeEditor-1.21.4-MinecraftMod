package com.recipeeditor.mixin;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.RecipePropertySet;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SmithingMenu.class)
public abstract class SmithingScreenHandlerMixin {

    @Shadow @Final private RecipePropertySet templateItemTest;
    @Shadow @Final private RecipePropertySet baseItemTest;
    @Shadow @Final private RecipePropertySet additionItemTest;

    @Inject(method = "createInputSlotDefinitions", at = @At("HEAD"), cancellable = true)
    private static void onCreateInputSlotDefinitions(RecipeAccess recipeAccess, CallbackInfoReturnable<ItemCombinerMenuSlotDefinition> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        RecipePropertySet base = recipeAccess.propertySet(RecipePropertySet.SMITHING_BASE);
        RecipePropertySet template = recipeAccess.propertySet(RecipePropertySet.SMITHING_TEMPLATE);
        RecipePropertySet addition = recipeAccess.propertySet(RecipePropertySet.SMITHING_ADDITION);

        ItemCombinerMenuSlotDefinition manager = ItemCombinerMenuSlotDefinition.create()
                .withSlot(0, 8, 48, stack -> template.test(stack) || CustomRecipeDispatcher.isCustomSmithingTemplate(stack))
                .withSlot(1, 26, 48, stack -> base.test(stack) || CustomRecipeDispatcher.isCustomSmithingBase(stack))
                .withSlot(2, 44, 48, stack -> addition.test(stack) || CustomRecipeDispatcher.isCustomSmithingAddition(stack))
                .withResultSlot(3, 98, 48)
                .build();
        cir.setReturnValue(manager);
    }

    @Inject(method = "canMoveIntoInputSlots", at = @At("HEAD"), cancellable = true)
    private void onCanMoveIntoInputSlots(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;
        if (stack == null || stack.isEmpty()) return;

        SmithingMenu handler = (SmithingMenu) (Object) this;
        if ((this.templateItemTest.test(stack) || CustomRecipeDispatcher.isCustomSmithingTemplate(stack)) && !handler.getSlot(0).hasItem()) {
            cir.setReturnValue(true);
            return;
        }
        if ((this.baseItemTest.test(stack) || CustomRecipeDispatcher.isCustomSmithingBase(stack)) && !handler.getSlot(1).hasItem()) {
            cir.setReturnValue(true);
            return;
        }
        if ((this.additionItemTest.test(stack) || CustomRecipeDispatcher.isCustomSmithingAddition(stack)) && !handler.getSlot(2).hasItem()) {
            cir.setReturnValue(true);
            return;
        }
    }
}
