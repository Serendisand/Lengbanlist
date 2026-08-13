[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen)](https://www.minecraft.net)
[![License](https://img.shields.io/badge/License-MPL2.0-blue)](LICENSE)

<div align="center">
<p>
<img width="200" src="/Photos/Lengbanlist-icon.png">
</p>

*[简体中文](/README.md) | English*

**[Multi-platform Links](readme-website.md)** |
**[Developer Notes](PullRequest_en.md)** |
*[License Notice](Mustn't_en.md)* |
**[Discord](https://discord.gg/aeWjf7vD)**
</div>

![Lengbanlist](https://github.com/Serendisand/Lengbanlist/blob/main/Photos/Lengbanlist.png)
![Lengbanlist](https://bstats.org/signatures/bukkit/Lengbanlist.svg)

## What is this?

Lengbanlist started as a simple ban broadcast plugin, but it's grown into a full server management toolkit: banning, warning, muting, reporting, chat filtering, IP association detection, VPN detection, a web management panel ... pretty much everything you need for day-to-day server administration.

Every feature has its own toggle in the config file — turn off what you don't need, no bloat.

## Features

**Ban / IP Ban / Unban / Setban — `/ban` `/ban-ip` `/unban` `/setban`**
Ban a player or IP with durations in seconds, minutes, hours, days, weeks, months, or years. Use `forever` for permanent or `auto` to let the plugin calculate it from warning count. You can also modify an existing ban's duration and reason.

**Warning System — `/warn` `/unwarn`**
Warn players for rule violations. Has a built-in auto-ban system (LBAC): 3 warnings within 30 days triggers an automatic ban, and the ban duration scales up with each trigger. Remove warnings to drop below the threshold and the ban gets lifted automatically.

**Mute — `/mute` `/unmute` `/listmute`**
Mute a player for a set duration or permanently. Muted players can't chat. Unmute anytime, or list all currently muted players.

**Chat Filter**
Define your own list of bad words in the config. Triggered words get replaced with "mew". Hit the threshold and the player gets auto-muted. Suspicious messages show clickable buttons for admins to approve or penalize.

**IP Association & VPN Detection**
Tracks every IP a player has used. If two players share the same IP, staff get notified. On join, the plugin checks if the player is behind a VPN or proxy — you can set it to warn, kick, or auto-ban.

**Player Reports — `/report` `/admin`**
Players can report rule-breaking. Reports are stored in the database, and the reporter gets notified when an admin processes it.

**Ban Broadcast**
Periodically announces the current ban count in chat. Fully customizable message format. Toggle it on/off anytime, or broadcast manually.

**Character Models**
12 built-in character skins — Hutao, Furina, Zhongli, Keqing, Nahida, Klee, Yaemiko, and more. Switch between them and all plugin messages change tone and wording. Also supports custom models — drop a YAML file in plugins/Lengbanlist/models/ to define your own message style. An example file is auto-generated on first startup.

**GUI — `/lban open`**
A 54-slot chest interface. Ban, unban, mute, reload, switch models — all clickable. Banning and unbanning also have a chat wizard that walks you through it step by step.

**Web Management Panel**
Built-in HTTP management page — open it in your browser. JWT authentication and rate limiting included. Ban, unban, mute, warn, check history, reload config, all from the web UI.

**Lookup Tools — `/check` `/history` `/getip`**
Check a player's or IP's current punishment status, full history, associated players, and geographical location.

**Staff Chat — `/sc`**
A private chat channel for staff only.

**Other Bits**
`/info` shows plugin version, memory, CPU, online players. Supports SQLite and MySQL with automatic migration from legacy YAML storage. Has bStats analytics and auto-update support.

## Quick Start

1. Drop the jar into your server's `plugins` folder.
2. Restart the server — the plugin generates config files automatically.
3. Edit `config.yml` and other configs to your liking.
4. `/lban reload` to apply changes, or restart the server.

## Command Help

Full command reference: [Lengbanlist Command Help](LengbanlistCommandHelp.md)

## Screenshots

[See the plugin in action →](Lengbanlist_Images.md)

---

## Support the Project ❤️

If you find this plugin useful, feel free to show your support. It helps me keep developing and maintaining these projects, and motivates me to create more cool stuff.

## Donate

[![Afdian Sponsor](https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E6%94%AF%E6%8C%81%E6%88%91-orange)](https://afdian.com/a/lengmc)

Thanks for your support! ❤️
