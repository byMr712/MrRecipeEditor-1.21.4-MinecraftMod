package com.recipeeditor.mixin;

import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBook.class)
public interface RecipeBookInvoker {
    @Invoker("remove")
    void recipeeditor$invokeRemove(Identifier id);
}
