[![Minecraft](https://img.shields.io/badge/Minecraft-1.17.1%20~%2026.3+-brightgreen)](https://www.minecraft.net)
[![License](https://img.shields.io/badge/License-MPL%202.0-blue)](../LICENSE)
![Java](https://img.shields.io/badge/Java-17+-orange)

<div align="center">
<p>
    <img width="200" src="/Photos/Lengbanlist-icon.png" alt="Lengbanlist">
</p>

**Lengbanlist · A lightweight, modular punishment and management plugin**

*[简体中文](/README.md) | English*
</div>

![Lengbanlist](https://github.com/Serendisand/Lengbanlist/blob/main/Photos/Lengbanlist.png)
![Lengbanlist](https://bstats.org/signatures/bukkit/Lengbanlist.svg)

## Overview

Lengbanlist started out as a ban broadcast machine: someone gets banned, the whole server hears about it.

Today it is a full management crew for your server — bans, warnings, mutes, reports and appeals, chat filtering, IP association, VPN detection, audit rollback, plus a web panel you open in a browser.

Feature toggles all live under `features` in `config.yml`.

## Features

| Module | Commands | Description |
| --- | --- | --- |
| Bans / IP bans | `/ban` `/ban-ip` `/unban` `/setban` | Ban a player or an IP with durations in seconds, minutes, hours, days, weeks, months or years, plus `forever` for permanent and `auto` to derive the duration from warning count. Existing bans can be re-timed and re-reasoned at any time. IP range bans (`172.198.2.x`, `172.198.2.0/24`) block an entire network segment in one go |
| Warning system | `/warn` `/unwarn` | Record and revoke warnings. Built-in LBAC auto-ban: 3 warnings within 30 days triggers a ban whose duration escalates with each trigger. Revoking warnings below the threshold lifts the ban automatically |
| Mutes | `/mute` `/unmute` `/listmute` | Timed or permanent mutes; muted players can't chat. Unmute at any time and list all active mutes |
| Chat filtering | — | Define blocked words in `chatconfig.yml`; hits get replaced automatically. Repeated hits trigger an auto-mute, and suspicious messages notify staff with one-click approve / warn buttons |
| IP association / VPN detection | `/lban alts` | Records every IP a player has used and notifies staff when multiple accounts share one. On join, VPN and proxy connections are detected and can be warned, kicked or banned |
| Reports and appeals | `/report` `/admin` | Players submit reports and are notified once staff handles them; ban appeals are submitted from the web page and handled in the panel |
| Ban broadcast | — | Periodically announces current ban statistics in chat, with fully customizable wording and format. Can be triggered manually or toggled at any time |
| Character models | `/lban model` `/lban models` | Every message comes from a character model downloaded on demand from the [Lengbanlist-Models](https://github.com/Serendisand/Lengbanlist-Models) cloud repo. Browse with `models list`, install with `models install <ID>`, switch with `model <name>` — and the tone of every plugin message changes with it. Models support **inheritance**: write only the lines you want to change, and text added by plugin updates follows along automatically. A featured model is picked each month from install stats |
| GUI | `/lban open` | A 54-slot chest menu whose buttons follow your permissions: ban, unban, re-time, mute, warn, freeze, vanish, switch models, reload. Ban, mute, warn and freeze include a chat wizard that walks you through the steps — send `cancel` to abort |
| Web panel | — | A built-in HTTP management page you open in a browser, protected by JWT authentication and rate limiting. Ban, unban, mute, warn, look up records and reload config from the page |
| Lookup tools | `/check` `/history` `/getip` | Check a player's or IP's current punishment status, full history, associated accounts and geographical location |
| Vanish / Freeze | `/lban vanish` `/lban freeze` | Vanish makes you fully invisible to other players — held items and armour included, and hidden from the player list (staff with `lengbanlist.vanish.see` still see you). Freeze blocks movement, block breaking, interaction, item dropping, commands and teleports while showing a red title with the reason; unfreezing is manual via `/lban unfreeze`. Freeze state is stored in the database, so it survives restarts and applies across sub-servers sharing one database |
| Shared database across sub-servers | — | Database settings live in `storage.yml` (leaving only feature toggles in `config.yml`). Give each sub-server a `server-name`, and when they share one MySQL / MariaDB / PostgreSQL instance every audit entry records which server and which operator performed the action — visible in `/lban audit` and the web panel |
| Staff chat | `/sc` | A private chat channel for staff (Lua extension) |
| Teleport | `/lban tp` | Teleport to a player (Lua extension) |
| Operation rollback | `/lban rollback` | Roll back by operator and time range using the audit log: bans are lifted, unbans re-applied, warnings revoked. Can be restricted to a single operation type |
| Other | `/info` | Plugin version, memory, CPU and online count. Supports SQLite / MySQL / MariaDB / PostgreSQL with automatic migration from legacy YAML storage, plus bStats analytics and auto-update |

## Extensions

The core keeps only the punishment loop; everything else can be taken out. Two ways to write one:

- **Lua extensions**: drop a `.lua` file into `plugins/Lengbanlist/extensions/` and run `/lban ext reload` — no compilation, no restart. The bundled `/lban tp` and `/sc` are written this way.
- **Java extensions**: a standalone Bukkit plugin depending on `lengbanlist-api` at compile time, able to register commands, hook into core decision points, and reuse the core's connection pool.

Commands from either kind walk the same registration path as built-in ones, so permissions, tab completion, `/lban help`, and feature toggles stay in sync; `/lban ext list` shows what's installed.

## Requirements

| Item | Requirement |
| --- | --- |
| Server | Spigot / Paper / Folia |
| Minecraft | 1.17.1 ~ 26.3+, one jar for all |
| Java | 17 or newer |
| Database | SQLite (bundled, works out of the box) / MySQL / MariaDB / PostgreSQL |

## Quick Start

1. Drop the jar into your server's `plugins` folder and restart the server.
2. The first start generates `plugins/Lengbanlist/eula.yml`: change `I have read and agree to the above terms` to `yes` and restart once more — the plugin stays disabled until you do.
3. Edit `config.yml` (feature toggles and behaviour) and `storage.yml` (database, retention, server tag). Legacy `database` sections in `config.yml` are migrated automatically.
4. Run `/lban reload` to apply the changes, or restart the server.

## Documentation

| Document | Contents |
| --- | --- |
| [Command Help](LengbanlistCommandHelp.md) | Full command list and usage |
| [Config Guide](LengbanlistConfig.md) | `config.yml` / `storage.yml` field reference |
| [Screenshots](Lengbanlist_Images.md) | Interface screenshots and results in game |
| [Contributor Guide](PullRequest_en.md) | Project layout, package responsibilities, extension system, build and tests |
| [Writing an Extension](../lengbanlist-api/README.md) | The Java extension contract, a full example, method reference |
| [Multi-platform](readme-website.md) | GitHub / SpigotMC / Modrinth |
| [Usage Restrictions](Mustn't_en.md) | MPL-2.0 and EULA essentials |

---

## Support the Project ❤️

Questions, ideas, or new feature chatter — come find us on [Discord](https://discord.gg/aeWjf7vD).

If this plugin helps you, consider supporting its development. It's what keeps me maintaining and improving it.

[![Afdian](https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E6%94%AF%E6%8C%81%E6%88%91-orange)](https://afdian.com/a/lengmc)

Thanks for your support ❤️
