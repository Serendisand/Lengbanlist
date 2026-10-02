# Lengbanlist 扩展清单

> **仓库拓扑（已定）**：官方扩展是 **`Serendisand/Lengbanlist-Extensions` 这一个仓库里的多个 Maven 模块**，
> 不是多个仓库。核心与契约在 `Serendisand/Lengbanlist`。
>
> **bStats**：每个扩展一个页面。bStats 的数字 ID 与插件名绑定，**创建后不便更改**，
> 所以 `plugin.yml` 的 `name` 必须先定死再建页面。
>
> 版本基线：2.1.6 · 仓库：`git@github.com:Serendisand/Lengbanlist-Extensions.git`（已创建）

---

## 一、命名约定

| 项目 | 规则 | 示例 |
| --- | --- | --- |
| 扩展 id | 全小写、无连字符；用于 `index.json`、`extensions.yml`、模块目录 | `punishpolicy`、`ipintel` |
| 模块目录 | 扩展仓库根下的 `<id>/` | `Lengbanlist-Extensions/punishpolicy/` |
| Maven artifactId | `lengbanlist-ext-<id>` | `lengbanlist-ext-punishpolicy` |
| `plugin.yml` 的 `name` | `Lengbanlist-Ext-<PascalId>` | `Lengbanlist-Ext-PunishPolicy` |
| bStats 页面名 | 与 `plugin.yml` 的 `name` 完全一致，便于对照 | `Lengbanlist-Ext-PunishPolicy` |
| README 与徽章 | 各模块目录下的 `README.md` | `punishpolicy/README.md` |
| 依赖声明 | `depend: [Lengbanlist]`（这一行是扩展拿到核心契约类的唯一来源） | — |

> 因为官方扩展集中在同一仓库，bStats 徽章只能放在**各模块子目录的 README**，不会显示在仓库首页。

**权限节点保持不变**：扩展继续使用既有的 `lengbanlist.*` 权限（如 `lengbanlist.freeze`），
不改成 `lengbanlist.<id>.*`，以免破坏现有用户的 LuckPerms 配置。

---

## 二、扩展清单（10 个模块）

> **为什么是 10 个而不是初版的 18 个**：初版按"最小可拆单元"划分，结果有 8 个模块不足 300 行，
> 每个都要单独开 bStats 页面、单独写 README、单独发版，维护成本与收益不成比例。
> 现按**同一领域 / 同一生命周期**合并。合并原则只有一条：**模块内部要能一句话说清它是干什么的**——
> 凡是需要"它包含了 A、B、C 三件不相关的事"来解释的，就不合并。

| # | 扩展 id | 名称 | 模块目录 | plugin.yml name / bStats 页名 | artifactId | 规模 | 合并了哪些 | 保留的权限节点 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `webpanel` | Web 管理面板 | `webpanel/` | `Lengbanlist-Ext-WebPanel` | `lengbanlist-ext-webpanel` | 5818 | 面板 + 主题/必应壁纸 | `lengbanlist.reload` |
| 2 | `models` | 角色模型 | `models/` | `Lengbanlist-Ext-Models` | `lengbanlist-ext-models` | 1830 | — | `lengbanlist.model` |
| 3 | `stafftools` | 管理员游戏内工具 | `stafftools/` | `Lengbanlist-Ext-StaffTools` | `lengbanlist-ext-stafftools` | 1705 | 隐身 + 冻结 + 箱子菜单 | `lengbanlist.vanish`、`.vanish.see`、`lengbanlist.freeze`、`lengbanlist.open` |
| 4 | `feedback` | 举报与申诉 | `feedback/` | `Lengbanlist-Ext-Feedback` | `lengbanlist-ext-feedback` | 862 | 举报 + 申诉 | `lengbanlist.report`、`lengbanlist.admin` |
| 5 | `bridge` | 外部互通 | `bridge/` | `Lengbanlist-Ext-Bridge` | `lengbanlist-ext-bridge` | 493 | 跨服同步 + PlaceholderAPI 占位符 | `lengbanlist.sync` |
| 6 | `audit` | 审计与追溯 | `audit/` | `Lengbanlist-Ext-Audit` | `lengbanlist-ext-audit` | 467 | 哈希链 + 导出 + 操作回滚 | `lengbanlist.audit`、`.export`、`.rollback` |
| 7 | `ipintel` | IP 情报与风控 | `ipintel/` | `Lengbanlist-Ext-IpIntel` | `lengbanlist-ext-ipintel` | 410 | 小号/IP 关联 + VPN 检测 + IP 归属地 | `lengbanlist.alts`、`lengbanlist.getip` |
| 8 | `notify` | 处罚通知 | `notify/` | `Lengbanlist-Ext-Notify` | `lengbanlist-ext-notify` | 288 | 封禁广播 + 到期提醒 + 离线警告 | `lengbanlist.broadcast`、`lengbanlist.toggle` |
| 9 | `chat` | 聊天管控 | `chat/` | `Lengbanlist-Ext-Chat` | `lengbanlist-ext-chat` | 221 | 违禁词过滤 + 管理员频道 | `lengbanlist.allowmsg`、`lengbanlist.staffchat` |
| 10 | `punishpolicy` | 处罚判定策略 | `punishpolicy/` | `Lengbanlist-Ext-PunishPolicy` | `lengbanlist-ext-punishpolicy` | 213 | 权重免疫 + 时长自动升级 | — |

