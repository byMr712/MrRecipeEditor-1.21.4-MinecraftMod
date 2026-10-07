package com.recipeeditor.mixin.client;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface TextFieldWidgetAccessor {
    @Accessor("displayPos")
    int getDisplayPos();
}
