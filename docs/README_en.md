[![Minecraft](https://img.shields.io/badge/Minecraft-1.17.1%20~%2026.3+-brightgreen)](https://www.minecraft.net)
[![License](https://img.shields.io/badge/License-MPL%202.0-blue)](../LICENSE)
![Java](https://img.shields.io/badge/Java-17+-orange)

<div align="center">
<p>
    <img width="200" src="/Photos/Lengbanlist-icon.png" alt="Lengbanlist">
</p>

**Lengbanlist · A lightweight, modular punishment and management plugin**

*[简体中文](/README.md) | English*

**[Multi-platform](readme-website.md)** ·
**[Command Help](LengbanlistCommandHelp.md)** ·
**[Config Guide](LengbanlistConfig.md)** ·
**[Developer Notes](PullRequest_en.md)** ·
*[License Notice](Mustn't_en.md)* ·
**[Discord](https://discord.gg/aeWjf7vD)**
</div>

![Lengbanlist](https://github.com/Serendisand/Lengbanlist/blob/main/Photos/Lengbanlist.png)
![Lengbanlist](https://bstats.org/signatures/bukkit/Lengbanlist.svg)

## Overview

Lengbanlist began as a ban broadcast plugin and has grown into a complete server management toolkit: bans, warnings, mutes, reports, chat filtering, IP association, VPN detection and a web panel.

Every module has its own toggle under the `features` section of `config.yml`. Disable what you don't need — a disabled feature won't even register its commands.

## Features

| Module | Commands | Description |
| --- | --- | --- |
| Bans / IP bans | `/ban` `/ban-ip` `/unban` `/setban` | Ban a player or an IP with durations in seconds, minutes, hours, days, weeks, months or years, plus `forever` for permanent and `auto` to derive the duration from warning count. Existing bans can be re-timed and re-reasoned at any time. IP range bans (`172.198.2.x`, `172.198.2.0/24`) block an entire network segment in one go |
| Warning system | `/warn` `/unwarn` | Record and revoke warnings. Built-in LBAC auto-ban: 3 warnings within 30 days triggers a ban whose duration escalates with each trigger. Revoking warnings below the threshold lifts the ban automatically |
| Mutes | `/mute` `/unmute` `/listmute` | Timed or permanent mutes; muted players can't chat. Unmute at any time and list all active mutes |
| Chat filtering | — | Define blocked words in `chatconfig.yml`; hits get replaced automatically. Repeated hits trigger an auto-mute, and suspicious messages notify staff with one-click approve / warn buttons |
| IP association / VPN detection | `/lban alts` | Records every IP a player has used and notifies staff when multiple accounts share one. On join, VPN and proxy connections are detected and can be warned, kicked or banned |
| Reports | `/report` `/admin` | Players submit reports; the reporter is notified once staff handles it |
| Ban broadcast | — | Periodically announces current ban statistics in chat, with fully customizable wording and format. Can be triggered manually or toggled at any time |
| Character models | `/lban model` `/lban models` | Messages aren't hardcoded — they're downloaded on demand from the [Lengbanlist-Models](https://github.com/Serendisand/Lengbanlist-Models) cloud repo. Browse with `models list`, install with `models install <ID>`, switch with `model <name>` and every plugin message changes tone and wording. 25 character styles available, updated monthly with a featured pick. Text supports **inheritance**: the global baseline lives in `models/_base.yml` and a model file only declares what it overrides, so new keys added by plugin updates never leave old models behind |
| GUI | `/lban open` | A 54-slot chest menu whose buttons follow your permissions: ban, unban, re-time, mute, warn, freeze, vanish, switch models, reload. Ban, mute, warn and freeze include a chat wizard that walks you through the steps — send `cancel` to abort |
| Web panel | — | A built-in HTTP management page you open in a browser, protected by JWT authentication and rate limiting. Ban, unban, mute, warn, look up records and reload config from the page |
| Lookup tools | `/check` `/history` `/getip` | Check a player's or IP's current punishment status, full history, associated accounts and geographical location |
| Vanish / Freeze | `/lban vanish` `/lban freeze` | Vanish makes you fully invisible to other players — held items and armour included, and hidden from the player list (staff with `lengbanlist.vanish.see` still see you). Freeze blocks movement, block breaking, interaction, item dropping, commands and teleports while showing a red title with the reason; unfreezing is manual via `/lban unfreeze`. Freeze state is stored in the database, so it survives restarts and applies across sub-servers sharing one database |
| Shared database across sub-servers | — | Database settings live in `storage.yml` (leaving only feature toggles in `config.yml`). Give each sub-server a `server-name`, and when they share one MySQL / MariaDB / PostgreSQL instance every audit entry records which server and which operator performed the action — visible in `/lban audit` and the web panel |
| Staff chat | `/sc` | A private chat channel for staff |
| Operation rollback | `/lban rollback` | Roll back by operator and time range using the audit log: bans are lifted, unbans re-applied, warnings revoked. Can be restricted to a single operation type |
| Other | `/info` | Plugin version, memory, CPU and online count. Supports SQLite / MySQL / MariaDB / PostgreSQL with automatic migration from legacy YAML storage, plus bStats analytics and auto-update |

## Requirements

| Item | Requirement |
| --- | --- |
| Server | Spigot / Paper / Folia |
| Minecraft | 1.17.1 ~ 26.3+, one jar for all |
| Java | 17 or newer |
| Database | SQLite (bundled, works out of the box) / MySQL / MariaDB / PostgreSQL |

## Quick Start

1. Drop the jar into your server's `plugins` folder.
2. Restart the server — config files are generated automatically.
3. Edit `config.yml` (feature toggles and behaviour) and `storage.yml` (database, retention, server tag). Legacy `database` sections in `config.yml` are migrated automatically.
4. Run `/lban reload` to apply the changes, or restart the server.

## Documentation

| Document | Contents |
| --- | --- |
| [Command Help](LengbanlistCommandHelp.md) | Full command list and usage |
| [Config Guide](LengbanlistConfig.md) | `config.yml` / `storage.yml` field reference |
| [Screenshots](Lengbanlist_Images.md) | Interface screenshots and results in game |
| [Multi-platform](readme-website.md) | GitHub / SpigotMC / Modrinth |

---

## Support the Project ❤️

If this plugin helps you, consider supporting its development. It's what keeps me maintaining and improving it.

[![Afdian](https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E6%94%AF%E6%8C%81%E6%88%91-orange)](https://afdian.com/a/lengmc)

Thanks for your support ❤️
