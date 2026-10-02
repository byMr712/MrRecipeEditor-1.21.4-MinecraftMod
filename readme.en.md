> **Language:** [Русский](readme.md) · English

# Recipe Editor (Minecraft 1.21.4 Fabric)

![Java 21](https://img.shields.io/badge/Java-21-blue.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-blue.svg)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)
![ModMenu](https://img.shields.io/badge/ModMenu-Supported-blue.svg)
![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)

Universal in-game recipe editor and creation studio for **Minecraft 1.21.4 (Fabric)**.

---

## About

**Recipe Editor** provides a comprehensive in-game graphical user interface to inspect, decompile, create, and customize crafting recipes for any item (vanilla or modded). The mod supports 7 workstation types, Drag & Drop controls, item tags, automatic recipe conflict prevention, and client-server network synchronization on dedicated servers.

---

## Gallery

| Recipe Editor | Configuration Menu |
|:---:|:---:|
| ![Recipe Editor](/images/totemcraft.png) | ![Configuration Menu](/images/totemcraft_modmenu.png) |

---

## Features

- **7 Workstation Types Supported:**
  - **Crafting Table** — 3x3 grid for shaped crafting recipes.
  - **Furnace** — item smelting with configurable time and experience yield.
  - **Blast Furnace** — high-speed smelting for ores and equipment.
  - **Smoker** — accelerated cooking for food items.
  - **Stonecutter** — block cutting with dynamic recipe button list generation in the UI.
  - **Smithing Table** — equipment transformation with 3 slots (template, base, addition).
  - **Campfire** — campfire food cooking.
- **Mouse Controls & Drag & Drop:**
  - Drag items from catalog into crafting slots (LMB).
  - Quick decompilation of an item's existing recipe from the catalog (RMB).
  - Clear selected slot with RMB or `Del` / `Backspace` key.
- **First-Class Tag Support (`#tag`):**
  - Full support for vanilla and modded tags (e.g., `#minecraft:planks`, `#c:iron_ores`, `#minecraft:sand`).
  - Dynamic matching validation using `Ingredient.test()`.
- **Recipe Conflict Protection:**
  - Automatic collision checks against vanilla and modded recipes upon saving.
  - Informative blocking modal with visual recipe preview.
- **Multi-Variant Recipes:**
  - Create multiple alternative recipe variants for the same item.
- **Network Synchronization (Dedicated Server & LAN):**
  - Network protocol based on Fabric Networking API (`UpdateRecipeC2SPacket` / `SyncRecipesS2CPacket`).
  - Operator permission checks (level 2) on servers and live broadcast updates to all connected players.
- **Item Catalog & Filtering:**
  - Filter modes: "All", "Uncraftable", "Craftable", "Custom".
  - Horizontally scrollable Mod Tabs for instant filtering by specific mods.
  - Full-text search by localized names and raw item IDs.
  - Responsive grid layout adapting to screen size and GUI scale.
- **Recipe Viewer Integration:**
  - Compatible with JEI, REI, EMI, and the vanilla Recipe Book.

---

## Controls

| Action | Description |
|---|---|
| **LMB on item** | Select item or drag it into a crafting slot |
| **RMB on catalog item** | Load and decompile the item's recipe into the editor |
| **RMB on crafting slot** | Clear the clicked slot |
| **`Del` / `Backspace` Key** | Clear active slot (or reset target item if result slot is selected) |
| **Mouse Scroll Wheel** | Scroll through catalog pages and mod tabs |
| **`◀` / `▶` Arrow Buttons** | Switch workstation pages and recipe variants |

---

## Configuration

Configuration file is saved at: `config/recipeeditor.json`.

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

---

## Installation

1. Download the latest release from [GitHub Releases](https://github.com/byMr712/RecipeEditor-MinecraftMod/releases).
2. Requires:
   - [Fabric API](https://modrinth.com/mod/fabric-api)
   - [Mod Menu](https://modrinth.com/mod/modmenu)
3. Place the `.jar` file into your `mods` folder.
4. Launch the game.

---

## Building

1. Requires Java 21 and Fabric Loader for Minecraft 1.21.4.
2. To build the project, run:
   ```bash
   ./gradlew build
   ```
3. The built file will be located at `build/libs/RecipeEditor-1.21.4-byMr712.jar`.

---

## Credits & License

- Developer: [Mr712](https://github.com/byMr712).
- Distributed under the [Apache License 2.0](LICENSE).
