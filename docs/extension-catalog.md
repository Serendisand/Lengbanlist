# Lengbanlist 扩展清单（用于创建 GitHub 仓库与 bStats 页面）

> 用途：这是创建仓库与 bStats 页面的**唯一依据**。
> 命名一旦用于 bStats 页面就**不便更改**（ID 与插件名绑定），因此请先确认本表再动手。
>
> 版本基线：2.1.6 · 清单日期：随生态改造 Phase 0

---

## 一、命名约定（请先确认）

| 项目 | 规则 | 示例 |
| --- | --- | --- |
| 扩展 id | 全小写、无连字符，用于 `index.json` 与 `extensions.yml` | `vanish`、`audittools` |
| GitHub 仓库 | `Serendisand/Lengbanlist-Ext-<PascalId>` | `Lengbanlist-Ext-Vanish` |
| Maven artifactId | `lengbanlist-ext-<id>` | `lengbanlist-ext-vanish` |
| `plugin.yml` 的 `name` | 与 GitHub 仓库名一致 | `Lengbanlist-Ext-Vanish` |
| bStats 页面名 | 与 `plugin.yml` 的 `name` 一致，便于对照 | `Lengbanlist-Ext-Vanish` |
| 依赖声明 | `depend: [Lengbanlist]`（这一行是扩展拿到核心 API 类的唯一来源） | — |

**权限节点保持不变**：扩展会继续使用既有的 `lengbanlist.*` 权限（如 `lengbanlist.freeze`），不改成 `lengbanlist.<id>.*`，以免破坏现有用户的 LuckPerms 配置。

---

## 二、扩展清单（共 18 个）

`plugin.yml` 的 `name` 与 bStats 页名都用「GitHub 仓库」列的值。

