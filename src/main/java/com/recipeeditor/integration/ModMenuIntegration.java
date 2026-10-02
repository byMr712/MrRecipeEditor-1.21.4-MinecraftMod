package com.recipeeditor.integration;

import com.recipeeditor.client.gui.RecipeEditorScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return RecipeEditorScreen::new;
    }
}
