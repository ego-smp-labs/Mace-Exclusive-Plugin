# Mace-Exclusive.  

<div align="center">

![Mace-Exclusive](https://img.shields.io/badge/Mace--Exclusive-Plugin-E84C3D?style=for-the-badge&logo=minecraft&logoColor=white)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://jdk.java.net/21/)
[![Spigot](https://img.shields.io/badge/Spigot-1.21+-F7CF0C?style=for-the-badge&logo=spigotmc&logoColor=white)](https://www.spigotmc.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](./LICENSE)

**Standalone Powerful Mace Plugin** 🛠️

A unique singleton weapon mechanic with custom effects, strict inventory tracking, and fully configurable settings.
Originally part of **SabíSMP**, now a dedicated plugin.

[Features](#features) • [Installation](#installation) • [Commands](#commands) • [Permissions](#permissions) • [Architecture](docs/ARCHITECTURE.md) • [Support](#support)

</div>

---
## Features

### 🔨 Limits the Power of the MACE
A legendary weapon with unique mechanics:
*   **Singleton Existence**: Only **ONE** Mace can exist on the server at a time (configurable).
*   **Custom Recipe**
* ![img](https://i.imgur.com/pLh7sXs.png)
*   **Strict Mode** (Refined):
    *   **Allowed**: Anvil, Enchanting Table, Player Inventory.
    *   **Blocked**: Storing in Chests, Shulkers, Barrels, etc.
    *   **Blocked**: Dropping the item (if strict mode is enabled).

### ✨ Effect Mace (Visuals & Combat)
*   **First Craft**: Player glows for 5 minutes (configurable) upon crafting.
*   **Passive**:
    *   *Holding*: Optional Glowing effect and Soul Particles.
*   **Combat**:
    *   *Ground Slam*: Hitting an entity causes blocks around to "jump" (visual effect).
    *   *Kill Message*: Custom chat message when killing a player.

### 🔮 Custom Mace: Mace Chaos (The Glitch)
A corrupted variant with chaotic properties and void origins:
*   **Hard Recipe**:
    *   Requires **3x Dark Ego** (custom `NETHER_STAR` items with the `egosmp:dark_ego` PDC tag), **2x Heavy Core**, **1x Mace**, and **3x Wither Rose**.
    *   ![Hard Recipe](https://i.imgur.com/XClFjxZ.png)
*   **Self-Curse**: Wither II and Inventory Shuffling (periodically shuffles hotbar and main inventory slots 0-35) for 10 seconds upon crafting or picking up.
*   **Combat Effects**:
    *   **Glitch Strike**: 10-20% chance (configurable) to corrupt the victim's inventory, forcing periodic slot shuffling for 5-10 seconds.
    *   **Glitch Kill**: Overrides standard death messages to obfuscate the killer's identity (e.g. `Victim was OBLITERATED by §kERROR_404`).



## Installation

1.  **Download**: Get the latest JAR from [Modrinth](https://modrinth.com/).
2.  **Install**: Drop the file into your server's `plugins/` folder.
3.  **Restart**: Start your server to generate configuration files.
4.  **Configure**: Edit files in `plugins/Mace-Exclusive/`:
    *   `config.yml`: Feature toggles (Strict mode, recipes, combat stats)
    *   `lang_en.yml` / `lang_vi.yml`: Custom localization strings
5.  **Reload**: Use `/macee reload` to apply configuration changes live.

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/macee help` | Show the plugin help menu | `mace.use` |
| `/macee info [power\|chaos]` | View current holder and coordinate location of the selected Mace type | `mace.use` |
| `/macee give [power\|chaos]` | **Admin**: Gives the selected Mace type to the player executing the command | `mace.admin` |
| `/macee reset [power\|chaos]` | **Admin**: Resets registration status, allowing the selected Mace type to be crafted again | `mace.admin` |
| `/macee reload` | **Admin**: Reloads plugin configuration and localization files | `mace.admin` |

*Note: If no mace type is specified, commands default to the `power` mace.*

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `mace.use` | true | Allows usage of `/macee help` and `/macee info` |
| `mace.admin` | op | Allows access to `/macee give`, `/macee reset`, `/macee reload`, and bypasses strict mode container block restrictions |

---

## Developer Guide

For details on the project's internal architecture, code flow, and instructions to build the plugin from source, please check the [Architecture & Development Guide](docs/ARCHITECTURE.md).

---

## License

Distributed under the MIT License. See `LICENSE` for more information.

Copyright © 2026 **NirussVn0** and **Ego SMP Labs**.

---

## Support

If you find any issues or have suggestions, please open an issue on [GitHub](https://github.com/ego-smp-labs/Mace-Exclusive-Plugin/issues).

donate: [paypal](https://www.paypal.com/paypalme/nirussvn0)