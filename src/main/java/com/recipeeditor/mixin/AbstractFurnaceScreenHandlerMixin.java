package com.recipeeditor.mixin;

import com.recipeeditor.config.RecipeTypeEnum;
import com.recipeeditor.inspector.RecipeInspector;
import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractFurnaceMenu.class)
public class AbstractFurnaceScreenHandlerMixin {

    @Shadow @Final private RecipeBookType recipeBookType;
    @Shadow @Final protected Level level;

    @Inject(method = "canSmelt", at = @At("RETURN"), cancellable = true)
    private void onCanSmelt(ItemStack itemStack, CallbackInfoReturnable<Boolean> cir) {
        if (com.recipeeditor.RecipeEditorMod.isDedicatedServer()) {
            return;
        }
        if (!itemStack.isEmpty() && this.recipeBookType != null && this.level != null) {
            RecipeTypeEnum typeEnum = switch (this.recipeBookType) {
                case FURNACE -> RecipeTypeEnum.SMELTING;
                case BLAST_FURNACE -> RecipeTypeEnum.BLASTING;
                case SMOKER -> RecipeTypeEnum.SMOKING;
                default -> null;
            };
            if (typeEnum == null) return;
            RecipeType<?> recipeType = typeEnum.toRecipeType();
            if (recipeType == null) return;

            @SuppressWarnings({"unchecked", "rawtypes"})
            boolean hasCustomMatch = CustomRecipeDispatcher.getCustomMatch((RecipeType) recipeType, new SingleRecipeInput(itemStack), this.level).isPresent();
            if (hasCustomMatch) {
                cir.setReturnValue(true);
                return;
            }

            if (cir.getReturnValue()) {
                if (this.level instanceof ServerLevel serverLevel) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    var match = serverLevel.recipeAccess().getRecipeFor((RecipeType) recipeType, new SingleRecipeInput(itemStack), serverLevel);
                    if (match.isEmpty()) {
                        cir.setReturnValue(false);
                    }
                } else if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
                    if (RecipeInspector.isCookingInputOverridden(typeEnum, itemStack.getItem(), this.level)) {
                        cir.setReturnValue(false);
                    }
                }
            }
        }
    }
}
