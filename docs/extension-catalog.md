# Lengbanlist 扩展清单

> **仓库拓扑（已定）**：18 个官方扩展是 **`Serendisand/Lengbanlist-Extensions` 这一个仓库里的 18 个 Maven 模块**，
> 不是 18 个仓库。核心与契约在 `Serendisand/Lengbanlist`。
>
> **bStats**：每个扩展一个页面（共 18 个）。bStats 的数字 ID 与插件名绑定，**创建后不便更改**，
> 所以 `plugin.yml` 的 `name` 必须先定死再建页面。
>
> 版本基线：2.1.6 · 仓库：`git@github.com:Serendisand/Lengbanlist-Extensions.git`（已创建）

---

## 一、命名约定（请先确认）

| 项目 | 规则 | 示例 |
| --- | --- | --- |
| 扩展 id | 全小写、无连字符；用于 `index.json`、`extensions.yml`、模块目录 | `vanish`、`audittools` |
| 模块目录 | 扩展仓库根下的 `<id>/` | `Lengbanlist-Extensions/vanish/` |
| Maven artifactId | `lengbanlist-ext-<id>` | `lengbanlist-ext-vanish` |
| `plugin.yml` 的 `name` | `Lengbanlist-Ext-<PascalId>` | `Lengbanlist-Ext-Vanish` |
| bStats 页面名 | 与 `plugin.yml` 的 `name` 完全一致，便于对照 | `Lengbanlist-Ext-Vanish` |
| README 与徽章 | 各模块目录下的 `README.md` | `vanish/README.md` |
| 依赖声明 | `depend: [Lengbanlist]`（这一行是扩展拿到核心 API 类的唯一来源） | — |

> 因为官方扩展集中在同一仓库，bStats 徽章只能放在**各模块子目录的 README**，不会显示在仓库首页。

**权限节点保持不变**：扩展继续使用既有的 `lengbanlist.*` 权限（如 `lengbanlist.freeze`），
不改成 `lengbanlist.<id>.*`，以免破坏现有用户的 LuckPerms 配置。

---

## 二、扩展清单（18 个模块）

`plugin.yml` 的 `name` 与 bStats 页名都用「plugin.yml name」列的值。规模为审计实测行数，供排期参考。