| # | 扩展 id | 中文名 | GitHub 仓库 / bStats 页名 | Maven artifactId | 规模 | 保留的权限节点 | 备注 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `vanish` | 管理员隐身 | `Lengbanlist-Ext-Vanish` | `lengbanlist-ext-vanish` | 179 行 | `lengbanlist.vanish`、`lengbanlist.vanish.see` | 纯内存，无外部依赖 |
| 2 | `freeze` | 冻结玩家 | `Lengbanlist-Ext-Freeze` | `lengbanlist-ext-freeze` | 601 行 | `lengbanlist.freeze` | 自有 `freezes` 表，可跨服生效 |
| 3 | `staffchat` | 管理员频道 | `Lengbanlist-Ext-StaffChat` | `lengbanlist-ext-staffchat` | 90 行 | `lengbanlist.staffchat` | — |
| 4 | `broadcast` | 封禁人数广播 | `Lengbanlist-Ext-Broadcast` | `lengbanlist-ext-broadcast` | 186 行 | `lengbanlist.broadcast`、`lengbanlist.toggle` | 自带 `broadcast.yml` |
| 5 | `chestui` | 箱子 GUI 菜单 | `Lengbanlist-Ext-ChestUi` | `lengbanlist-ext-chestui` | 925 行 | `lengbanlist.open` | 调用核心的 ban/mute/warn 服务 |
| 6 | `report` | 举报系统 | `Lengbanlist-Ext-Report` | `lengbanlist-ext-report` | 360 行 | `lengbanlist.report`、`lengbanlist.admin` | 含 `/admin` 管理界面；自有 `reports` 表 |
| 7 | `appeal` | 封禁申诉 | `Lengbanlist-Ext-Appeal` | `lengbanlist-ext-appeal` | 502 行 | — | 含网页申诉页；自有 `appeals` 表 |
| 8 | `chatfilter` | 聊天违禁词过滤 | `Lengbanlist-Ext-ChatFilter` | `lengbanlist-ext-chatfilter` | 131 行 | `lengbanlist.allowmsg` | 自带 `chatconfig.yml` 词表 |
| 9 | `altdetect` | 小号与 IP 关联 | `Lengbanlist-Ext-AltDetect` | `lengbanlist-ext-altdetect` | 279 行 | `lengbanlist.alts` | **合并** 小号检测 + IP 关联（同一张 `player_ip_history`） |
| 10 | `vpndetect` | VPN / 代理检测 | `Lengbanlist-Ext-VpnDetect` | `lengbanlist-ext-vpndetect` | 80 行 | — | 外呼第三方 `ip-api.com`（隐私敏感，默认应关闭） |
| 11 | `getip` | IP 归属地查询 | `Lengbanlist-Ext-GetIp` | `lengbanlist-ext-getip` | 51 行 | `lengbanlist.getip` | 同上，外呼 `ip-api.com` |
| 12 | `placeholderapi` | PlaceholderAPI 占位符 | `Lengbanlist-Ext-PlaceholderApi` | `lengbanlist-ext-placeholderapi` | 206 行 | — | 软依赖 PlaceholderAPI |
| 13 | `models` | 角色模型 | `Lengbanlist-Ext-Models` | `lengbanlist-ext-models` | 1830 行 | `lengbanlist.model` | ⚠️ 与既有内容仓库 `Lengbanlist-Models` 区分：那是模型文本仓库，这个是扩展代码仓库 |
| 14 | `sync` | 跨服数据同步 | `Lengbanlist-Ext-Sync` | `lengbanlist-ext-sync` | 287 行 | `lengbanlist.sync` | 仅共享数据库（MySQL/MariaDB/PostgreSQL）有效 |
| 15 | `webpanel` | Web 管理面板 | `Lengbanlist-Ext-WebPanel` | `lengbanlist-ext-webpanel` | 5818 行 | `lengbanlist.reload` | 最大的一块；**合并**面板主题/必应壁纸 |
| 16 | `audittools` | 审计工具 | `Lengbanlist-Ext-AuditTools` | `lengbanlist-ext-audittools` | 467 行 | `lengbanlist.audit`、`lengbanlist.export`、`lengbanlist.rollback` | **合并** 哈希链防篡改 + 导出 + 操作回滚（同属审计域） |
| 17 | `punishpolicy` | 处罚策略 | `Lengbanlist-Ext-PunishPolicy` | `lengbanlist-ext-punishpolicy` | 213 行 | — | **合并** 权重免疫 + 时长自动升级（两者都要插入核心处罚决策的 Hook） |
| 18 | `punishnotify` | 处罚通知 | `Lengbanlist-Ext-PunishNotify` | `lengbanlist-ext-punishnotify` | 102 行 | — | **合并** 封禁到期提醒 + 离线警告（都是被动通知） |

### 需要你回填的一列

创建 bStats 页面后，把分配到的数字 ID 填到下表（我会据此在各扩展里接入 `new Metrics(plugin, <ID>)`）：

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
| `/lban tp` | **留核心** | 仅 40 行，是 `/lban` 的一个子命令；为它单开仓库 + bStats 页的维护成本高于收益 |
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

bStats 本身**留核心**（约 727 行），只上报核心自身；各扩展用自己的 ID 分别上报，这样能看到每个功能真实的使用量。

---

## 五、操作顺序（按你设想的流程）

1. **我完成 Phase 0–3 的代码改造**（当前 Phase 0 进行中）
2. **你按本表创建 18 个 GitHub 仓库**
3. **我推送各扩展代码**
4. **你为每个扩展创建 bStats 页面**（名字用本表的 `plugin.yml name` 列）
5. **你把 bStats 数字 ID 回填到上面第二张表**
6. **我接入 `new Metrics(plugin, <ID>)` 并在各仓库 README 加上 bStats 徽章**

> 依赖关系：第 5 步的 ID 是第 6 步的输入，所以顺序不能颠倒；第 2 步的仓库名是第 3 步推送的目标，所以仓库名一旦创建就与上表一致、不要改名。
