# refactor-fabric ↔ main 功能对齐清单

> 本文档记录 `refactor-fabric` 分支相对 `main` 分支缺失的功能与修复，并给出每个点的搬运指引。状态在动手前请确认 `git fetch origin` 后再核对一遍 commit hash。

## 总览

`refactor-fabric` 当前 HEAD：`c77e575`。
`main` 当前 HEAD：`14b8b66`。
`refactor-fabric` 缺 main 上 7 个提交（其中 6 个功能性修复 + 1 个版本号 release 标记 1a2b77e）：

| Commit | 摘要 | 难度 | 涉及模块 |
|---|---|---|---|
| `f4d381d` | 保证封禁写入原子性并传播数据库错误 | 大 | common 多个 manager + bukkit 多个命令 |
| `12c90ef` | 完善封禁事务原子性和失败处理 | 巨大 | `BanManager`/`DatabaseManager`/`LengbanlistCommand`（837 行）/`ReportManager`/`WarnManager`/`WebServer` |
| `1bc74f4` | 修复举报转封禁事务守卫冲突 + IPv6 恢复 | 中 | `LengbanlistCommand`/`BanManager`/`BanMutationFeedback`/`DatabaseManager`/`StorageMigrationManager`/`IpMatcher` |
| `6dae4b0` | 修复安全审查发现的13个问题 | 大 | 13 个文件，含完整 CORS/JWT/SHA-256/CIDR 防御 |
| `177a1be` | 自动更新校验误拒官方JAR | 中 | `AutoUpdateManager` + 新增单元测试 |
| `14b8b66` | 审计日志执行人统一为 CONSOLE | 中 | `Utils.getSenderName` + 多命令 + `RollbackManager` + `WebServer` |
| `1a2b77e` | 版本号 release 标记 V1.9.9 | 小 | 仅 pom 版本号 |

## 平台抽象带来的差异（搬运时必须重新适配）

`refactor-fabric` 在 `common` 模块实现了 `LengbanlistPlatform` 抽象，部分 main 上的"直接访问 Lengbanlist 实例"的代码在 refactor-fabric 必须改成访问平台接口：

| main 上的写法 | refactor-fabric 上的对应 |
|---|---|
| `Lengbanlist.getInstance().xxx` | `plugin.xxx`（类型为 `LengbanlistPlatform`） |
| 直接读 `Lengbanlist` 实例的 `getXxxManager()` | 走平台接口 `plugin.getXxxManager()` |
| `Utils.sendMessage(sender, msg)`（bukkit 包） | 改用 lambda `msg -> Utils.sendMessage(sender, msg)` 适配 `MessageSink` |
| `CommandSender` 直接传给 common 类 | 必须先包装成 `MessageSink` |

具体受影响的位置见下面每个提交的搬运说明。

## 文件位置差异

- `Lengbanlist - main/src/main/java/...`（main 上的扁平布局）→ refactor-fabric 已改为模块化布局：
  - `Lengbanlist - main/common/src/main/java/...`（平台无关）
  - `Lengbanlist - main/bukkit/src/main/java/...`（Bukkit 专属）
  - `Lengbanlist - main/fabric/src/main/java/...`（Fabric 专属）
- `BanMutationFeedback` 在 main 上位于 `org.leng.commands`，refactor-fabric 已迁到 `org.leng.manager`（common 模块）。

---

## 提交 `f4d381d` —— 保证封禁写入原子性并传播数据库错误

### 核心改动
1. 引入 `BanManager.BanMutationResult` 枚举（`APPLIED` / `NOT_ACTIVE` / `STATE_CHANGED` / `REJECTED_PRIVATE_OR_RESERVED_IP` / `DATABASE_ERROR`）
2. `BanManager` 把 `banIp` / `banPlayer` / `unbanIp` / `unbanPlayer` 改为 `tryBanIp` / `tryBanPlayer` / `tryUnbanIp` / `tryUnbanPlayer`，返回 `BanMutationResult`
3. 失败时上层调用方通过 `BanMutationFeedback.sendFailure(...)` 提示

### refactor-fabric 现状
- `BanManager`（common 模块，line 35-144）已含 `tryBanPlayer/tryBanIp/tryUnbanPlayer/tryUnbanIp` 与 `BanMutationResult` ✓
- `BanMutationFeedback` 已搬到 `org.leng.manager`，仅保留 `MessageSink` 重载 ✓
- bukkit 端 8 处调用方已改为 `msg -> Utils.sendMessage(sender, msg)` ✓
- **缺**：`f4d381d` 的"成功后才发 addBan/addBanIp 消息（silent 路径）"逻辑