合计约 12,300 行从核心迁出。

### 合并理由（逐条）

| 合并 | 为什么是一件事 |
| --- | --- |
| 隐身 + 冻结 + 箱子菜单 → `stafftools` | 三者都是"管理员在游戏内直接作用于在线玩家"的操作面；箱子里本就同时有隐身与冻结按钮 |
| 举报 + 申诉 → `feedback` | 同一条生命周期：玩家举报 → 处理 → 被封 → 申诉 → 复核。共用报告/申诉 Web 端点与审核权限 |
| 跨服同步 + 占位符 → `bridge` | 都是"把核心的数据/状态暴露给外部系统"，一个面向另一台服务器，一个面向别的插件 |
| 哈希链 + 导出 + 回滚 → `audit` | 同属审计域，共用 `audit_logs` 表与 `lengbanlist.audit` 权限 |
| 小号/IP 关联 + VPN + 归属地 → `ipintel` | 三者都围绕 IP：关联历史、代理识别、地理位置。共用 `player_ip_history` 与外呼 `ip-api.com` 的限流 |
| 广播 + 到期提醒 + 离线警告 → `notify` | 都是"被动把处罚相关消息推给玩家"，无人调用也照样按定时器工作 |
| 违禁词 + 管理员频道 → `chat` | 都在 `AsyncPlayerChatEvent` 上：一个过滤、一个改投递目标 |

### 刻意不合并的

| 没合并 | 理由 |
| --- | --- |
| `webpanel`（5818） | 已经很大；再并入任何东西都会变成"什么都装"的巨石 |
| `models`（1830） | 与内容仓库 `Lengbanlist-Models` 是一对，边界清晰，混入代码功能会让内容作者困惑 |
| `punishpolicy`（213） | 虽小，但它是**唯一以钩子形式插进核心处罚判定**的模块。并进 `notify` 会让"判定策略"和"结果通知"两件事混在一个模块里，而这两者的失败模式完全不同（前者判错会误封，后者发错只是骚扰） |

### 需要你回填的一列

创建 bStats 页面后，把分配到的数字 ID 填到下表（我据此接入 `new Metrics(plugin, <ID>)`）：

| 扩展 id | plugin.yml name | bStats 数字 ID |
| --- | --- | --- |
| `webpanel` | `Lengbanlist-Ext-WebPanel` | |
| `models` | `Lengbanlist-Ext-Models` | |
| `stafftools` | `Lengbanlist-Ext-StaffTools` | |
| `feedback` | `Lengbanlist-Ext-Feedback` | |
| `bridge` | `Lengbanlist-Ext-Bridge` | |
| `audit` | `Lengbanlist-Ext-Audit` | |
| `ipintel` | `Lengbanlist-Ext-IpIntel` | |
| `notify` | `Lengbanlist-Ext-Notify` | |
| `chat` | `Lengbanlist-Ext-Chat` | |
| `punishpolicy` | `Lengbanlist-Ext-PunishPolicy` | |

> ⚠️ **改名成本正在上涨**：模块目录、`plugin.yml` 的 `name` 一旦推送并与 bStats 页面绑定就不宜再改。
> 现在（页面尚未创建、代码尚未迁移）是改名的最后低成本窗口——要调就趁现在。

---

