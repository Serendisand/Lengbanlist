# Lengbanlist 插件生态改造规划

> 目标版本：2.2.0（机制）/ 3.0.0（拆分完成）
> 依据：`docs/feature-audit.html` 的功能审计（24,336 行，44 个功能，8 项开关缺陷）
> 状态：**规划待批准**，尚未动工

---

## 1. 目标

把当前的单体插件拆成 **薄核心 + 可插拔扩展 + 分发市场** 三层：

| 层 | 内容 | 规模 |
| --- | --- | --- |
| `Lengbanlist-API` | 纯契约 jar：接口、不可变数据对象、事件 | 约 1.5k 行 |
| `Lengbanlist`（核心） | 处罚闭环：封禁/IP封禁/解封/改期/警告/禁言/踢出/查询/审计/到期/缓存/数据库 | 约 7.5k 行 |
| `Lengbanlist-Ext-*` | 其余 30+ 功能，每个一个独立 Bukkit 插件 | 约 15k 行 |
| 市场 | `index.json` + 安装器 + sha256 校验 + GitHub 分发 | 新增 |

**用户可见结果**

- 服务器只装需要的功能，主插件体积与内存占用大幅下降
- 第三方可以按文档写出扩展，无需改动核心
- `/lban ext install <id>` 一条命令装扩展，带完整性校验
- **升级无感**：现有 `config.yml` 的 39 个 `features.*` 开关继续有效

**明确不做（v1）**

- 付费扩展、授权校验、DRM
- 第三方自助发布平台（上传/审核/举报/下架）
- 运行时热加载扩展 jar（技术上不可行，见 3.2）
- 扩展沙箱隔离（技术上不可行，见 3.2）

---

## 2. 当前现状（已验证事实）

### 2.1 工程基础

- 仓库 24,336 行：Java 20,851 行 / 103 文件 + Web 前端 3,485 行
- 单模块 Maven：`Lengbanlist - main/pom.xml`，`org.leng:Lengbanlist:2.1.3`，Java 17，Spigot API 1.17.1（provided）
- shade 插件把 SQLite / MySQL / MariaDB / PostgreSQL 四个 JDBC 驱动全部打进主 jar
- **回归安全网（已实测确认）**：12 个测试类 / **284 个测试方法**，在 JDK 17 上全绿——`Tests run: 284, Failures: 0, Errors: 0, Skipped: 2`，`BUILD SUCCESS`，耗时 1 分 54 秒
- ⚠️ **必须用 JDK 17 构建**：`maven.compiler.source/target=17`，但本机默认 `java` 是 **JDK 26**，会让 Mockito 5.14.2 / Byte Buddy 无法 mock 而报 **164 个错误**（不是代码问题）。构建前需设 `JAVA_HOME=C:\Program Files\Java\jdk-17`（本机另有 `jdk-21.0.10` 可用）

### 2.2 已存在的拆分障碍（审计实证）

| 障碍 | 证据 |
| --- | --- |
| **开关只关命令，不关实现** | `onLoad`/`onEnable` 无条件构造 18 个 manager + 注册 9 个监听器；`CommandRegistry` 只控制命令注册 |
| **`org.leng.api` 不是 API** | `LengbanlistAPI` 直接返回内部类型（`BanEntry`、`BanManager.BanMutationResult`）并持有插件实例；缺 freeze/vanish/audit/identity/config/scheduler 等接口 |
| **横切逻辑内联** | `warn` 的 LBAC 自动封禁挂在 `features.ban` 上；`mute` 拦截在 `ChatListener` 中先于 `chat-filter` 判断 |
| **表被核心垄断** | 14 张表由 `DatabaseManager` 无条件创建，含 `freezes` / `reports` / `appeals` / `sync_events` 等扩展表 |
| **门控散落各处** | `Utils.canUse(..., feature)`、`CustomModel.filterDisabledFeatures`、`CommandRegistry.HELP_FEATURES` 三处各自读 `features.*` |
| **既存缺陷会被放大** | 审计发现的 8 项开关缺陷（含 `unban-ip` 键不存在恒为 true、Web 侧 5 个端点未接门控）在拆分后若照搬会变成"扩展装了但关不掉" |

### 2.3 可直接复用的资产