| # | 扩展 id | 中文名 | 模块目录 | plugin.yml name / bStats 页名 | artifactId | 规模 | 保留的权限节点 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `vanish` | 管理员隐身 | `vanish/` | `Lengbanlist-Ext-Vanish` | `lengbanlist-ext-vanish` | 179 | `lengbanlist.vanish`、`lengbanlist.vanish.see` | 纯内存，无外部依赖 |
| 2 | `freeze` | 冻结玩家 | `freeze/` | `Lengbanlist-Ext-Freeze` | `lengbanlist-ext-freeze` | 601 | `lengbanlist.freeze` | 自有 `freezes` 表，可跨服生效 |
| 3 | `staffchat` | 管理员频道 | `staffchat/` | `Lengbanlist-Ext-StaffChat` | `lengbanlist-ext-staffchat` | 90 | `lengbanlist.staffchat` | — |
| 4 | `broadcast` | 封禁人数广播 | `broadcast/` | `Lengbanlist-Ext-Broadcast` | `lengbanlist-ext-broadcast` | 186 | `lengbanlist.broadcast`、`lengbanlist.toggle` | 自带 `broadcast.yml` |
| 5 | `chestui` | 箱子 GUI 菜单 | `chestui/` | `Lengbanlist-Ext-ChestUi` | `lengbanlist-ext-chestui` | 925 | `lengbanlist.open` | 调用核心的 ban/mute/warn 服务 |
| 6 | `report` | 举报系统 | `report/` | `Lengbanlist-Ext-Report` | `lengbanlist-ext-report` | 360 | `lengbanlist.report`、`lengbanlist.admin` | 含 `/admin` 管理界面；自有 `reports` 表 |
| 7 | `appeal` | 封禁申诉 | `appeal/` | `Lengbanlist-Ext-Appeal` | `lengbanlist-ext-appeal` | 502 | — | 含网页申诉页；自有 `appeals` 表 |
| 8 | `chatfilter` | 聊天违禁词过滤 | `chatfilter/` | `Lengbanlist-Ext-ChatFilter` | `lengbanlist-ext-chatfilter` | 131 | `lengbanlist.allowmsg` | 自带 `chatconfig.yml` 词表 |
| 9 | `altdetect` | 小号与 IP 关联 | `altdetect/` | `Lengbanlist-Ext-AltDetect` | `lengbanlist-ext-altdetect` | 279 | `lengbanlist.alts` | **合并** 小号检测 + IP 关联（同一张 `player_ip_history`） |
| 10 | `vpndetect` | VPN / 代理检测 | `vpndetect/` | `Lengbanlist-Ext-VpnDetect` | `lengbanlist-ext-vpndetect` | 80 | — | 外呼第三方 `ip-api.com`（隐私敏感，默认应关闭） |
| 11 | `getip` | IP 归属地查询 | `getip/` | `Lengbanlist-Ext-GetIp` | `lengbanlist-ext-getip` | 51 | `lengbanlist.getip` | 同上，外呼 `ip-api.com` |
| 12 | `placeholderapi` | PlaceholderAPI 占位符 | `placeholderapi/` | `Lengbanlist-Ext-PlaceholderApi` | `lengbanlist-ext-placeholderapi` | 206 | — | 软依赖 PlaceholderAPI |
| 13 | `models` | 角色模型 | `models/` | `Lengbanlist-Ext-Models` | `lengbanlist-ext-models` | 1830 | `lengbanlist.model` | ⚠️ 与既有内容仓库 `Lengbanlist-Models` 区分：那是模型文本仓库，这是扩展代码模块 |
| 14 | `sync` | 跨服数据同步 | `sync/` | `Lengbanlist-Ext-Sync` | `lengbanlist-ext-sync` | 287 | `lengbanlist.sync` | 仅共享数据库（MySQL/MariaDB/PostgreSQL）有效 |
| 15 | `webpanel` | Web 管理面板 | `webpanel/` | `Lengbanlist-Ext-WebPanel` | `lengbanlist-ext-webpanel` | 5818 | `lengbanlist.reload` | 最大的一块；**合并**面板主题/必应壁纸 |
| 16 | `audittools` | 审计工具 | `audittools/` | `Lengbanlist-Ext-AuditTools` | `lengbanlist-ext-audittools` | 467 | `lengbanlist.audit`、`lengbanlist.export`、`lengbanlist.rollback` | **合并** 哈希链防篡改 + 导出 + 操作回滚（同属审计域） |
| 17 | `punishpolicy` | 处罚策略 | `punishpolicy/` | `Lengbanlist-Ext-PunishPolicy` | `lengbanlist-ext-punishpolicy` | 213 | — | **合并** 权重免疫 + 时长自动升级（两者都要插入核心处罚决策的 Hook） |
| 18 | `punishnotify` | 处罚通知 | `punishnotify/` | `Lengbanlist-Ext-PunishNotify` | `lengbanlist-ext-punishnotify` | 102 | — | **合并** 封禁到期提醒 + 离线警告（都是被动通知） |

合计约 12,700 行从核心迁出。

### 需要你回填的一列

创建 bStats 页面后，把分配到的数字 ID 填到下表（我据此接入 `new Metrics(plugin, <ID>)`）：

| 扩展 id | bStats 数字 ID |
| --- | --- |
| `vanish` | |
| `freeze` | |
| `staffchat` | |
| `broadcast` | |
| `chestui` | |
| `report` | |
| `appeal` | |
| `chatfilter` | |
| `altdetect` | |
| `vpndetect` | |
| `getip` | |
| `placeholderapi` | |
| `models` | |
| `sync` | |
| `webpanel` | |
| `audittools` | |
| `punishpolicy` | |
| `punishnotify` | |

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
| `LengbanlistAPI` 旧门面 | **已删除** | 对外是"API"、对内从未被使用；删除后核心不再持有 `org.leng.api` 包，split package 问题一并消失 |

bStats 本身**留核心**（约 727 行），只上报核心自身；各扩展用自己的 ID 分别上报，这样能看到每个功能真实的使用量。

---

## 五、操作顺序

1. **我完成 Phase 0**（扩展契约、ExtensionRegistry、Hook 链、统一门控、`features.*` → `extensions.yml`）
2. **我创建 `Lengbanlist-Extensions` 的多模块骨架**（父 pom + 18 个模块目录 + CI），推送代码
3. **你为 18 个扩展各建一个 bStats 页面**（名字用本表的 `plugin.yml name` 列）
4. **你把数字 ID 回填到上面第二张表**
5. **我接入 `new Metrics(plugin, <ID>)`，并在各模块 `README.md` 加上 bStats 徽章**
6. **我按 Phase 1-3 迁移各扩展代码**（先无表无横切的，最后是 models/sync/webpanel）

> 依赖关系：第 4 步的 ID 是第 5 步的输入；第 2 步的模块目录与 `plugin.yml name` 一旦推送并与 bStats 页面绑定，
> 就不宜再改名，因此本表请先确认再动手。