## 三、不进扩展、留核心的功能

| 功能 | 决定 | 理由 |
| --- | --- | --- |
| `/lban tp` | **留核心** | 仅 40 行，是 `/lban` 的一个子命令；为它单开模块 + bStats 页的维护成本高于收益 |
| 封禁/解封/IP封禁/改期 | 留核心 | 处罚闭环核心 |
| 警告/撤销警告、禁言/解禁 | 留核心 | 处罚闭环核心 |
| 查询 check/history、踢出、审计日志 | 留核心 | 处罚闭环核心 |
| 到期停用与历史清理任务 | 留核心 | 不跑则封禁永不过期、库无限膨胀 |
| 数据库层（4 方言）、封禁/警告缓存 | 留核心 | 一切的前提 |
| 命令注册与门控机制 | 留核心 | 扩展体系本身 |
| 重载配置 | 留核心 | — |

## 四、明确删除

| 功能 | 决定 | 理由 |
| --- | --- | --- |
| 启动「一言」彩蛋 | **删除** | 每次插件启用都无条件外呼 `v1.hitokoto.cn`，零功能价值（见功能审计风险项） |
| `LengbanlistAPI` 旧门面 | **已删除** | 对外是"API"、对内从未被使用；删除后核心不再持有 `org.leng.api` 源码，split package 问题一并消失 |

bStats 本身**留核心**（约 727 行），只上报核心自身；各扩展用自己的 ID 分别上报，这样能看到每个功能真实的使用量。

---

## 五、命令界面：一个必须先定的问题

初版规划漏掉了这条，而它会影响所有扩展，所以在这里写明。

**现状**：核心的命令分两种形态。

| 形态 | 例子 | 注册方式 |
| --- | --- | --- |
| 顶层命令 | `/ban`、`/warn`、`/sc`、`/getip`、`/report` | `CommandRegistry` 运行时按 `features.*` 注册；`plugin.yml` **故意不声明**，这样功能关掉时名字能留给别的插件 |
| `/lban` 子命令 | `/lban vanish`、`/lban freeze`、`/lban models`、`/lban audit`、`/lban a`、`/lban toggle`、`/lban reload` | `LengbanlistCommand` 里的一个大 `switch`，配合 `CommandRegistry.SUBCOMMAND_FEATURES` |

而契约里的 `CommandSpec.name` **只表达顶层命令名**（`usage` 只是文档串）。于是当 `vanish` 这类
子命令功能搬进扩展时，会出现两条路：

| 方案 | 结果 | 代价 |
| --- | --- | --- |
| **A. 扩展注册顶层命令** | `/lban vanish` → `/vanish` | **用户可见的破坏性变更**，与"行为不变"的硬要求冲突 |
| **B. 契约支持 `parent` 字段** | 扩展声明 `parent = "lban"`，核心把它挂进 `/lban` 的分派表 | 契约要加一个字段、核心加一张分派表；UX 完全不变 |

**倾向 B**，因为 Phase 0 的硬要求是"既有功能行为不变"，而 A 会让老用户的肌肉记忆和现有教程失效。
但这属于你拍板的范围（影响 `CommandSpec` 的公开契约），**在动手迁移带命令的扩展之前需要你确认**。

`punishpolicy` 不注册任何命令，因此不受这个问题影响——它是第一个可以安全迁移的模块。

---

## 六、操作顺序

1. ✅ **Phase 0 已完成**（扩展契约、ExtensionRegistry、Hook 链、统一门控、`features.*` → `extensions.yml`）
2. ✅ **`Lengbanlist-Extensions` 已创建**（空仓库）
3. **我创建多模块骨架**（父 pom + 10 个模块目录 + CI）并推送代码
4. **你为 10 个扩展各建一个 bStats 页面**（名字用上表的 `plugin.yml name` 列）
5. **你把数字 ID 回填到第二张表**
6. **我接入 `new Metrics(plugin, <ID>)`，并在各模块 `README.md` 加上 bStats 徽章**
7. **我按 Phase 1-3 迁移各扩展代码**（先 `punishpolicy` 与无表无横切的，最后是 `models`/`bridge`/`webpanel`）

> 依赖关系：第 5 步的 ID 是第 6 步的输入；第 3 步的模块目录与 `plugin.yml name` 一旦推送并与
> bStats 页面绑定，就不宜再改名，因此本表请先确认再动手。