**`ModelCloudManager` 已经实现了市场需要的全部机制**：远端 `index.json`、多镜像回退（`raw.githubusercontent` / `gh-proxy` / `mirror.ghproxy`）、TTL 缓存、**`ModelInfo.sha256` 字段**、安装统计上报。市场不是从零造，而是把同一套机制换个索引 schema。

**`CommandRegistry` 已经是扩展点原型**：`Spec(feature, name, permission, usage, description, executor)` + 运行时注册/注销 + `HELP_FEATURES` 帮助过滤。扩展注册只需把硬编码列表换成注册表。

**`org.leng.api.events` 已有 8 个事件**（Ban / BanIp / Unban / Mute / Unmute / Warn / Unwarn / Report），是事件总线的好种子。

---

## 3. 可行性

### 3.1 结论：可行

最关键的技术问题——**独立插件 jar 能否使用核心的 API 类**——已从官方文档确认：

- Paper 的 `ClassLoaderAccess` 明确说明：类加载访问按**直接与传递依赖**授权，"只有在传入的 classloader 属于该插件的直接或传递依赖时才返回 true"；并且"**legacy spigot 插件可以访问服务器上任何可用的 classloader**，而现代 Paper 插件被正确地限制在依赖树内"。
- `PluginClassLoaderGroup` 同样是"持有某个插件声明的全部直接与传递依赖"的 classloader 组。

因此扩展声明 `depend: [Lengbanlist]` 后即可使用核心暴露的 API 类，**在 Spigot 与 Paper 上都成立**。这正是 PlaceholderAPI 扩展、MythicMobs 扩展等生态的运作方式。

