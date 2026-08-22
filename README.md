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
 **[Discord](https://discord.gg/thAGyuFrX)**
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

**角色模型**
内置了 12 种角色风格——胡桃、芙宁娜、钟离、刻晴、纳西妲、可莉、八重神子等等。切换之后所有提示消息的措辞和语气都跟着变。同时还支持自定义模型：在 plugins/Lengbanlist/models/ 下放 YAML 文件就能定义自己的消息风格，首次启动会自动生成示例文件帮你上手。

**图形界面 —— `/lban open`**
一个 54 格的箱子界面，封禁、解禁、禁言、重载、切模型这些操作点一点就行。封禁和解禁还有聊天向导，一步步引导你输入，不用记命令格式。

**Web 管理面板**
内置了一个 HTTP 管理页面，浏览器打开就能用。做了 JWT 鉴权和限流。封禁、解禁、禁言、警告、查记录、重载配置，页面上都能操作。

**查询工具 —— `/check` `/history` `/getip`**
查玩家或 IP 的当前处罚状态、历史记录、关联信息、IP 归属地。

**管理员频道 —— `/sc`**
管理员之间聊天的专用频道，普通玩家看不到。

**操作回滚 —— `/lban rollback`**
管理员误操作了怎么办？可以回滚。基于审计日志，指定操作人和时间范围，把封禁、解封、禁言、警告等操作一键回滚：封禁的解开、解封的重新封、警告的撤销。也支持只回滚某类操作（如只回滚封禁）。

**其他**
插件版本、内存、CPU、在线人数这些信息用 `/info` 就能看。支持 SQLite 和 MySQL，从旧版 YAML 存储也能自动迁移。有 bStats 统计和自动更新。

## 使用说明

1. 把插件 jar 扔进服务端的 `plugins` 目录。
2. 重启服务器，插件会自动生成配置文件。
3. 按需改 `config.yml` 和其他配置。
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
