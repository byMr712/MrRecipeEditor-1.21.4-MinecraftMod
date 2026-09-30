# TotemCraft

> **Language:** [Русский](readme.md) · English

A lightweight, fully configurable Minecraft mod for the **Fabric 1.21.4** mod loader that introduces a balanced crafting recipe for the **Totem of Undying** with an authentic in-game **Mod Menu** GUI configuration screen, Recipe Book & Recipe Viewer (JEI, REI, EMI, JEB) auto-fill support, and a dedicated custom advancement.

![Java 21](https://img.shields.io/badge/Java-21-blue.svg)
![Minecraft](https://img.shields.io/badge/Minecraft-1.21.4-blue.svg)
![Fabric](https://img.shields.io/badge/Loader-Fabric-blue.svg)
![ModMenu](https://img.shields.io/badge/ModMenu-Supported-blue.svg)
![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)

## Screenshots

| Totem Crafting | ModMenu Configuration |
|:---:|:---:|
| ![Totem Crafting](/images/totemcraft.png) | ![ModMenu Configuration](/images/totemcraft_modmenu.png) |

## Features

- **Balanced Default Recipe:** 8 Golden Apples surrounding a Ghast Tear in the center.
- **In-Game Minecraft GUI Configurator:** Customize the recipe layout directly in-game in authentic vanilla style.
- **Responsive & Dynamic Catalog Size:** The item picker automatically calculates the optimal columns and rows based on your screen resolution and GUI Scale.
- **Mod Filtering Tabs (Mod Tabs):** Quickly switch between "All", "Minecraft" (Vanilla), and individual tabs for every detected mod.
- **Mouse Wheel Scrolling:** Scroll through catalog pages effortlessly using the mouse scroll wheel or navigation buttons.
- **Support for Items from Any Mod:** Built-in item picker catalog with live search enables assigning any item from any installed mod to any crafting grid slot or recipe output.
- **Seamless Recipe Book & Recipe Viewer Integration:** The recipe fully appears in the Recipe Book, JEI, REI, EMI, JEB and supports instant recipe ingredient transfer into crafting tables with a single click.
- **Custom Advancement:** Players unlock the *«Handmade Totem — I will never die!»* advancement upon crafting their first totem.
- **Optional Mod Menu Integration:** If [Mod Menu](https://modrinth.com/mod/modmenu) is present, the settings button seamlessly appears in the mod list. If Mod Menu is absent, the mod operates normally without issues.
- **Live Recipe Updates without Restart:** Recipe adjustments take effect instantly in-game without needing to restart the client, world, or server.
- **Vanilla Client Friendly:** Server-side installation supports unmodded vanilla clients out of the box.
- **Full Localization:** Fully localized in Russian (`ru_ru`) and English (`en_us`).

## Default Crafting Recipe

By default, crafting takes place in a standard 3x3 crafting grid: **8 Golden Apples** placed around **1 Ghast Tear** in the center.

### 3x3 Crafting Grid Layout

```
+---------------------+---------------------+---------------------+
| [Golden Apple] | [Golden Apple] | [Golden Apple] |
+---------------------+---------------------+---------------------+
| [Golden Apple] | [Ghast Tear] | [Golden Apple] | ===> [Totem of Undying] (x1)
+---------------------+---------------------+---------------------+
| [Golden Apple] | [Golden Apple] | [Golden Apple] |
+---------------------+---------------------+---------------------+
```

### Ingredients Table

| Slot | Item | Identifier (ID) | Quantity |
|:---:|---|---|:---:|
| 1-3, 4, 6, 7-9 | **Golden Apple** | `minecraft:golden_apple` | 8 |
| 5 (Center) | **Ghast Tear** | `minecraft:ghast_tear` | 1 |
| **Output** | **Totem of Undying** | `minecraft:totem_of_undying` | 1 |

## Advancement

The mod adds a custom advancement to the "Adventure" tab:

| Icon | Title | Description | Requirement |
|---|---|---|---|
| | **Handmade Totem** | *I will never die!* | Craft a Totem of Undying using the mod's recipe (manually or via an auto-crafter). |

## In-Game Configuration Screen (GUI)

With [Mod Menu](https://modrinth.com/mod/modmenu) installed, navigate to **Main Menu Mods TotemCraft Settings**:

- **Interactive 3x3 Crafting Grid:** Click any slot in the grid to select it (highlighted with a gold border).
- **Result Slot & Output Count:** Select the result slot to change the produced item and adjust output amount with `+` / `-` buttons (1 to 64).
- **Mod Tabs:** Switch between "All", "Minecraft", and third-party mod tabs for instant category filtering.
- **Full-Text Item Search:** Search for items in English, Russian, or by raw ID (e.g. `apple`, `diamond`, `botania:mana_pearl`).
- **Responsive Item Catalog:** Automatically adapts slot capacity to your screen size and supports mouse scroll navigation. Click any item in the catalog to assign it to the active slot.
- **Quick Action Buttons:**
 - `Clear Slot` — clears the active slot (sets to empty / Air).
 - `Fill 8` — fills all 8 outer slots with the item in the active slot.
 - `Clear All` — empties the entire 3x3 grid.
 - `Recipe: ENABLED / DISABLED` — toggle recipe on/off in real-time.
 - `Reset Defaults` — restores 8 golden apples + ghast tear layout.

## Configuration File

Configuration is saved in `config/totemcraft.json`:

```json
{
 "enabled": true,
 "patternSlots": [
 "minecraft:golden_apple",
 "minecraft:golden_apple",
 "minecraft:golden_apple",
 "minecraft:golden_apple",
 "minecraft:ghast_tear",
 "minecraft:golden_apple",
 "minecraft:golden_apple",
 "minecraft:golden_apple",
 "minecraft:golden_apple"
 ],
 "resultItemId": "minecraft:totem_of_undying",
 "resultCount": 1
}
```

## System Requirements

- **Minecraft:** `1.21.4`
- **Mod Loader:** [Fabric Loader](https://fabricmc.net/) `0.16.0+`
- **Java:** `Java 21` or newer
- **Library:** [Fabric API](https://modrinth.com/mod/fabric-api) (recommended)
- **Optional:** [Mod Menu](https://modrinth.com/mod/modmenu) (for opening the in-game GUI settings)

## Installation

### For Singleplayer / Client
1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft **1.21.4**.
2. Place `Fabric API`, `Mod Menu` (optional), and `TotemCraft-1.0.0.jar` in your `.minecraft/mods/` directory.
3. Launch Minecraft.

### For Dedicated Server
1. Set up a Fabric 1.21.4 server.
2. Place `Fabric API` and `TotemCraft-1.0.0.jar` into the `mods/` directory.
3. Restart the server.

## Project Structure

```
TotemCraft-MinecraftMod/
├── gradle/wrapper/ # Gradle Wrapper binaries and config
├── src/
│ └── main/
│ ├── java/
│ │ └── com/totemcraft/
│ │ ├── TotemCraftMod.java # Mod entry point & registry
│ │ ├── client/gui/
│ │ │ └── TotemCraftConfigScreen.java # Responsive Minecraft GUI screen
│ │ ├── config/
│ │ │ └── TotemCraftConfig.java # JSON configuration manager
│ │ ├── integration/
│ │ │ └── ModMenuIntegration.java # Mod Menu API integration
│ │ └── recipe/
│ │ └── TotemCraftCustomRecipe.java # Dynamic crafting recipe logic
│ └── resources/
│ ├── assets/totemcraft/
│ │ ├── icon.png # Mod icon
│ │ └── lang/
│ │ ├── en_us.json # English localization
│ │ └── ru_ru.json # Russian localization
│ ├── data/totemcraft/
│ │ ├── advancement/
│ │ │ ├── craft_totem.json # Totem crafting advancement
│ │ │ └── recipes/
│ │ │ └── totem_of_undying.json # Recipe unlocking trigger
│ │ └── recipe/
│ │ └── totem_of_undying.json # Recipe declaration
│ └── fabric.mod.json # Fabric mod metadata
├── .gitattributes # Git line endings normalization
├── .gitignore # Git ignored files configuration
├── build.gradle # Fabric Loom build configuration
├── gradle.properties # Dependencies & version properties
├── gradlew / gradlew.bat # Gradle Wrapper executable scripts
├── LICENSE # Apache-2.0 License
├── readme.md # Documentation (Russian)
├── readme.en.md # Documentation (English)
└── settings.gradle # Gradle settings
```

## Building from Source

1. Clone the repository:
 ```bash
 git clone https://github.com/byMr712/TotemCraft-MinecraftMod.git
 cd TotemCraft-MinecraftMod
 ```

2. Run Gradle build:
 - **Linux / macOS:** `./gradlew clean build`
 - **Windows:** `.\gradlew.bat clean build`

3. The compiled jar will be at:
 ```
 build/libs/TotemCraft-1.0.0.jar
 ```

## License

This project is licensed under the [Apache License 2.0](LICENSE).
