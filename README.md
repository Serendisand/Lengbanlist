[![Minecraft](https://img.shields.io/badge/Minecraft-1.17.1%20~%2026.3+-brightgreen)](https://www.minecraft.net)
[![License](https://img.shields.io/badge/License-MPL%202.0-blue)](LICENSE)
![Java](https://img.shields.io/badge/Java-17+-orange)

<div align="center">
<p>
    <img width="200" src="/Photos/Lengbanlist-icon.png" alt="Lengbanlist">
</p>

**冷禁列表 · 轻量、模块化的服务端处罚与管理插件**

*简体中文 | [English](docs/README_en.md)*
</div>

![Lengbanlist](https://github.com/Serendisand/Lengbanlist/blob/main/Photos/Lengbanlist.png)
![Lengbanlist](https://bstats.org/signatures/bukkit/Lengbanlist.svg)

## 简介

Lengbanlist 起初只是台封禁广播机：谁被封了，全服喊一声。

现在它是服务器的一整套管理班子——封禁、警告、禁言、举报申诉、聊天过滤、IP 关联、VPN 检测、审计回滚，外加一个浏览器就能打开的 Web 面板。

功能开关都在 `config.yml` 的 `features` 段。

## 功能

| 模块 | 命令 | 说明 |
| --- | --- | --- |
| 封禁 / IP 封禁 | `/ban` `/ban-ip` `/unban` `/setban` | 时长支持 秒 / 分 / 时 / 天 / 周 / 月 / 年，也认 `forever` 与 `auto`（按警告次数自动算）；已有封禁随时改期改理由；IP 段封禁支持 `172.198.2.x`、`172.198.2.0/24`，整个网段一起拦 |
| 警告系统 | `/warn` `/unwarn` | 记录与撤销警告。LBAC 自动封禁：30 天内攒够 3 次就封，越攒越久；撤销到阈值以下自动解封 |
| 禁言 | `/mute` `/unmute` `/listmute` | 定时或永久禁言，禁言期间开不了口；随时解禁，`/listmute` 看当前名单 |
| 聊天过滤 | — | 违禁词在 `chatconfig.yml` 里自己定，命中自动替换；攒够次数自动禁言；可疑消息带按钮推给管理员，一键放行或警告 |
| IP 关联 / VPN 检测 | `/lban alts` | 记下每位玩家的历史登录 IP，多账号共用同一 IP 时提醒管理员；入服检测 VPN / 代理，可配置成警告、踢出或封禁 |
| 举报与申诉 | `/report` `/admin` | 玩家提交举报，管理员处理完通知本人；封禁申诉从网页提交，在面板里处理 |
| 封禁广播 | — | 定时在聊天栏播报封禁统计，文案格式随意改；也能手动触发、随时开关 |
| 角色模型 | `/lban model` `/lban models` | 提示语来自云端仓库 [Lengbanlist-Models](https://github.com/Serendisand/Lengbanlist-Models)：`models list` 浏览、`models install <ID>` 安装、`model <名称>` 切换，插件里每句话的语气跟着变。模型支持继承——只写要改的句子，插件升级新增的文案自动跟上。每月还会按安装统计选出月度精选 |
| 图形界面 | `/lban open` | 54 格箱子菜单，按钮按权限显示：封禁、解禁、改期、禁言、警告、冻结、隐身、切模型、重载都能点。封禁、禁言、警告、冻结还带聊天向导，一路问下来，中途 `cancel` 退出 |
| Web 管理面板 | — | 内置 HTTP 管理页，浏览器直接打开，JWT 鉴权加限流。封禁、解禁、禁言、警告、记录查询、重载在页面上都能办 |
| 查询工具 | `/check` `/history` `/getip` | 查玩家或 IP 的当前处罚、历史记录、关联账号与 IP 归属地 |
| 隐身 / 冻结 | `/lban vanish` `/lban freeze` | 隐身：对其他人完全不可见，手持物与盔甲一并隐藏，玩家列表里也不显示（有 `lengbanlist.vanish.see` 权限的照旧看得见）。冻结：禁移动、禁破坏、禁交互、禁丢弃、禁命令、禁传送，屏幕上弹出红色标题与理由，解冻得手动 `/lban unfreeze`；冻结记录存数据库，重启不丢，多子服共库时全服生效 |
| 多子服共用数据 | — | 数据库配置独立到 `storage.yml`。给每台子服设一个 `server-name`，共用同一份 MySQL / MariaDB / PostgreSQL 时，审计日志会记下每条操作出自哪台子服、哪位操作员，`/lban audit` 与 Web 面板都能翻 |
| 管理员频道 | `/sc` | 只有管理员看得见的独立聊天频道（Lua 扩展） |
| 传送 | `/lban tp` | 传送到玩家身边（Lua 扩展） |
| 操作回滚 | `/lban rollback` | 顺着审计日志按操作人与时间范围一键回滚：封禁的解封、解封的重新封禁、警告的撤销，也能只回滚某一类 |
| 其他 | `/info` | 插件版本、内存、CPU、在线人数；SQLite / MySQL / MariaDB / PostgreSQL 四选一，旧版 YAML 存储自动迁移；内置 bStats 统计与自动更新 |

## 扩展

核心只留处罚闭环，别的都能拆出去。目前两种写法：

- **Lua 扩展**：把 `.lua` 丢进 `plugins/Lengbanlist/extensions/`，`/lban ext reload` 就生效——不用编译，不用重启。核心自带的 `/lban tp`、`/sc` 就是这么写的。
- **Java 扩展**：独立 Bukkit 插件，编译期依赖 `lengbanlist-api`，能注册命令、挂核心决策钩子、复用核心的连接池。

两种扩展注册的命令和内置命令走同一条路径，权限、补全、`/lban help`、功能开关自动对齐；装了哪些 Lua 扩展，`/lban ext list` 一眼看完。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| 服务端 | Spigot / Paper / Folia |
| 游戏版本 | 1.17.1 ~ 26.3+，单个 jar 通用 |
| Java | 17 及以上 |
| 数据库 | SQLite（内置，开箱即用）/ MySQL / MariaDB / PostgreSQL |

## 快速开始

1. 把插件 jar 放进服务端 `plugins` 目录，重启服务端。
2. 首次启动会生成 `plugins/Lengbanlist/eula.yml`：把 `I have read and agree to the above terms` 改成 `yes`，再重启一次——没同意之前插件不会启用。
3. 按需修改 `config.yml`（功能开关与玩法）与 `storage.yml`（数据库、保留策略、服务器标识）。旧版本写在 `config.yml` 里的 `database` 段会自动迁移。
4. 执行 `/lban reload` 应用改动，或重启服务端。

## 文档

| 文档 | 内容 |
| --- | --- |
| [命令帮助](docs/LengbanlistCommandHelp.md) | 完整命令列表与用法 |
| [配置速览](docs/LengbanlistConfig.md) | `config.yml` / `storage.yml` 字段说明 |
| [插件展示](docs/Lengbanlist_Images.md) | 界面截图与实际效果 |
| [开发须知](docs/PullRequest_zh.md) | 工程结构、各包职责、扩展系统、构建与测试 |
| [扩展开发](lengbanlist-api/README.md) | 用 Java 写扩展：契约、完整示例、方法参考 |
| [多平台链接](docs/readme-website.md) | GitHub / SpigotMC / Modrinth / Discord |
| [使用限制与责任声明](docs/Mustn't_zh.md) | MPL-2.0 与 EULA 要点 |

---

## 支持这个项目 ❤️

遇到问题、想聊新功能，来 [Discord](https://discord.gg/aeWjf7vD) 坐坐。

如果这个插件对你有帮助，欢迎赞助支持。你的支持是我持续开发和维护的动力。

[![爱发电](https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E6%94%AF%E6%8C%81%E6%88%91-orange)](https://afdian.com/a/lengmc)

感谢支持 ❤️