### 搬运指引
- `bukkit/.../commands/BanCommand.java`：在 `tryBanPlayer` 成功分支后追加 silent 后的 `addBan` 模型消息
- `bukkit/.../commands/BanIpCommand.java`：同上，silent 后追加 `addBanIp` 模型消息
- `bukkit/.../commands/LengbanlistCommand.java`：silent 后追加 `addBan`/`addBanIp`，以及 escalation 消息的成功条件分支
- `bukkit/.../commands/UnbanCommand.java`：基本已对齐（沉默失败也已被 tryXxx 系列处理）
- `bukkit/.../listeners/PlayerJoinListener.java`：尝试 unban 过期改走 `tryUnbanXxx(..., null, true)`
- `common/.../web/WebServer.java`：`BanMutationResult` 已用上，但要核对"silent"参数语义是否与 main 一致
- `common/.../manager/ReportManager.java`：核对 `banFromReport` 路径里 silent 处理

---

## 提交 `12c90ef` —— 完善封禁事务原子性和失败处理

### 核心改动
1. `LengbanlistCommand.java` **837 行** 大改：拆分子命令到独立类 / 重构 escalation 流程 / 把所有 `plugin.getBanManager().banXxx()` 改为 `tryBanXxx` + 检查结果 / audit 入口统一
2. `BanManager` 进一步细化：失败时不再静默，broadcast / kick / audit 都按 silent 区分
3. `BanMutationFeedback` 从 `org.leng.commands` 迁移到 `org.leng.manager`（与 refactor-fabric 已就位的位置一致）
4. `DatabaseManager` 调整 upsert 路径返回 `WriteResult`，与 `BanMutationResult` 对接
5. `ReportManager` / `WarnManager` 也走 `tryBan*` 路径

### refactor-fabric 现状
- `BanMutationFeedback` 已迁到 `manager` 包 ✓
- `BanManager` 已有 `tryBanXxx` 与 `BanMutationResult` ✓
- `LengbanlistCommand` 是 `bukkit/.../commands/LengbanlistCommand.java`，但**整文件结构与 main 差异大**：refactor-fabric 上 `lban` 入口还是单体类，没有拆子命令

### 搬运指引
- 不建议逐文件搬运，因为 `LengbanlistCommand.java` 已经是平台化重构后的结构（plugin 是 `Lengbanlist` 实例，`addSilent` 局部变量、`Utils.sendMessage` 调用都是 refactor-fabric 风格）
- 正确做法：**只搬运增量功能**——读取 `git show 12c90ef -- LengbanlistCommand.java` 的 diff，按"是否已经在 refactor-fabric 中实现"分类：
  - "改 tryBanXxx" → 已实现，跳过
  - "audit 入口统一" → 看 `LengbanlistCommand` 里每个子命令是否已记录 audit，没的就补
  - "subcommand 拆分" → 这是 refactor-fabric 重构后的状态，main 反向合并此拆分需要逆向
- 建议在本地单独比对每个文件的 diff，跳过已经在 refactor-fabric 上等价的改动
- `BanManager` 对比 `git show 12c90ef -- BanManager.java` 的 diff，看 silent/broadcast 是否一致
- `DatabaseManager` 改动较多，对照 diff 逐段 review
- `IpMatcher` 加了 IPv6 私有/保留段拦截（与 `1bc74f4` 重复，见下）

---

## 提交 `1bc74f4` —— 修复举报转封禁事务守卫冲突 + IPv6 恢复

### 核心改动
1. 删除 `LengbanlistCommand.handle` 子命令的命令层状态前置检查
2. `DatabaseManager.upsertBan/upsertIpBan` 返回 `WriteResult`，`StorageMigrationManager` 失败时告警
3. `IpMatcher` 恢复 IPv6 私有/保留地址（fc/fd ULA、::1）的封禁拦截
4. 删除无调用方的 `deactivateBan/deactivateIpBan/deactivateActiveEntry`（死代码清理）

### refactor-fabric 现状
- `DatabaseManager` 已有 `WriteResult` 枚举（看 `BanManager.mapWriteResult` 引用） ✓
- `IpMatcher` 是否包含 IPv6 私有/保留段拦截需要核对——重点！

