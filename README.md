# Mace-Exclusive

<div align="center">

![Mace-Exclusive](https://img.shields.io/badge/Mace--Exclusive-Plugin-E84C3D?style=for-the-badge&logo=minecraft&logoColor=white)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://jdk.java.net/21/)
[![Spigot](https://img.shields.io/badge/Spigot-1.21+-F7CF0C?style=for-the-badge&logo=spigotmc&logoColor=white)](https://www.spigotmc.org/)
[![Gradle](https://img.shields.io/badge/Gradle-8.1-02303A?style=for-the-badge&logo=gradle&logoColor=white)](https://gradle.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](./LICENSE)

**Standalone Powerful Exclusive Weapons Plugin** 🛠️

A unique singleton weapon mechanic with custom effects, strict inventory tracking, an immersive forging pipeline, and fully configurable settings. Refactored for Vanilla-like integration.

[Features](#features) • [Cores](#artifact-cores) • [Maces](#exclusive-maces) • [Wiki](#official-player-wiki) • [Installation](#installation) • [Commands](#commands) • [Permissions](#permissions) • [Architecture](docs/ARCHITECTURE.md)

</div>

---

## Features

### 🔨 Singleton Limits & Strict Inventory
Legendary weapons with strict possession rules:
*   **Singleton Existence**: Only **ONE** instance of each exclusive weapon type can exist on the server at a time.
*   **Strict Container Guard**: Blocks storing weapons in Chests, Shulkers, Hoppers, Crafters, Dispensers, Droppers. Weapons are kept in the player's active inventory (or anvil/enchant table).

### 🛡️ The Awakening (Forge Pipeline)
*   **Lodestone Forge**: Crafting a recipe transforms the workbench into a **Lodestone** block.
*   **The Ritual**: The block charges for 3 seconds, explodes, then counts down for 5 minutes (saved across restarts). A second explosion completes the process and drops the final weapon bound to its wielder.

---

## Artifact Cores
Magical cores required to forge the legendary maces. When carried in inventory, they trigger negative **Instability** effects:
*   **Ego Core**: Prideful core that blocks XP absorption.
*   **Soulfire Core**: Contains intense blue heat.
*   **Blood Core**: Periodically drains wielder health. Crafted via Nether blood sacrifice.
*   **Sculk Core**: Spreads darkness. Awakened via Warden death near Sculk Catalyst.
*   **End Core**: Warps space to teleport wielder nearby. Acquired via End Portal distortion.
*   **Ruined Core**: Defective core resulting from failed rituals.

---

## Exclusive Maces
Phase 3 Mace-first roster with unique active abilities (triggered via **Sneak + Left Click**), passives, and curses:
*   **Power Mace**: Storm-forged strike that sends targets into the air.
*   **Abyssal Void Mace**: bargains with fatal damage for Abyss resurrection.
*   **Chaos Mace**: Madness rage state, space-warping attacks, and hotbar item shuffling.
*   **Mace of Vampirism**: Melee final damage lifesteal and temporary max-health siphon.
*   **Singularity Gravity Mace**: Generates a gravity well pulling entities before collapsing.
*   **Echoing Warden Mace**: Fires a long-range true damage sonic boom wave.
*   **Soulfire Pyre Mace**: Creates an expanding storm of blue flame and Wither ticks.

---

## Official Player Wiki
📚 **Xem thông tin đầy đủ, công thức chế tạo trực quan và cách kích hoạt kỹ năng tại / View the full guide, visual crafting grids, and gameplay guidelines at:**
👉 **[https://niruss.staticdomains.app/wiki](https://niruss.staticdomains.app/wiki)**

---

## Installation

1.  **Download**: Get the latest JAR from source or release.
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
| `/macee info <weapon_id>` | View current wielder of the specified weapon | `mace.use` |
| `/macee give <weapon_id>` | **Admin**: Gives the weapon to the executing player | `mace.admin` |
| `/macee reset <weapon_id>` | **Admin**: Resets ownership, allowing the weapon to be forged again | `mace.admin` |
| `/macee reload` | **Admin**: Reloads plugin configuration and localization files | `mace.admin` |

*Weapon IDs: `power_mace`, `void_mace`, `chaos_mace`, `vampiric_mace`, `gravity_mace`, `sonic_mace`, `soulfire_mace`*

---

## Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `mace.use` | true | Allows usage of `/macee help` and `/macee info` |
| `mace.admin` | op | Allows access to `/macee give`, `/macee reset`, `/macee reload` |

---

## Developer Guide

For details on the project's internal architecture, code flow, and instructions to build the plugin from source, please check the [Architecture & Development Guide](docs/ARCHITECTURE.md).

---

## License

Distributed under the MIT License. See `LICENSE` for more information.

Copyright © 2026 **NirussVn0** and **Ego SMP Labs**.