来源：[ClassLoaderAccess](https://jd.papermc.io/paper/26.1.2/io/papermc/paper/plugin/provider/classloader/ClassLoaderAccess.html) · [PluginClassLoaderGroup](https://jd.papermc.io/paper/1.21.7/io/papermc/paper/plugin/provider/classloader/PluginClassLoaderGroup.html)

### 3.2 硬约束（必须在设计里让步，不能承诺）

| 约束 | 后果 |
| --- | --- |
| **Bukkit 类加载器无法真正卸载插件** | 安装/更新扩展必须**重启服务器**才能生效。不承诺热插拔，UI 文案要诚实 |
| **隔离很弱** | legacy 插件可访问任意 classloader，扩展拥有完整服务器权限。市场只能校验**完整性**（sha256）与**来源**（HTTPS），不能做沙箱。安全模型必须靠审核与信任，而非技术隔离 |
| **Spigot 上扩展无法从传递依赖加载库** | 这是已知的 Spigot 缺陷（[SPIGOT-6502](https://hub.spigotmc.org/jira/si/jira.issueviews:issue-html/SPIGOT-6502/SPIGOT-6502.html)）。**API jar 必须零第三方依赖**，扩展若需库必须自行 shade + relocate |
| **Folia 兼容** | 核心的 `SchedulerUtils` 已抽象 Folia/Bukkit 调度。API 必须暴露它；扩展**不得**直接调用 `Bukkit.getScheduler()`，否则 Folia 上直接崩 |

### 3.3 主要风险

1. **横切功能是最大技术风险（高）**：`immunity`（能否处罚）、`escalation`（时长计算）、`models`（文案渲染）不是独立功能，而是插入核心决策流程的逻辑。若没有先建立 Hook 机制就拆，必然造成逻辑割裂——审计已经发现 `EscalationManager.resolveMute` 恒走 legacy 分支、`warn` 半截逻辑这类既存问题。
   **缓解**：Phase 0 先在核心内建立 Hook 链并把内联逻辑改为经 Hook 调用，功能**仍留在核心**，用测试证明行为零变化，再搬迁。

2. **版本地狱（中）**：扩展与核心的 API 版本不匹配会产生难懂的 `NoSuchMethodError`。
   **缓解**：`requiredApi` 用 semver range，启用时校验并给出可读原因；市场索引里冗余记录 `minCore`。

3. **"关不掉"缺陷被放大（中）**：现有 8 项开关缺陷若照搬进扩展体系，会变成"扩展已安装但开关无效"。
   **缓解**：Phase 0 建立**统一门控入口**，所有可见性判断只走注册表；把审计发现的 8 项缺陷作为 Phase 0 的修复项一并处理。

4. **拆分期长（中）**：15k 行代码搬迁，中途必须保持可发布。
   **缓解**：按耦合度分 3 批，每批结束都能出一个可发布版本。

---

## 4. 实现设计

### 4.1 制品划分

```
lengbanlist-parent                 (pom, packaging=pom)
├── lengbanlist-api                纯契约，零第三方依赖（仅 spigot-api provided）
├── lengbanlist-core               主插件，实现 API，shade JDBC
└── extensions/
    ├── ext-vanish, ext-staffchat, ext-tp, ext-broadcast        第一批：无表无横切
    ├── ext-freeze, ext-report, ext-appeal, ext-chestui,
    │   ext-chatfilter, ext-alts, ext-ipassoc, ext-vpn,
    │   ext-getip, ext-placeholderapi                            第二批：有表但自洽
    └── ext-models, ext-sync, ext-webpanel, ext-immunity,
        ext-escalation, ext-rollback, ext-export, ext-auditchain,
        ext-expiryreminder, ext-offlinewarn, ext-theme           第三批：横切/基础设施
```

### 4.2 三种扩展形态（关键设计洞察）

审计表明"其余功能"**不是同质集合**。API 必须分别支撑三类，否则拆分一定失败：

| 形态 | 代表 | 需要的扩展点 | 数量 |
| --- | --- | --- | --- |
| **功能型** | vanish, freeze, chest-ui, report, appeal, alts | 命令注册 + 事件 + 自有表 + 独立监听器 | 多数 |
| **横切型** | immunity, escalation, models(文案) | **Hook 链**：插入核心的处罚决策与消息渲染 | 3 |
| **基础设施型** | sync, audit-chain, web-panel, rollback | **回调**：缓存失效、审计写入、页面注册 | 4 |

### 4.3 API 契约

```java
// 扩展入口：核心通过 Bukkit 插件发现 + instanceof + ServicesManager 探知
public interface LengbanlistExtension {
    String id();              // "freeze"，与 config.yml 的 features.freeze 对应
    String name();
    String version();
    String requiredApi();     // semver range，如 "[2.0,3.0)"
    void onEnable(ExtensionContext ctx) throws Exception;
    void onDisable();
}

public interface ExtensionContext {
    CommandRegistrar commands();   // 取代 CommandRegistry 的硬编码 specs()
    EventBus         events();     // 订阅核心处罚/审计/缓存事件
    HookRegistry     hooks();      // 注册横切 hook
    DataStore        data();       // 受管连接池 + 扩展自有表迁移
    Scheduler        scheduler();  // Folia 安全，替代 Bukkit.getScheduler()
    Messages         messages();   // 文案渲染（models 扩展可替换实现）
    ExtensionConfig  config();     // 扩展私有配置文件
    CacheInvalidator caches();     // sync 扩展用
    AuditSink        audit();      // 写审计日志
    Services         services();   // ban/warn/mute/query/identity 服务
    Logger           logger();
}
```

**API jar 的设计原则**

- 只含接口 + 不可变数据对象（`BanEntry` / `BanIpEntry` / `MuteEntry` / `WarnEntry` / `ReportEntry` / `AppealEntry` / `FreezeEntry` / `AuditEntry` / `PlayerIdentity` / `SyncEvent` 从 `org.leng.object` 迁入）
- **零第三方依赖**（受 SPIGOT-6502 约束）；JSON 若需要，暴露核心的 Gson 实例而不是让扩展自带
- 旧 `LengbanlistAPI` 门面保留为 `@Deprecated` 委托，已集成用户不崩

### 4.4 横切 Hook 设计

核心在关键决策点暴露**有序 hook 链**，默认实现保持当前行为：

| Hook | 注册方 | 作用 |
| --- | --- | --- |
| `PunishmentDecisionHook` | immunity, escalation | 能否处罚 / 时长如何计算 |
| `MessageRenderHook` | models | 文案替换 |
| `CommandVisibilityHook` | 全部 | 替代 `Utils.canUse` + `HELP_FEATURES` 的分散判断 |
| `PunishmentMutationListener` | sync, audit-chain, webhook | 处罚变更后的后置动作 |

**Phase 0 的核心动作**：先把这 4 个 Hook 建好，把核心内的内联逻辑改为经 Hook 调用，功能**仍留在核心 jar 内**。此时行为必须零变化，由现有 12 个测试 + 新增 Hook 顺序测试证明。之后搬迁才是纯机械动作。

### 4.5 向后兼容

- **保留** `config.yml` 的 `features.*` 段，每个键映射到扩展 id
- **语义修正**：`features.X` 生效条件 = *扩展已安装* **且** *开关为 true*。未安装视为关闭并提示一次——这同时修掉了"缺失键默认 true"的误导（审计缺陷 7）
- **首次升级**：把用户现有 `features.*` 选择迁移到 `extensions.yml`，并把官方扩展包整包释放到 `plugins/`，保证功能不丢
- 扩展缺失时：核心启动正常，相关命令消失，日志给出一次可读提示，**不产生异常栈**

### 4.6 市场设计

**索引 schema**（与现有 `ModelIndex` 同构，复用镜像链与 TTL 缓存）

```json
{
  "schemaVersion": 1,
  "updated": "2026-08-20T00:00:00Z",
  "extensions": [{
    "id": "freeze",
    "name": "冻结玩家",
    "author": "Serendisand",
    "version": "1.2.0",
    "requiredApi": "[2.0,3.0)",
    "minCore": "2.0.0",
    "url": "https://github.com/Serendisand/Lengbanlist-Ext-Freeze/releases/download/v1.2.0/freeze-1.2.0.jar",
    "sha256": "…",
    "size": 123456,
    "description": "…",
    "tags": ["punishment"],
    "dependencies": [{ "id": "models", "requiredApi": "[1.0,2.0)" }]
  }]
}
```

**安装流程**

1. 拉取 `index.json`（镜像链回退，TTL 缓存）
2. 解析依赖，校验 `requiredApi` / `minCore` 与当前核心匹配
3. 下载到临时文件 → **校验 sha256** → 原子移动到 `plugins/`
4. 提示重启生效（诚实说明无法热加载）

**命令**：`/lban ext list | search | info | install | update | remove | check`
**Web**：面板扩展提供页面（核心不含 HTTP 服务）

**完整性边界**：sha256 + HTTPS 保证"产物没被篡改"，**不保证"扩展是安全的"**。安全依赖审核与社区信任，这一点必须写进开发者文档，不能让用户误以为市场是沙箱。

### 4.7 文件与目录布局（硬约束）

**扩展 jar 必须放在 `plugins/` 顶层，不能放进 `plugins/Lengbanlist/` 子文件夹。**

原因（已核实）：

1. Bukkit 判断哪些文件是插件靠的是 **文件名过滤器**——`PluginLoader.getPluginFileFilters()` 返回的是匹配文件名后缀的 `Pattern`（如 `\.jar$`），作用于 `plugins/` 的**目录列表**，**不做递归**。子目录名不以 `.jar` 结尾，也不会被进入，因此永远不被扫描。
2. 想让子文件夹里的 jar 生效，只能由核心自己调用 `PluginManager.loadPlugin(File)`。而这个 API 在 Paper 上已标注 **`@Deprecated(forRemoval = true)`**——把生态地基压在"将来会被移除"的 API 上不可接受。
3. 更关键的是，这条路等于退回你已否决的"自建模块加载器"方案，会丢掉 `depend: [Lengbanlist]` 这条**已验证可用**的类可见性通路。

来源：[JavaPluginLoader javadoc](https://jd.papermc.io/paper/1.21.7/org/bukkit/plugin/java/JavaPluginLoader.html)

**推荐的目录结构**

```
plugins/
├── Lengbanlist-2.2.0.jar                  ← 核心（内含 API 类）
├── Lengbanlist-Ext-Freeze-1.2.0.jar       ← 官方扩展
├── Lengbanlist-Ext-Vanish-1.0.3.jar
├── Lengbanlist-Ext-WebPanel-3.0.1.jar
├── someones-custom-ext-1.0.0.jar          ← 第三方扩展
├── Lengbanlist/                           ← 只有核心的配置与数据
│   ├── config.yml  storage.yml  extensions.yml  eula.yml
│   └── lengbanlist.db
├── Lengbanlist-Ext-Freeze/                ← 扩展自己的配置（Bukkit 自动创建）
└── Lengbanlist-Ext-WebPanel/
```

**用命名约定解决"plugins 目录变乱"，而不是换目录**

- 统一前缀 `Lengbanlist-Ext-<Id>-<version>.jar`，在 `plugins/` 里天然聚成一排、可排序、可 grep
- 插件显示名同样带前缀，`/plugins` 输出里一眼可辨归属
- 玩家基本不手动碰文件：`/lban ext` 与 Web 页负责装卸
- 每个扩展的配置仍在 `plugins/<扩展名>/`（Bukkit 标准），核心目录保持干净

**API jar 的分发方式**

`lengbanlist-api` **不需要放进 `plugins/`**，它是**纯编译期产物**：

- 扩展以 `provided` 作用域依赖它，**绝不打包进扩展 jar**
- 运行时由核心的 classloader 提供（扩展 `plugin.yml` 声明 `depend: [Lengbanlist]` 即建立该通路）
- 扩展的 `plugin.yml` 必需项：

```yaml
name: Lengbanlist-Ext-Freeze
version: 1.2.0
main: org.leng.ext.freeze.FreezeExtension
api-version: 1.17
depend: [Lengbanlist]        # 这一行是类可见性的来源
```

- **必须由 CI 强制检查**：扩展 jar 内不得出现 `org/leng/api/**`。若误以 `compile` 作用域打包，扩展会拿到自己的一份接口副本，运行时抛 `ClassCastException`（接口类型不一致），且报错极难定位。

> 备选（仅在特别在意 `plugins/` 整洁时考虑）：把**官方**扩展合并成单个 `Lengbanlist-Official.jar`（内部多模块，按 `extensions.yml` 启用），第三方扩展仍各自独立。代价是官方扩展无法单独升级或删除，与"可自定义生态"的目标相冲突，故不推荐。

### 4.8 核心边界（已确认"处罚闭环核心"）

| 留在核心 | 行数 | 外置 | 行数 |
| --- | --- | --- | --- |
| 封禁/解封/改期/IP段 | 1,556 | Web 面板 | 5,818 |
| 数据库层 | 2,509 | 角色模型/云端 | 1,830 |
| 到期清理任务 | 80 | 箱子 GUI | 925 |
| 命令注册与门控 | 347 | bStats（保留核心） | 727 |
| 查询 check/history | 571 | 冻结 | 601 |
| 主命令 /lban | 759 | 申诉 | 502 |
| 缓存 | 428 | 举报 | 360 |
| 警告 | 736 | 回滚 | 312 |
| 禁言 | 642 | 跨服同步 | 287 |
| 审计 | 239 | 广播 | 186 |
| 踢出/重载/到期提醒/离线警告 | 182 | 隐身 | 179 |
| | | 其它（聊天过滤/小号/IP关联/VPN/权限钩子/升级/导出/哈希链/主题/传送/工作频道/getip） | 约 1,300 |
| **小计** | **约 8,050** | **小计** | **约 13,030** |
| **共享基础设施** | 主类 677 · 调度抽象 205 · 工具类（Utils/ConsoleText/ErrorLog/HttpHelper/TimeUtils 等）约 560 · 进服与 OP 监听器 241 · API 事件 149 · 会话/清理等杂项约 1,390 | **约 3,260** |

> **口径说明**：上表按"主要归属"归并，`LengbanlistCommand`(759)、`StaffCommands`(263)、`GuiCommands`(607) 等被子功能共享的文件只计一次，因此两列不等于按文件精确切割的结果。三部分相加 ≈ 24,336 行，与审计口径一致。
>
> 另：bStats 留在核心（约 727 行，只上报核心本身）；**建议删除"一言"彩蛋**（每次启动无条件外呼 `v1.hitokoto.cn`，零功能价值，见审计风险项）。

### 4.9 统一下载层（Phase 0 第 0.10 项）

**现状：仓库里已经存在两套互不相干的镜像逻辑，市场将是第三套。**

| 现有实现 | 配置键 | 形态 | 失败记忆 | 校验 |
| --- | --- | --- | --- | --- |
| `ModelCloudManager` | `models-cloud.mirrors` + `repo`/`branch` | 完整 URL 列表；留空则默认三链（raw.githubusercontent → gh-proxy → mirror.ghproxy） | 有：`primaryUnreachable` 在单次任务内跳过错的主源，下次任务重试 | `ModelInfo.sha256` 字段已存在 |
| `GitHubUpdateChecker` | `update-check.mirrors` | 带 `type`（github / github-proxy / jsdelivr / gitee）的完整 URL，另有代理改写逻辑 | 逐条尝试，无记忆 | 无 |
| 市场（待建） | — | — | — | 计划 sha256 |

**目标**：一套镜像链 + 一个下载服务，models / update / extensions 三者共用。

核心新增 `org.leng.download` 包，只抽两件事：

**1. `MirrorChain`** —— 把"一个逻辑资源"映射成"一串候选 URL"，按序尝试并在单次任务内记住不可达的源。

- 输入：逻辑资源（`releases/latest`、`<repo>/<branch>/index.json`、某个 asset 名）
- 输出：候选 URL 列表；由共用镜像链生成，可按用途覆盖
- 失败判定复用现有的 `isUnreachableFailure` 语义：无状态码的 `IOException` 或 HTTP ≥ 500 视为不可达；HTTP 404 属于"资源不存在"，**不应**把镜像标记为坏掉；`InterruptedException` 立即中断
- 强制 HTTPS，仅额外放行 localhost 的 http（沿用现有 `isAllowedUrl`）

**2. `DownloadService`** —— 在 `MirrorChain` 之上提供三种操作，全部共用同一个 `HttpHelper`、同一组超时与 User-Agent：

- `fetchText(...)`：拉 JSON/文本（models 索引、市场索引、更新检查），带 TTL 内存缓存 + 磁盘缓存回写
- `downloadToFile(...)`：流式下载到临时文件 → **校验 sha256** → 原子移动到目标（扩展 jar、模型文件、自动更新 jar）
- `downloadBytes(...)`：小文件直读

**配置收敛为一个 `download:` 段**（旧键保留兼容）：

```yaml
download:
  connect-timeout: 8000
  read-timeout: 10000
  user-agent: "Lengbanlist"
  ssl-verify: true          # 仅证书劫持环境才设 false
  mirrors:                  # 共用镜像链，所有用途默认走这里
    - name: gh-proxy
      type: github-proxy
    - name: jsDelivr
      type: jsdelivr
    - name: GitHub直连
      type: github
  overrides: {}             # 按用途覆盖，键：models / extensions / update
```

**迁移**：`models-cloud.mirrors` / `update-check.mirrors` 存在时优先读取，写入 `download.overrides.<用途>` 并打印一次迁移日志（与 `StorageMigrationManager` 的既有做法一致）。

**验收**

- `ModelCloudManager` 与 `GitHubUpdateChecker` 中镜像相关代码被删除，行为由 `DownloadService` 复现
- 现有 `ModelCloudSourceFallbackTest`（验证镜像回退）保持通过
- 新增 `MirrorChainTest`（候选生成、主源失败跳过、≥500 与 404 区别对待）与 `DownloadChecksumTest`（**sha256 不匹配必须拒绝落盘**）
- 镜像全挂时给出可读报错，而非静默失败

---

## 5. 变更清单

### Phase 0 — 机制先行（行为不变，可被测试证明）

| # | 变更 | 目的 | 状态 |
| --- | --- | --- | --- |
| 0.1 | 仓库根新增父工程 `pom.xml`；`Lengbanlist - main` 成为核心模块；CI 与发布工作流改为从根构建 | 制品分离 | ✅ 已完成 `c855442` |
| 0.2a | 新建 `lengbanlist-api`：迁入 `org.leng.object.*`（10 个数据对象）与 `org.leng.api.events.*`（8 个事件），**包名不变** | 编译期契约 | ✅ 已完成 `c855442` |
| 0.2b | 在 `lengbanlist-api` 新增 `LengbanlistExtension` / `ExtensionContext` / 服务接口 / Hook 接口 | 扩展契约 | 待做 |
| 0.3 | 核心新增 `ExtensionRegistry`，取代 `CommandRegistry.specs()` 的硬编码列表 | 扩展注册 | 待做 |
| 0.4 | 核心新增 `ExtensionContext` 实现 + `DataStore` + `Scheduler`/`Messages`/`Config` 门面 | 扩展运行时 | 待做 |
| 0.5 | 核心 18 个 manager 改为**按需构造**（依赖注册表而非无条件 `new`） | 薄核心 | 待做 |
| 0.6 | 建立 4 条 Hook 链，把 immunity/escalation/models 的内联逻辑改为经 Hook 调用（功能仍留核心） | 解横切耦合 | 待做 |
| 0.7 | `Utils.canUse` / `CustomModel.filterDisabledFeatures` / `CommandRegistry.HELP_FEATURES` 统一走注册表 | 消除三处分散门控 | 待做 |
| 0.8 | 修复审计发现的 8 项开关缺陷（含 `unban-ip` 键、Web 侧未接门控） | 避免缺陷被继承 | 待做 |
| 0.9 | `features.*` → `extensions.yml` 迁移 + 语义改为"已安装 且 开启" | 兼容与正确性 | 待做 |
| 0.10 | **统一下载层**：把 `models-cloud.mirrors` 与 `update-check.mirrors` 合并为一套镜像链 + 一个下载服务（见 4.9） | 统一下载，市场复用 | 待做 |

**Phase 0 完成时功能与 2.1.3 完全一致，只是内部可插拔。**

### Phase 1 — 拆低耦合扩展

`ext-vanish`(179) · `ext-staffchat`(90) · `ext-tp`(40) · `ext-broadcast`(186)
无表、无横切、无跨模块依赖，用来验证注册表/事件/命令注册三条通路。

### Phase 2 — 拆有表但自洽的扩展

`ext-freeze`(601) · `ext-chestui`(925) · `ext-report`(360) · `ext-appeal`(502) · `ext-chatfilter`(131) · `ext-alts`(120) · `ext-ipassoc`(159) · `ext-vpn`(80) · `ext-getip`(51) · `ext-placeholderapi`(206)
引入 `DataStore` 与扩展自有表迁移；`freezes`/`reports`/`appeals` 表所有权移交扩展（含一次性数据迁移）。

### Phase 3 — 拆横切与基础设施扩展

`ext-models`(1,830) · `ext-webpanel`(5,818) · `ext-sync`(287) · `ext-immunity`(143) · `ext-escalation`(70) · `ext-rollback`(312) · `ext-export`(115) · `ext-auditchain`(40) · `ext-expiryreminder`(77) · `ext-offlinewarn`(25) · `ext-theme`(373)
验证 Hook 链与 `CacheInvalidator`；`sync` 是核心表的最大消费者，放最后。

### Phase 4 — 市场

索引 schema + 生成流水线（CI 从各扩展仓库汇总）· 安装器（下载/sha256/原子落盘/版本约束）· `/lban ext` 命令组 · 版本不匹配的可读报错 · 镜像链复用

### Phase 5 — 生态运营

扩展模板仓库 + Maven archetype · 开发者文档（API 用法、Folia 注意、依赖禁忌、发布流程）· CI 校验（API 版本兼容性、plugin.yml 规范、sha256 生成）· 收录审核流程

---

## 6. 验证

### 自动化

| 测试 | 覆盖 |
| --- | --- |
| 现有 284 个测试（12 个测试类） | Phase 0 的**回归基线**，必须全绿且行为零变化 |
| `ExtensionRegistryTest` | 已安装/未安装 × 开关开/关 四种组合的可见性 |
| `VersionConstraintTest` | semver range 解析与边界（`[2.0,3.0)`、`2.x`、不满足） |
| `MarketIndexTest` | 索引解析、sha256 校验失败、缺字段、镜像回退 |
| `HookOrderTest` | Hook 链顺序与默认行为等价 |
| `LegacyConfigMigrationTest` | 39 个旧开关迁移后语义不变 |

### 手工

- Spigot / Paper / Folia 三端各跑一次完整流程
- 旧配置升级：`features.*` 选择不丢，功能不丢
- 单独删除任一扩展 jar：服务器正常启动、命令消失、无异常栈
- 断网安装扩展：镜像链回退与最终可读报错

### 失败路径（必须显式处理）

sha256 不匹配 · 下载中断 · 缺依赖扩展 · API 版本不满足 · 扩展 `onEnable` 抛异常（**核心必须只禁用该扩展，而非崩服**）· 磁盘不可写

---

## 7. 验收标准

1. 核心 jar 约 8,000 行，且不含任何外置功能的类
2. 全新安装"核心 + 官方扩展包"，功能与 2.1.3 等价
3. 旧 `config.yml` 的 39 个 `features.*` 开关升级后语义不变
4. 单独删除任一扩展 jar，服务器正常启动，无异常栈
5. `/lban ext install freeze` 能从 GitHub 安装并通过 sha256 校验
6. 装了 API 版本不匹配的扩展时，给出可读原因而非 `NoSuchMethodError`
7. 第三方按文档在 30 分钟内写出"自定义命令 + 订阅封禁事件"的扩展
8. Folia 上扩展通过 `ExtensionContext.scheduler()` 调度，无跨线程异常

---

## 8. 待决问题

| # | 问题 | 推荐默认 | 权衡 |
| --- | --- | --- | --- |
| 1 | 官方扩展包形态：多 jar 还是一个 bundle jar？ | **多 jar** | 多 jar 可单独删除/单独升级，契合"自定义生态"；bundle 打包简单但失去粒度 |
| 2 | 每个扩展各自上报 bStats？ | **各自用自己的 id** | 能追踪生态健康度；但需扩展作者配合，且增加遥测面 |
| 3 | `freezes`/`reports`/`appeals` 表所有权 | **移交扩展 + 一次性迁移脚本** | 核心不再持有扩展表，边界干净；代价是要写并测试数据迁移 |
| 4 | API 发布渠道 | **GitHub Packages** | 已有 GitHub Actions CI，改造成本最低；Maven Central 门槛高，JitPack 对多模块支持一般 |
| 5 | 是否保留 `LengbanlistAPI` 旧门面 | **保留为 `@Deprecated` 委托** | 不破坏已集成用户；代价是短期双份入口 |

---

## 9. 仓库与分发拓扑

**结论：官方内容不拆仓库；必须新建 1 个市场仓库，Phase 5 再建议加 1 个模板仓库。**

| 仓库 | 内容 | 是否新建 |
| --- | --- | --- |
| `Serendisand/Lengbanlist`（现有） | 核心 + `lengbanlist-api` + **全部官方扩展模块**（monorepo） | 否 |
| `Serendisand/Lengbanlist-Extensions` | 市场索引 `index.json` + 官方扩展 jar 的 Releases 托管 + 收录 PR 入口 | **是（必须）** |
| `Serendisand/Lengbanlist-Ext-Template` | 第三方扩展脚手架（GitHub Template Repository） | 建议（Phase 5） |
| 第三方作者自己的仓库 | 各自的扩展 | 与我们无关 |

**为什么不把官方扩展拆成 N 个仓库**

官方扩展与核心共享 `lengbanlist-api` 的版本契约。放在同一仓库才能保证：一次 CI 就能全量验证所有扩展对当前 API 编译通过；改 API 时可在同一个 PR 内同步修正所有受影响扩展。拆成 20 个仓库后，"改一次 API → 开 20 个 PR → 等 20 条 CI"会变成不可维护的负担，而这正是生态最容易死掉的地方。

**为什么市场索引要单独一个仓库**

- 与现有 `Serendisand/Lengbanlist-Models`（云端模型）完全同构，用户与贡献者心智一致
- 索引变更（收录新扩展）与核心代码变更解耦，第三方可通过 PR 自助登记
- 官方扩展 jar 按扩展独立 tag（如 `freeze-v1.2.0`）发布到该仓库 Releases，使各扩展版本互不牵连

**需要你协助的事项（我无法代做）**

1. **创建 `Serendisand/Lengbanlist-Extensions` 仓库** —— 我没有创建 GitHub 仓库的能力
2. **确认官方扩展 jar 的托管位置**：推荐放上述新仓库的 Releases（而非核心仓库的 Releases）
3. **索引若需跨仓库汇总**（CI 从各扩展仓库读取 release 信息生成 `index.json`），需要一个具备 `repo` 读权限的 token 作为 secret；若只在本仓库内操作，用默认 `GITHUB_TOKEN` 即可
4. **确认仓库名后**，我把默认索引地址写进配置（`extensions.index-url` 的默认值）

**在你创建仓库之前我能先做的**：把 `index.json` schema、`MirrorChain`/`DownloadService`、安装器与 `/lban ext` 全部按"索引地址可配置"实现，默认值留空并在未配置时给出明确提示。这样不会被仓库创建阻塞。

---

## 附录：本规划直接修掉的既存缺陷

审计发现的 8 项开关缺陷中，以下会在 Phase 0 顺带解决：

1. `features.sync` 关不掉后台轮询 → 统一门控后由注册表接管
2. `features.mute` 关不掉聊天拦截 → 拦截逻辑归核心，开关语义明确
3. `features.broadcast` 关不掉定时广播 → 广播成为扩展，扩展未装即关
4. `features.model` 关不掉 `/lban models` → 模型成为扩展
5. Web 侧 5 个端点未接门控 → 门控统一入口
6. `features.export` 关不掉 `audit verify` → 统一门控
7. **`unban-ip` 键不存在恒为 true** → 修复键名
8. `features.reload` 关不掉 `/lban reload` → 统一门控
9. 缺失键默认 true 的误导 → 语义改为"已安装 且 开启"