### 搬运指引
- `common/.../manager/BanManager.java`：确认 `tryBanIp` 的 `isPrivateOrReservedIp` 检查是否覆盖 IPv6
- `common/.../utils/IpMatcher.java`：补 IPv6 段（`fc00::/7`、`fe80::/10`、`::1`）
- `common/.../manager/DatabaseManager.java`：核对 `WriteResult` 字段、`upsertBan` 返回值是否与 main 一致
- `common/.../manager/StorageMigrationManager.java`：失败告警分支
- `bukkit/.../commands/LengbanlistCommand.java`：`handle` 子命令的状态前置删除

---

## 提交 `6dae4b0` —— 修复安全审查发现的13个问题

### 核心改动
1. 举报重复处理导致重复封禁（恢复已处理状态守卫）
2. Web 面板补全免疫检查（unban/unmute/IP 封禁绕过）
3. 自动更新校验官方 SHA-256，拒绝被篡改的 JAR
4. 修复 CIDR 禁言解除静默失败
5. 审计日志写入失败不再静默丢失
6. runSync 超时取消排队任务
7. reload 失败时记录严重日志
8. 明文 HTTP 启动时输出安全提醒
9. 举报 ID 改用完整 UUID
10. rollback 私网 IP 恢复封禁时明确报错
11. 拒绝 `0.0.0.0/0` 匹配所有 IP（防全员误封）
12. Web 面板线程池改缓存线程池 + 增大 backlog
13. CORS 按来源白名单发放，新增 token 注销吊销机制

### refactor-fabric 现状
- 自动更新 SHA-256 校验：上一轮我已经加上了（`AutoUpdateManager.java` 已经有 `GitHubUpdateChecker.getLatestSha256(platform)` + `validatePluginJar`）
- 明文 HTTP 启动提醒：`WebServer.start()` 第 105 行已有 ✓
- Web 面板线程池 `Executors.newCachedThreadPool()` ✓（line 74）
- JWT token 注销：`AuthManager.revokedTokens` ✓（line 144）
- CORS 按来源白名单发放：**核对是否实现**
- `runSync` 超时取消：**核对是否实现**（要看 `LengbanlistPlatform.runSyncCancellable` 是否带超时）
- 免疫检查：`WebServer` 已经在这次对话里补上了 `plugin.canPunish` / `canPunishTarget` ✓
- 举报 UUID、`0.0.0.0/0` 拒绝：**核对**

### 搬运指引
- `common/.../web/WebServer.java`：核对照 main 的 diff（line 7-12, 73-105, 144 等），把还没补的 CORS 白名单、unban/unmute 免疫检查、rollback 私网 IP 报错等补齐
- `common/.../utils/IpMatcher.java`：增加 `0.0.0.0/0` 拒绝
- `common/.../platform/LengbanlistPlatform.java` + `bukkit/.../Lengbanlist.java`：核对 `runSyncCancellable` 是否带超时
- `bukkit/.../Lengbanlist.java`：reload 失败严重日志
- `common/.../manager/AuditManager.java`：写入失败告警
- `common/.../manager/MuteManager.java`：CIDR 禁言解除静默失败修复
- `common/.../manager/ReportManager.java`：举报 ID 完整 UUID（`UUID.randomUUID().toString()` 已经用，但要看 `setId` 是否完整 36 位）

---

## 提交 `177a1be` —— 自动更新校验误拒官方JAR

### 核心改动
1. `AutoUpdateManager.validatePluginJar` 改为校验 `plugin.yml` 的 `main` 字段（不校验 manifest 的 `Main-Class`，因为 Bukkit jar 永远不写）
2. 仅当 manifest 显式声明冲突的 `Main-Class` 时才拒绝
3. 校验失败时清理 `.temp` 临时文件
4. `getPluginBaseName` 增加文件名无版本号时的防崩溃守卫
5. 新增单元测试 `AutoUpdateManagerDownloadTest`

### refactor-fabric 现状
- `validatePluginJar` 已经基于 `plugin.yml` 校验（line 237-260） ✓（且我的改动保留了 manifest 冲突兜底）
- 临时文件清理：line 132 已经 `tempFile.delete()` ✓
- 单元测试：**没有**，需要新增
- `getPluginBaseName`：line 46-55 已存在，但要被 `generateNewFileName` 替代

