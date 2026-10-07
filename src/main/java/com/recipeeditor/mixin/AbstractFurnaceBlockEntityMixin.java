package com.recipeeditor.mixin;

import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.AbstractCookingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.SingleStackRecipeInput;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class AbstractFurnaceBlockEntityMixin {

    @Inject(method = "canAcceptRecipeOutput", at = @At("HEAD"), cancellable = true)
    private static void onCanAcceptRecipeOutput(DynamicRegistryManager registryManager, RecipeEntry<? extends AbstractCookingRecipe> recipe, SingleStackRecipeInput input, DefaultedList<ItemStack> slots, int count, CallbackInfoReturnable<Boolean> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;

        if (slots.get(0).isEmpty() || recipe == null) {
            cir.setReturnValue(false);
            return;
        }

        ItemStack result = recipe.value().craft(input, registryManager);
        if (result.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        ItemStack output = slots.get(2);
        if (output.isEmpty()) {
            cir.setReturnValue(true);
            return;
        }

        if (!ItemStack.areItemsAndComponentsEqual(output, result)) {
            cir.setReturnValue(false);
            return;
        }

        int maxAllowed = Math.min(count, Math.min(output.getMaxCount(), result.getMaxCount()));
        cir.setReturnValue(output.getCount() + result.getCount() <= maxAllowed);
    }

    @Inject(method = "craftRecipe", at = @At("HEAD"), cancellable = true)
    private static void onCraftRecipe(DynamicRegistryManager registryManager, RecipeEntry<? extends AbstractCookingRecipe> recipe, SingleStackRecipeInput input, DefaultedList<ItemStack> slots, int count, CallbackInfoReturnable<Boolean> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) return;

        if (recipe == null || slots.get(0).isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        ItemStack resultStack = recipe.value().craft(input, registryManager);
        if (resultStack.isEmpty()) {
            cir.setReturnValue(false);
            return;
        }

        ItemStack outputStack = slots.get(2);
        if (!outputStack.isEmpty()) {
            if (!ItemStack.areItemsAndComponentsEqual(outputStack, resultStack)) {
                cir.setReturnValue(false);
                return;
            }
            int maxAllowed = Math.min(count, Math.min(outputStack.getMaxCount(), resultStack.getMaxCount()));
            if (outputStack.getCount() + resultStack.getCount() > maxAllowed) {
                cir.setReturnValue(false);
                return;
            }
        }

        ItemStack inputStack = slots.get(0);
        if (outputStack.isEmpty()) {
            slots.set(2, resultStack.copy());
        } else {
            outputStack.increment(resultStack.getCount());
        }

        if (inputStack.isOf(Items.WET_SPONGE) && !slots.get(1).isEmpty() && slots.get(1).isOf(Items.BUCKET)) {
            slots.set(1, new ItemStack(Items.WATER_BUCKET));
        }

        inputStack.decrement(1);
        cir.setReturnValue(true);
    }
}
