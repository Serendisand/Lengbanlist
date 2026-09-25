[![Minecraft](https://img.shields.io/badge/Minecraft-1.17.1%20~%2026.3+-brightgreen)](https://www.minecraft.net)
[![License](https://img.shields.io/badge/License-MPL%202.0-blue)](LICENSE)
![Java](https://img.shields.io/badge/Java-17+-orange)

<div align="center">
<p>
    <img width="200" src="/Photos/Lengbanlist-icon.png" alt="Lengbanlist">
</p>

**冷禁列表 · 轻量、模块化的服务端处罚与管理插件**

*简体中文 | [English](docs/README_en.md)*

**[多平台](docs/readme-website.md)** ·
**[命令帮助](docs/LengbanlistCommandHelp.md)** ·
**[配置速览](docs/LengbanlistConfig.md)** ·
**[开发须知](docs/PullRequest_zh.md)** ·
*[许可证提示](docs/Mustn't_zh.md)* ·
**[Discord](https://discord.gg/aeWjf7vD)**
</div>

![Lengbanlist](https://github.com/Serendisand/Lengbanlist/blob/main/Photos/Lengbanlist.png)
![Lengbanlist](https://bstats.org/signatures/bukkit/Lengbanlist.svg)

## 简介

Lengbanlist 从一个封禁广播插件发展为一套完整的服务器管理工具：封禁、警告、禁言、举报、聊天过滤、IP 关联、VPN 检测、Web 管理面板一应俱全。

所有功能在 `config.yml` 的 `features` 段中独立开关，用不到的模块关掉即可，被关闭的功能连命令都不会注册。

## 功能

| 模块 | 命令 | 说明 |
| --- | --- | --- |
| 封禁 / IP 封禁 | `/ban` `/ban-ip` `/unban` `/setban` | 封禁玩家或 IP，时长支持 秒 / 分 / 时 / 天 / 周 / 月 / 年，也支持 `forever` 永久与 `auto`（按警告次数自动计算）；可随时修改已有封禁的时长与理由；支持 IP 段封禁（`172.198.2.x`、`172.198.2.0/24`），一次拦截整个网段 |
| 警告系统 | `/warn` `/unwarn` | 记录与撤销警告。内置 LBAC 自动封禁：30 天内累计 3 次警告自动封禁，时长随触发次数递增；撤销警告至阈值以下时自动解封，无需手动处理 |
| 禁言 | `/mute` `/unmute` `/listmute` | 定时或永久禁言，禁言期间无法发言；可随时解禁并查看当前禁言列表 |
| 聊天过滤 | — | 违禁词列表在 `chatconfig.yml` 自定义，命中后自动替换；累计触发自动禁言；可疑消息带按钮通知管理员，一键放行或警告 |
| IP 关联 / VPN 检测 | `/lban alts` | 记录每位玩家的历史登录 IP，发现多账号共用同一 IP 时提醒管理员；入服时检测 VPN / 代理，可配置为警告、踢出或封禁 |
| 举报系统 | `/report` `/admin` | 玩家提交举报，管理员处理完毕后通知举报人 |
| 封禁广播 | — | 定时在聊天栏广播当前封禁统计，文案与格式完全自定义；支持手动触发与随时开关 |
| 角色模型 | `/lban model` `/lban models` | 文案不写死在插件内，从云端仓库 [Lengbanlist-Models](https://github.com/Serendisand/Lengbanlist-Models) 按需下载：`models list` 浏览、`models install <ID>` 安装、`model <名称>` 切换，所有提示消息的措辞与语气随之改变。现有 25 种角色风格，每月更新并投票选出月度精选。模型文本支持**继承**：全局默认位于 `models/_base.yml`，模型文件只写需要覆写的字段，插件升级新增文案不会让老模型掉队 |
| 图形界面 | `/lban open` | 54 格箱子菜单，按钮按权限显示；封禁、解禁、改期、禁言、警告、冻结、隐身、切模型、重载均可直接操作。封禁、禁言、警告、冻结附带聊天向导，逐步引导填写，发送 `cancel` 随时退出 |
| Web 管理面板 | — | 内置 HTTP 管理页面，浏览器直接打开；JWT 鉴权与限流保护。封禁、解禁、禁言、警告、记录查询、重载配置全部可在页面上完成 |
| 查询工具 | `/check` `/history` `/getip` | 查询玩家或 IP 的当前处罚状态、历史记录、关联账号与 IP 归属地 |
| 隐身 / 冻结 | `/lban vanish` `/lban freeze` | 隐身：对其它玩家完全不可见，手持物品与盔甲一并隐藏，玩家列表也不显示（`lengbanlist.vanish.see` 权限者仍可见）。冻结：禁止移动、破坏、交互、丢弃、使用命令与传送，屏幕弹出红色标题与理由，解冻需手动执行 `/lban unfreeze`；冻结记录存于数据库，重启不丢，多子服共用数据库时全服生效 |
| 多子服共用数据 | — | 数据库配置独立至 `storage.yml`（`config.yml` 只保留功能开关）。为每台子服设置 `server-name`，共用同一份 MySQL / MariaDB / PostgreSQL 时，审计日志会记录每条操作来自哪台子服、哪位操作员，`/lban audit` 与 Web 面板均可查看 |
| 管理员频道 | `/sc` | 仅管理员可见的独立聊天频道 |
| 操作回滚 | `/lban rollback` | 基于审计日志，按操作人与时间范围一键回滚：封禁的解封、解封的重新封禁、警告的撤销。可限定只回滚某一类操作 |
| 其他 | `/info` | 查看插件版本、内存、CPU 与在线人数；支持 SQLite / MySQL / MariaDB / PostgreSQL，旧版 YAML 存储自动迁移；内置 bStats 统计与自动更新 |

## 环境要求

| 项目 | 要求 |
| --- | --- |
| 服务端 | Spigot / Paper / Folia |
| 游戏版本 | 1.17.1 ~ 26.3+，单个 jar 通用 |
| Java | 17 及以上 |
| 数据库 | SQLite（内置，开箱即用）/ MySQL / MariaDB / PostgreSQL |

## 快速开始

1. 将插件 jar 放入服务端 `plugins` 目录。
2. 重启服务端，插件自动生成配置文件。
3. 按需修改 `config.yml`（功能开关与玩法）与 `storage.yml`（数据库、保留策略、服务器标识）。旧版本写在 `config.yml` 里的 `database` 段会自动迁移。
4. 执行 `/lban reload` 应用改动，或重启服务端。

## 文档

| 文档 | 内容 |
| --- | --- |
| [命令帮助](docs/LengbanlistCommandHelp.md) | 完整命令列表与用法 |
| [配置速览](docs/LengbanlistConfig.md) | `config.yml` / `storage.yml` 字段说明 |
| [插件展示](docs/Lengbanlist_Images.md) | 界面截图与实际效果 |
| [多平台链接](docs/readme-website.md) | GitHub / SpigotMC / Modrinth |

---

## 支持这个项目 ❤️

如果这个插件对你有帮助，欢迎赞助支持。你的支持是我持续开发和维护的动力。

[![爱发电](https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E6%94%AF%E6%8C%81%E6%88%91-orange)](https://afdian.com/a/lengmc)

感谢支持 ❤️