### 搬运指引
- 主要逻辑 refactor-fabric 已经具备（但请把 main 上对应的单元测试搬运过来）
- 新增 `bukkit/.../utils/AutoUpdateManagerDownloadTest.java`（main 上是单测，需要看是否在 refactor-fabric 的 common test 还是 bukkit test 目录）

---

## 提交 `14b8b66` —— 审计日志执行人统一为 CONSOLE

### 核心改动
1. 新增 `Utils.getSenderName`：控制台操作统一记为 `CONSOLE`（替代 Bukkit 的 `Console`）
2. 全部命令的 audit / 封禁 / 禁言 / 警告记录的 staff 统一走该规范化
3. Web 面板所有操作统一走 `resolveActor`：默认 admin 记为 `CONSOLE`，自定义用户名保留
4. 回滚操作执行人改为发起 `/lban rollback` 的管理员，不再固定为 `rollback`

### refactor-fabric 现状
- `Utils.getSenderName`：bukkit 包的 `Utils` 是否有这个方法需要核对（之前 grep 看到它被引用，但当前看到 refactor-fabric 的多处直接用 `sender.getName()`）

### 搬运指引
- `bukkit/.../utils/Utils.java`：新增 `getSenderName(CommandSender)`，控制台返回 `"CONSOLE"`，玩家返回 `player.getName()`
- 替换以下文件的 `sender.getName()` 为 `Utils.getSenderName(sender)`：
  - `BanCommand` / `BanIpCommand` / `UnbanCommand` / `SetBanCommand` / `MuteCommand` / `UnmuteCommand` / `KickCommand` / `WarnCommand` / `UnwarnCommand` / `WarnMsgCommand` / `LengbanlistCommand` / `RollbackCommand`
- `common/.../web/WebServer.java`：新增 `resolveActor(token)`；所有 audit 调用改为 `auditManager.log("xxx", resolveActor(token), ...)`
- `common/.../manager/RollbackManager.java`：构造函数加 `operator` 参数，audit 时使用真实操作者

---

## 提交 `1a2b77e` —— V1.9.9 release 标记

### 核心改动
仅 `pom.xml` 把版本号从 `1.9.8` 改为 `1.9.9`。

### refactor-fabric 现状
已经标 `1.9.9`（root pom `line 9` / `line 25`），无需操作。

---

## 建议的搬运顺序

1. **`f4d381d` 与 `12c90ef` 合并搬运**——这两个都是 `tryBanXxx` 体系，先把 `LengbanlistCommand.java` 在 refactor-fabric 现有结构上对齐 silent/escalation/audit 行为
2. **`1bc74f4` 与 `6dae4b0` 中 "IpMatcher 与 CIDR 防御" 部分**——核对 `IpMatcher` 的 IPv6 / `0.0.0.0/0` 拦截
3. **`6dae4b0` 中 CORS / runSync / reload / audit 失败日志部分**——`WebServer` + `LengbanlistPlatform` + `AuditManager` 改动
4. **`6dae4b0` 中免疫检查 + 自动更新 SHA-256 部分**——大部分已就位，剩余 SHA-256 校验逻辑统一与 CORS 同步
5. **`177a1be` 单元测试搬运**——验证逻辑但不改主代码
6. **`14b8b66` `Utils.getSenderName` 改造**——跨多文件，影响大，最后做

## 风险点

1. **`LengbanlistCommand.java` 大文件**——refactor-fabric 上这个文件 837 行（看 12c90ef 的 diff 大小），平台化后结构与 main 差异大，逐行 cherry-pick 必然 100% 冲突。**必须手工搬运**：取 main 上每个子命令的"增量行为"（silent 路径、escalation 消息、tryXxx 结果处理），在 refactor-fabric 的现有代码里找对应位置补
2. **Fabric 模块同步**——每个 main 提交搬运后，还要在 `fabric/.../FabricCommandBridge.java` 与 `FabricServerFeatures.java` 找等价位置补。Fabric 端通过 `MessageSink` + 反射调用 common 模块，所以搬运到 common 后 fabric 端只要跟着调用即可
3. **数据库迁移**——`DatabaseManager.upsertBan` 返回 `WriteResult` 涉及存储格式，迁移时要确保新旧版本兼容

## 验证步骤

每搬运完一个提交：

```bash
cd "Lengbanlist - main"
mvn clean package -DskipTests
```

最后整体验证：

```bash
mvn clean package          # 含测试
mvn install
# 用真实服务端启动 bukkit + fabric 各一次，确认命令注册、SHA-256 校验、CORS 等生效
```
