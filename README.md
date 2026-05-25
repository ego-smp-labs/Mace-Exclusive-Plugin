# Mace-Exclusive

<div align="center">

![Mace-Exclusive](https://img.shields.io/badge/Mace--Exclusive-Plugin-E84C3D?style=for-the-badge&logo=minecraft&logoColor=white)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://jdk.java.net/21/)
[![Spigot](https://img.shields.io/badge/Spigot-1.21+-F7CF0C?style=for-the-badge&logo=spigotmc&logoColor=white)](https://www.spigotmc.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](./LICENSE)

**Standalone Powerful Exclusive Weapons Plugin** 🛠️

A unique singleton weapon mechanic with custom effects, strict inventory tracking, an immersive forging pipeline, and fully configurable settings. Refactored for Vanilla-like integration.

[Features](#features) • [Installation](#installation) • [Commands](#commands) • [Permissions](#permissions) • [Architecture](docs/ARCHITECTURE.md) • [Support](#support)

</div>

---
## Features

### 🔨 Singleton Limits & Strict Inventory
Legendary weapons with strict possession rules:
*   **Singleton Existence**: Only **ONE** instance of each exclusive weapon type can exist on the server at a time (configurable).
*   **Strict Container Guard**: 
    *   **Allowed**: Anvil, Enchanting Table, Player Inventory.
    *   **Blocked**: Storing in Chests, Shulkers, Barrels, Hoppers, Crafters, Dispensers, Droppers.

### 🛡️ The Awakening (Forge Pipeline)
*   **Unawakened Crafting**: Crafting a recipe yields an "Unawakened" weapon.
*   **The Ritual**: Players must drop the unawakened item onto a designated block to start an **Awakening Session**.
*   **Persistence**: A 5-minute process guarded by Holograms (TextDisplay). State is saved across server restarts.

### ✨ Weapon: Power Mace
The ultimate brute force:
*   **Passive (Stored Momentum)**: Increases knockback slightly when falling.
*   **Active (Ground Pulse)**: Smashing the ground releases an area-of-effect pulse that knocks enemies into the air.

### 🔮 Weapon: Chaos Mace
A corrupted variant with chaotic properties and void origins:
*   **Hold Curse**: Reduces max health when equipped.
*   **Environment Backfire**: Submerging in water causes rapid damage.
*   **Passive (Fractured Step)**: Dodges behind the attacker upon taking damage (internal cooldown).
*   **Active (Rift Reversal)**: Swaps positions with a target, inflicting damage. If the target dies within 3 seconds, a backfire applies to the user.

### ⚓ Weapon: Chronos Anchor Spear
A weapon that commands time:
*   **Hold Curse**: Reduces max health when equipped.
*   **Active (Time Pin)**: Throwing the spear and hitting an entity freezes them completely for 2.25s (cancels movement, jumping, interactions).
*   **Miss Backfire**: If the spear hits a block or misses, it returns to the user and freezes them for 1.25s instead.

---

## Installation

1.  **Download**: Get the latest JAR from [Modrinth](https://modrinth.com/) or build from source.
2.  **Install**: Drop the file into your server's `plugins/` folder.
3.  **Restart**: Start your server to generate configuration files.
4.  **Configure**: Edit files in `plugins/Mace-Exclusive/`:
    *   `config.yml`: Feature toggles, abilities, cooldowns.
    *   `lang_en.yml` / `lang_vi.yml`: Custom localization strings using MiniMessage.
5.  **Reload**: Use `/macee reload` to apply configuration changes live.

---

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/macee help` | Show the plugin help menu | `mace.use` |
| `/macee info <weapon_id>` | View current holder of the specified exclusive weapon | `mace.use` |
| `/macee give <weapon_id>` | **Admin**: Gives the weapon to the executing player | `mace.admin` |
| `/macee reset <weapon_id>` | **Admin**: Resets ownership, allowing the weapon to be forged again | `mace.admin` |
| `/macee reload` | **Admin**: Reloads plugin configuration and localization files | `mace.admin` |

*Weapon IDs: `power_mace`, `chaos_mace`, `chronos_anchor_spear`*

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `mace.use` | true | Allows usage of `/macee help` and `/macee info` |
| `mace.admin` | op | Allows access to `/macee give`, `/macee reset`, `/macee reload` |

---

## Developer Guide

For details on the project's internal architecture, code flow, and instructions to build the plugin from source, please check the [Architecture & Development Guide](docs/ARCHITECTURE.md) and the new [Implementation Report](docs/IMPLEMENTATION_REPORT.md).

---

## License

Distributed under the MIT License. See `LICENSE` for more information.

Copyright © 2026 **NirussVn0** and **Ego SMP Labs**.

---

## Support

If you find any issues or have suggestions, please open an issue on [GitHub](https://github.com/ego-smp-labs/Mace-Exclusive-Plugin/issues).

donate: [paypal](https://www.paypal.com/paypalme/nirussvn0)
