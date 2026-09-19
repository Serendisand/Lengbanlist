[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.x-brightgreen)](https://www.minecraft.net)
[![License](https://img.shields.io/badge/License-MPL2.0-blue)](LICENSE)

<div align="center">
<p>
    <img width="200" src="/Photos/Lengbanlist-icon.png">
</p>

 *简体中文 | [English](docs/README_en.md)* 

 **[多平台链接](docs/readme-website.md)** |
 **[开发须知](docs/PullRequest_zh.md)** |
 *[许可证提示](docs/Mustn't_zh.md)* |
 **[Discord](https://discord.gg/aeWjf7vD)**
</div>

![Lengbanlist](https://github.com/Serendisand/Lengbanlist/blob/main/Photos/Lengbanlist.png)
![Lengbanlist](https://bstats.org/signatures/bukkit/Lengbanlist.svg)

## 这个插件是干什么的

Lengbanlist 最早只是个封禁广播插件，现在已经长成一套完整的服务器管理工具了：封禁、警告、禁言、举报、聊天过滤、IP 关联检测、VPN 检测、Web 管理面板…… 日常服务器管理用得上的东西基本都在里面。

所有功能都可以在配置文件里单独开关，不需要的关掉就行，不会拖累服务器。

## 功能一览

**封禁相关的 —— `/ban` `/ban-ip` `/unban` `/setban`**
封禁玩家或者 IP，时长可以写秒/分/时/天/周/月/年，也可以直接 `forever` 永久封，或者 `auto` 让插件根据警告次数自动算。封完了想改时长和原因也行。同时支持 IP 段封禁（如 `172.198.2.x` 或 `172.198.2.0/24`），可一次性拦截整个网段。

**警告系统 —— `/warn` `/unwarn`**
给玩家记警告。内置了个自动封禁逻辑（LBAC）：30 天内累计 3 次警告自动封禁，封禁时长会随着触发次数递增。如果你撤销警告减到阈值以下，封禁也会自动解除，不用管理员手动处理。

**禁言 —— `/mute` `/unmute` `/listmute`**
禁言玩家一段时间或永久，禁言期间发不出消息。可以随时解禁，也能查看当前禁言列表。

**聊天过滤**
配置文件里自己定敏感词列表，触发了自动替换成"喵"。触发次数多了会自动禁言。可疑消息会带按钮通知管理员，点一下就能放行或警告。

**IP 关联 & VPN 检测**
自动记下每个玩家的登录 IP，发现不同玩家用了同一个 IP 时提醒管理员。玩家进服时还会检测是不是 VPN 或代理，可以设置只警告、踢出去、或者直接封掉。

**举报系统 —— `/report` `/admin`**
玩家可以直接举报违规行为，管理员处理后举报人会收到处理通知。

**封禁广播**
定时在聊天栏广播当前的封禁统计数字，消息内容和格式完全自己改。也可以手动触发广播，随时开关。

**角色模型（云端下载）**
模型不再写死在插件里，而是从云端仓库 [Lengbanlist-Models](https://github.com/Serendisand/Lengbanlist-Models) 按需下载：`/lban models list` 看有哪些风格，`/lban models install <ID>` 装一个，`/lban model <名称>` 切过去，之后所有提示消息的措辞和语气都跟着变。仓库现有 25 种角色风格（胡桃、芙宁娜、钟离、刻晴、可莉、八重神子、爱诺、哥伦比娅……），每月更新，还能投票选出月度精选。

模型文本支持**继承**：全局默认写在 `models/_base.yml`，模型文件只写自己不一样的部分，没写的自动沿用全局默认。所以自己写模型时抄几个字段就行，插件升级新增文案也不会让老模型掉队。

**图形界面 —— `/lban open`**
一个 54 格的箱子界面：封禁、解禁、改期限、禁言、警告、冻结、隐身、切模型、重载……能点的按钮都按你的权限显示，点下去该干活的就干活。封禁、禁言、警告、冻结都是聊天向导，一步步问你要什么，不用记命令格式，中途发个 `cancel` 就能退出。

**Web 管理面板**
内置了一个 HTTP 管理页面，浏览器打开就能用。做了 JWT 鉴权和限流。封禁、解禁、禁言、警告、查记录、重载配置，页面上都能操作。

**查询工具 —— `/check` `/history` `/getip`**
查玩家或 IP 的当前处罚状态、历史记录、关联信息、IP 归属地。

**管理员隐身 & 冻结 —— `/lban vanish` `/lban freeze`**
`/lban vanish` 让自己被其他玩家彻底看不见——连同手持物品和盔甲一起隐藏，玩家列表里也不会出现（有 `lengbanlist.vanish.see` 权限的管理员照常看得见）。
`/lban freeze <玩家> <理由>` 把玩家定在原地：不能移动、破坏、交互、丢东西、用命令或传送，屏幕上会弹红色标题「你已被冻结」和理由，聊天栏也会收到提示。解冻只能手动 `/lban unfreeze <玩家|all>`。冻结记录存在 `frozen.yml` 里，重启不丢。

**多子服共用一套数据**
数据库配置拆到了 `storage.yml`（config.yml 只留功能开关）。给每台子服起个 `server-name`，共用同一份 MySQL / MariaDB / PostgreSQL 时，审计日志会记下每条操作来自哪台子服、哪个操作员，`/lban audit` 和 Web 面板都能看到。

**管理员频道 —— `/sc`**
管理员之间聊天的专用频道，普通玩家看不到。

**操作回滚 —— `/lban rollback`**
管理员误操作了怎么办？可以回滚。基于审计日志，指定操作人和时间范围，把封禁、解封、禁言、警告等操作一键回滚：封禁的解开、解封的重新封、警告的撤销。也支持只回滚某类操作（如只回滚封禁）。

**其他**
插件版本、内存、CPU、在线人数这些信息用 `/info` 就能看。支持 SQLite、MySQL、MariaDB、PostgreSQL，从旧版 YAML 存储也能自动迁移。有 bStats 统计和自动更新。

## 使用说明

1. 把插件 jar 扔进服务端的 `plugins` 目录。
2. 重启服务器，插件会自动生成配置文件。
3. 按需改 `config.yml`（功能开关和玩法）和 `storage.yml`（数据库、保留策略、服务器标识）。旧版本写在 config.yml 里的 `database` 段会自动迁移过去。
4. `/lban reload` 重载配置，或者重启服务器。

## 命令帮助

完整命令列表和用法见： [Lengbanlist 命令帮助](docs/LengbanlistCommandHelp.md)

## 插件展示

[点这里看插件截图和实际效果](docs/Lengbanlist_Images.md)

---

## 欢迎支持我的项目！❤️

如果你觉得这插件有用，或者喜欢我做的这些东西，欢迎赞助支持一下。你的支持能让我继续开发和维护，也让我有动力做更多好玩的东西。

## 赞助方式

[![爱发电 Sponsor](https://img.shields.io/badge/%E7%88%B1%E5%8F%91%E7%94%B5-%E6%94%AF%E6%8C%81%E6%88%91-orange)](https://afdian.com/a/lengmc)

感谢你的支持！❤️
