> **Language:** [Русский](readme.md) · English

# [MR] Recipe Editor

![Java 21](https://img.shields.io/badge/Java-21-blue.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.3-blue.svg)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)
![ModMenu](https://img.shields.io/badge/ModMenu-Supported-blue.svg)
![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)

## About

**[MR] Recipe Editor** is a handy in-game recipe creator directly inside Minecraft! No more dealing with complex configuration files or datapacks: simply open the editor screen via **Mod Menu** (in your pause/mods menu), drag & drop any item with your mouse, and design your own crafting recipes for any item — from vanilla Minecraft or any installed mods.

## Gallery

![Configuration Menu](/images/RecipeEditor_modmenu_en.png)

## What can this mod do?

### Mod Integration & Recipe Viewers
- Modify recipes from any installed mods! Everything is neatly sorted into mod tabs.
- Created and overridden recipes are fully integrated into Minecraft's vanilla Recipe Book (crafting table and furnaces) with instant one-click autofill support.
- Recipe viewer support: official integration with **[Roughly Enough Items (REI)](https://www.curseforge.com/minecraft/mc-mods/roughly-enough-items)** via its client API — all custom recipes appear immediately in recipes and usages searches, and overridden recipes are properly hidden.

### Supports 7 Workstations & Tables
- **Crafting Table** — convenient 3x3 grid. Smaller recipes (2x2 or 1x2, like torches or sticks) can be crafted anywhere on the table grid.
- **Furnace** — smelt ores and items with convenient cooking time adjustments (with live seconds tooltip) and experience reward.
- **Blast Furnace** — fast smelting for ores, armor, and tools.
- **Smoker** — fast cooking for food items.
- **Stonecutter** — precise block cutting.
- **Smithing Table** — upgrade gear with template, base, and material slots.
- **Campfire** — simple food roasting without burning fuel.

### Intuitive Controls & Features
- **Mouse Drag & Drop:** Hold Left Mouse Button (LMB) on any item in the catalog to drag into crafting slots.
- **Recipe Inspector:** Right Mouse Button (RMB) on any catalog item instantly loads its existing recipe.
- **Override & Restore Original Recipes:** Modifying an existing recipe cleanly overrides the original craft in-game without creating duplicate variants. Deleting a custom recipe from "My Crafts" instantly restores the original vanilla or modded recipe in both the game and editor!
- **Multiple recipe variants:** create alternative ways to craft your favorite item using the `+` button and easily switch between them using the arrow keys or your mouse wheel right above the crafting window (both methods work in-game).
- **Clear Slots:** Right-click a crafting slot or press the `Delete` key.
- **Recipe Conflict Protection:** Alerts you if a crafting shape is already occupied by another item.
- **Fast Search & Catalog:** Filter by All, Uncraftable, Craftable, or My Crafts with instant search in any language and mod tabs scrollable by mouse wheel.

### Current Limitations
- When loading and modifying existing recipes, item group (tag) support is preserved (e.g. any wood planks or wool). Creating a brand new recipe from scratch currently does not support assigning custom tags.

## Controls

| Action | Description |
|---|---|
| **Open Editor Screen** | Open via **Mod Menu** in the Esc pause menu or main menu |
| **LMB on item** | Select item or drag it into a crafting slot |
| **RMB on catalog item** | Load and decompile the item's recipe into the editor |
| **RMB on crafting slot** | Clear the clicked slot |
| **`Del` / `Backspace` Key** | Clear active slot (or reset target item if result slot is selected) |
| **`I` Key** | Toggle hints on or off |
| **Mouse Scroll Wheel** | Scroll catalog pages and mod tabs, or cycle recipe variants when hovering over the crafting grid |
| **`◀` / `▶` Arrow Buttons** | Switch workstation pages and recipe variants |

## Configuration

Configuration file is saved at: `config/mrrecipeeditor.json`.

```json
{
  "modEnabled": true,
  "recipes": {
    "minecraft:totem_of_undying#SHAPED_CRAFTING#minecraft:golden_apple,...": {
      "id": "totem_of_undying_shaped",
      "resultItemId": "minecraft:totem_of_undying",
      "resultCount": 1,
      "type": "SHAPED_CRAFTING",
      "patternSlots": [
        "minecraft:golden_apple", "minecraft:golden_apple", "minecraft:golden_apple",
        "minecraft:golden_apple", "minecraft:ghast_tear",   "minecraft:golden_apple",
        "minecraft:golden_apple", "minecraft:golden_apple", "minecraft:golden_apple"
      ],
      "experience": 0.1,
      "cookingTime": 200,
      "enabled": true,
      "overrideExisting": false
    }
  }
}
```

## Installation

1. Download the latest release from [CurseForge](https://www.curseforge.com/minecraft/mc-mods/mr-recipe-editor) or [GitHub Releases](https://github.com/byMr712/MrRecipeEditor-MinecraftMod/releases).
2. Requires:
   - [Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
   - [Mod Menu](https://www.curseforge.com/minecraft/mc-mods/modmenu)
3. Place the `.jar` file into your `mods` folder.
4. Launch the game.

## Building

1. Requires Java 21 and Fabric Loader for Minecraft 1.21.3.
2. To build the project, run:
   ```bash
   ./gradlew build
   ```
3. The built file will be located at `build/libs/MrRecipeEditor-Fabric-1.21.4-byMr712-v1.3.jar`.

## Credits & License

- Developer: [Mr712](https://github.com/byMr712).
- Distributed under the [Apache License 2.0](LICENSE).
