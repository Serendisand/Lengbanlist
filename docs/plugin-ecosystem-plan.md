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
- 多模块 Maven：仓库根 `pom.xml` 为父工程（`org.leng:Lengbanlist-Parent`），下辖 `lengbanlist-api`（契约）与 `Lengbanlist`（核心）；版本 **2.1.6**；Java 17；Spigot API 1.17.1（provided）
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
仓库一：Serendisand/Lengbanlist（核心与契约，发布节奏独立）
lengbanlist-parent                     (pom, packaging=pom)
├── lengbanlist-api                    纯契约，零第三方依赖（仅 spigot-api provided）
└── Lengbanlist - main                 主插件（artifactId: Lengbanlist），实现契约，shade JDBC

仓库二：Serendisand/Lengbanlist-Extensions（18 个模块 + 市场索引，已创建）
lengbanlist-extensions-parent          (pom, packaging=pom)
├── vanish, staffchat, broadcast                          第一批：无表无横切
├── freeze, chestui, report, appeal, chatfilter,
│   altdetect, vpndetect, getip, placeholderapi           第二批：有表但自洽
├── models, sync, webpanel, audittools,
│   punishpolicy, punishnotify                            第三批：横切 / 基础设施
└── index.json                                            市场索引（CI 发布时回写 sha256）
```

> 模块目录名 = 扩展 id（`vanish/`、`audittools/`），不带 `ext-` 前缀；完整清单、规模与
> 保留的权限节点见 `docs/extension-catalog.md`。`/lban tp` 决定留在核心，不单独成模块。

### 4.2 三种扩展形态（关键设计洞察）

审计表明"其余功能"**不是同质集合**。API 必须分别支撑三类，否则拆分一定失败：

| 形态 | 代表 | 需要的扩展点 | 数量 |
| --- | --- | --- | --- |
| **功能型** | vanish, freeze, chest-ui, report, appeal, alts | 命令注册 + 事件 + 自有表 + 独立监听器 | 多数 |
| **横切型** | immunity, escalation, models(文案) | **Hook 链**：插入核心的处罚决策与消息渲染 | 3 |
| **基础设施型** | sync, audit-chain, web-panel, rollback | **回调**：缓存失效、审计写入、页面注册 | 4 |

### 4.3 API 契约

```java
// 已实现（Phase 0.2b / 0.3）。这里只列真正落地的方法，不写"计划中"的签名——
// 服务接口与 Hook 接口推迟到 0.4/0.6，与真正的消费方一起定义。
public interface LengbanlistExtension {
    String id();              // "vanish"：extensions.yml 的开关键 + 市场 index.json 的 id
    String name();            // 展示名，默认取 id
    String version();
    String requiredApi();     // semver 范围，如 "[2.0,3.0)"；空 = 不校验
    void onEnable(ExtensionContext context) throws Exception;
    default void onDisable() {}
}

// 核心注册进 ServicesManager，是外部扩展触达核心的唯一入口
public interface LengbanlistCore {
    String apiVersion();
    ExtensionContext enable(LengbanlistExtension extension) throws ExtensionLoadException;
    void disable(String extensionId);
    boolean isEnabled(String extensionId);
    Set<String> enabledExtensionIds();
}

public interface ExtensionContext {
    String extensionId();
    CommandRegistrar commands();   // 命令注册，随扩展启用/停用自动装卸
    ExtensionConfig  config();     // 私有配置：外部扩展落自己的插件目录，内置落核心 extensions/
    Scheduler        scheduler();  // Folia 安全，替代 Bukkit.getScheduler()
    Logger           logger();     // 自带 [扩展名] 前缀
    // 以下随消费方落地，不做超前设计：
    // EventBus(0.6) / HookRegistry(0.6) / DataStore(0.4) / Messages / CacheInvalidator / AuditSink / Services
}
```

**API jar 的设计原则**

- 只含接口 + 不可变数据对象（`BanEntry` / `BanIpEntry` / `MuteEntry` / `WarnEntry` / `ReportEntry` / `AppealEntry` / `FreezeEntry` / `AuditEntry` / `PlayerIdentity` / `SyncEvent` 从 `org.leng.object` 迁入）
- **零第三方依赖**（受 SPIGOT-6502 约束）；JSON 若需要，暴露核心的 Gson 实例而不是让扩展自带
- 旧 `LengbanlistAPI` 门面**直接删除**（用户决定）：它对外是"API"，对内从未被使用，全仓只有两处 `register`/`unregister` 调用，也没有任何测试覆盖。删除后核心不再持有 `org.leng.api` 包，**此前的 split package 问题一并消失**。这是对外破坏性变更，必须在发布说明与 README 中写明；它的替代品是 0.2b 定义的服务接口。

### 4.4 横切 Hook 设计

核心在关键决策点暴露 hook，**策略缺席时回落到默认行为**——这是让"扩展被删掉"永远等于"功能回到默认"的关键。

| Hook | 状态 | 注册方 | 缺席时的默认行为 |
| --- | --- | --- | --- |
| `PunishmentDecisionHook` | ✅ 已实现（0.6a） | immunity | **一律放行**（没装免疫 = 谁都能罚） |
| `DurationPolicyHook` | 0.6b | escalation | 用调用方请求的时长，不做升级 |
| 文案（models） | 0.6c，见下（改期到 0.4） | models | 回落到内置文案 |
| `PunishmentMutationListener` | Phase 3 | sync / audit-chain / webhook | 无后置动作 |

**已实现（0.6a）的形状**

- 钩子按**扩展**隔离存放（`HookRegistryImpl`）；核心按 **功能键 → 归属扩展 → 该扩展的钩子** 三级查找。扩展停用时只丢掉自己那一份，不存在"钩子还在但实现已失效"的中间态。
- `isFeatureActive` 与钩子查找是**两件事**：前者回答"装了且开着吗"，后者回答"策略是什么"；`PunishmentGate` 把两者合起来，并负责第三件事——缺席时怎么办。
- **一处必须保留的行为差异**：`canPunish` 按玩家权重、`canPunishTarget` 对含 `.` 的目标按 IP 权重。改造前就是两个方法，合并会改变线上行为；因此 IP 判定留在核心闸门里，钩子只提供两种权重。
- 只提供钩子、没有命令的功能（immunity 就是）通过 `LengbanlistExtension.features()` 声明功能键，否则 `isFeatureActive` 会把它当"未知功能"而只看向开关。

**0.6c 文案（models）为什么不能照搬 hook —— 改期到 0.4**

文案不是"一个决策点"，而是约 40 个调用点、`Model` 上的上百个**强类型方法**（`getImmunityDenied(target)`、`onEscalatedBan(...)`、`addWarn(target, reason)` …）。把它们改成通用的 `render(key, args)` 会同时丢掉类型安全并触及全部 40 个调用点，收益为零。

正确的形状是**服务**而不是每次调用的钩子：models 扩展提供一个 `Model` 实现，核心通过 `Messages` 门面取它，取不到时回落到内置文案（`ConsoleText` 已有这套兜底）。这属于 **0.4**（`Messages` 门面）的范畴，故在此说明并改期。

**Phase 0 的核心动作**：把横切逻辑改为经 Hook 调用，功能**仍留在核心 jar 内**。此时行为必须零变化，由既有测试 + 新增 Hook 测试证明。之后搬迁才是纯机械动作。

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
  mirrors:                  # 按用途分开；留空即使用该用途的内置默认链
    update:                 # 插件更新检查
      - name: gh-proxy
        type: github-proxy
        url: "https://gh-proxy.com/https://api.github.com/repos/Serendisand/Lengbanlist/releases/latest"
    models: []              # 云端模型索引
    extensions: []          # 扩展市场（Phase 4 使用）
```

> **为什么镜像链按用途分开，而不是一份共用列表**（实现时才确认的约束）：同一条逻辑资源在不同镜像类型下的**入口地址形态不同**——更新检查是 `api.github.com/repos/<repo>/releases/latest` 或 jsDelivr 的包地址，模型索引是 `raw.githubusercontent.com/<repo>/<branch>/index.json`。一份 URL 列表无法同时服务两个用途。真正可共用的只有**连接参数**（超时 / User-Agent / SSL）与**回退机制**，这两者已完全统一；`MirrorType` 的类型改写能力留待需要时再补。

**迁移**：`download.mirrors.<用途>` 优先；未配置时回退到既有的 `models-cloud.mirrors` / `update-check.mirrors`，再回退到各用途的内置默认链。既有配置文件无需改动即可继续工作。

**实现进度（已完成）**

- 新增 `org.leng.download`：`MirrorType` / `MirrorSpec` / `DownloadSettings` / `MirrorChain` / `DownloadService`
- `ModelCloudManager` 与 `GitHubUpdateChecker` 均已接入，两者自建的镜像逻辑已删除
- 新增 25 个测试（`MirrorChainTest` 12 / `DownloadServiceTest` 13），总计 284 → 309
- **顺带修掉一个真实缺陷**：`ModelInfo.sha256` 此前从索引解析出来后从未被使用，模型下载只校验内容含 `"name:"`；现在索引提供 sha256 即强制校验，不匹配拒绝安装

**尚未迁移：`AutoUpdateManager`（需先给 `DownloadService` 补三项能力）**

它的下载实现比当前 `DownloadService` **更严格**，直接迁移会降级安全性：

| 现有保护 | 当前 DownloadService | 迁移前提 |
| --- | --- | --- |
| 校验 JAR 文件头（zip magic），非 JAR 立即拒绝 | 无 | 增加**内容校验钩子**（首块 / 整体） |
| 下载体积上限 | 无 | 增加**体积上限** |
| 拿不到官方 sha256 时**拒绝安装**（fail-closed） | 未提供校验值即放行 | 增加**必须校验**模式 |

这三项能力**扩展安装器（Phase 4）同样必需**——安装第三方 jar 比更新自身更需要它们。因此先补 `DownloadService`，再迁 `AutoUpdateManager`，顺序不能颠倒。

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
| 0.2b | 契约模块新增 `LengbanlistExtension` / `LengbanlistCore` / `ExtensionContext` / `CommandSpec` / `CommandRegistrar` / `ExtensionConfig` / `Scheduler` / `Cancellable` / `ApiVersion`。**服务接口与 Hook 接口推迟到 0.4/0.6**，与真正的消费方一起定义，不做超前设计 | 扩展契约 | ✅ 已完成 |
| 0.3 | 核心新增 `ExtensionRegistry` + `CoreService`（注册进 ServicesManager）+ `ExtensionContextImpl`；命令表改为注册表驱动，内置功能经 `BuiltinExtensions` 注册为提供者 | 扩展注册 | ✅ 已完成 |
| 0.4 | 核心新增 `ExtensionContext` 实现 + `DataStore` + `Scheduler`/`Messages`/`Config` 门面 | 扩展运行时 | 待做 |
| 0.5 | 核心 18 个 manager 改为**按需构造**（依赖注册表而非无条件 `new`） | 薄核心 | 待做 |
| 0.6 | 建立 Hook 链，把横切逻辑改为经 Hook 调用（功能仍留核心） | 解横切耦合 | ✅ **0.6a**（免疫，19 个调用 → `PunishmentGate`）与 **0.6b**（时长，6 个调用 → `DurationPolicy`）完成；**0.6c 文案并入 0.4**（原因见 4.4） |
| 0.7 | `Utils.canUse` / `CustomModel.filterDisabledFeatures` / `CommandRegistry.HELP_FEATURES` 统一走注册表 | 消除三处分散门控 | ✅ 已完成：三处都改为经 `Lengbanlist.isFeatureActive`（新增的空安全包装，注册表未建立时退回只看开关）或注册表 |
| 0.8 | 修复审计发现的 8 项开关缺陷 | 避免缺陷被继承 | ✅ 完成 7 项；1 项判定为**非缺陷**：`StatsController` 是面板仪表盘，聚合封禁/禁言/警告等多来源数据，没有对应的单一功能键，而面板本身已由 `web.enabled` 把关 |
| 0.9 | `features.*` → `extensions.yml` 迁移 + 语义改为"已安装 且 开启" | 兼容与正确性 | ✅ 已完成：全部 39 个功能键登记为提供者；`extensions.yml` 首次启动自动迁移 `features.*` 的现有选择，读取时新文件优先、缺失键回退旧键；`/lban reload` 一并重载 |
| 0.11 | **文案外部化**：`ConsoleText` 里没有 yml 对应键的 9 项（`eula-required` / `eula-hint` / `db-init-failed` / `model-detected` / `model-detect-save-failed` / `preset-*` / `storage-*`）补进 `_base.yml` 的 `console:` 段；`ErrorLog` 等 11 处硬编码原因串改为可覆写文案 | 可维护性 | 待做（见第 10 节） |
| 0.9 | `features.*` → `extensions.yml` 迁移 + 语义改为"已安装 且 开启" | 兼容与正确性 | 待做 |
| 0.10 | **统一下载层**：新增 `org.leng.download`（`MirrorChain` + `DownloadService`），`ModelCloudManager` 与 `GitHubUpdateChecker` 已接入，`config.yml` 新增 `download:` 段（见 4.9） | 统一下载，市场复用 | ✅ 已完成（`AutoUpdateManager` 待 `DownloadService` 补齐校验钩子 / 体积上限 / 必须校验后再迁） |

**Phase 0 完成时功能与 2.1.6 完全一致，只是内部可插拔。**

> ⚠️ **唯一的例外是 0.8**：它的目的就是让开关真正生效，因此当某个功能被**关闭**时，行为**会**变化——那正是被修掉的缺陷。在默认配置（全部开关为 true）下，0.8 的行为与改造前逐条一致。
>
> 0.8 引入的行为变化清单（仅在该功能被关闭时触发）：
> - `features.sync: false` → 不再启动跨服轮询（此前照跑）
> - `features.mute: false` → 被禁言的玩家可以发言（此前仍被拦截）
> - `features.broadcast: false` → 不再定时广播，且 `/lban a`、`/lban toggle` 被拒绝（此前照跑）
> - `features.model: false` → `/lban models` 被拒绝（此前可执行）
> - `features.reload: false` → `/lban reload` 被拒绝（此前可执行）
> - `features.export: false` → `/lban audit verify` 被拒绝（此前可执行）
> - `features.audit / export / admin / history: false` → 对应的 Web 端点被拒绝（此前只校验登录）
> - IP 解封：Web 端改用真实存在的 `features.unban`（此前查的 `unban-ip` 键不存在，等于恒放行）

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
2. 全新安装"核心 + 官方扩展包"，功能与 2.1.6 等价
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
| 4 | API 发布渠道 | **JitPack**（原推荐 GitHub Packages，实现前改为 JitPack） | 消费 GitHub Packages 需要**每个消费者**配置带 `read:packages` 的 token，对"让第三方写扩展"是实质障碍；JitPack 零配置即可依赖公开仓库的 tag，官方与第三方一视同仁。代价是依赖 JitPack 服务、首次构建较慢。Maven Central 门槛过高 |
| 5 | ~~是否保留 `LengbanlistAPI` 旧门面~~ | **已定：直接删除** | 用户决定。它内部从未被使用、无测试覆盖；代价是对已有第三方集成是破坏性变更，需在发布说明中写明 |

---

## 9. 仓库与分发拓扑

**结论（已定）：核心与扩展分两个仓库；18 个官方扩展集中在 `Lengbanlist-Extensions` 做多模块；市场索引与该仓库同址。**

| 仓库 | 内容 | 状态 |
| --- | --- | --- |
| `Serendisand/Lengbanlist`（现有） | 核心 + `lengbanlist-api` 契约模块 | 已有 |
| `Serendisand/Lengbanlist-Extensions` | **18 个官方扩展的多模块工程** + 市场索引 `index.json` + 各扩展 jar 的 Releases | ✅ 已创建 |
| `Serendisand/Lengbanlist-Ext-Template` | 第三方扩展脚手架（GitHub Template Repository） | Phase 5 再建 |
| 第三方作者自己的仓库 | 各自的扩展 | 与我们无关 |

**为什么核心与扩展分仓库**：核心的发布节奏（安全修复、数据库兼容）不应被扩展拖累，反之亦然；核心仓库保持"只含处罚闭环 + 契约"的边界，任何人打开它都能立刻看懂这是什么。

**为什么官方扩展集中在一个仓库做多模块，而不是每个扩展一个仓库**（用户决定）

- 官方扩展与核心共享 `lengbanlist-api` 的版本契约：一次 CI 就能全量验证 18 个扩展对当前 API 编译通过；改 API 时可在同一个 PR 内同步修正全部受影响扩展。拆成 18 个仓库后，"改一次 API → 开 18 个 PR → 等 18 条 CI"会变成不可维护的负担
- 发布流水线、CI 模板、sha256 生成只需维护一份
- 代价：无法在仓库根展示各扩展的 bStats 徽章，徽章放进各模块子目录的 `README.md`（如 `vanish/README.md`）

**为什么市场索引与扩展代码同仓库**：CI 在发布某个扩展时，可在同一次运行里更新 `index.json` 并回写 sha256，索引与产物天然不会脱节。

**发布与索引机制**

- 每个模块独立版本（版本号写在各自 pom；父工程只做依赖与插件管理）
- 打 tag `<扩展id>-v<版本>`（如 `vanish-v1.0.0`）触发 CI：只构建该模块 → 上传 jar 到 Release → 计算 sha256 → 更新 `index.json` 并提交
- `index.json` 指向本仓库的 Release 资产，因此**不需要跨仓库 token**，默认 `GITHUB_TOKEN` 即可

**已确认**

- ✅ `Serendisand/Lengbanlist-Extensions` 已由用户创建
- `lengbanlist-api` 需发布到 GitHub Packages，供扩展仓库在 CI 中解析（见待决问题 4）

**剩余待你协助**

1. **`Serendisand/Lengbanlist-Ext-Template`** —— 需要，但属于 Phase 5（面向第三方作者），现在不急
2. **bStats 数字 ID** —— 你为 18 个扩展各建一个页面后把 ID 给我，我接进各扩展并加 README 徽章

---

## 10. 已知遗留：散落的硬编码文案

**实测：核心 Java 源码里有 1147 处中文串**，其中相当一部分是用户可见文案。分布（Top 12）：

| 文件 | 处数 | 文件 | 处数 |
| --- | --- | --- | --- |
| `QueryCommands` | 78 | `ReportCommands` | 48 |
| `LengbanlistCommand` | 77 | `ModelsCommand` | 47 |
| `GuiCommands` | 67 | `BuiltinExtensions` | 40 |
| `BanCommands` | 61 | `AutoUpdateManager` | 32 |
| `WebhookNotifier` | 49 | `MuteCommands` | 27 |
| `RollbackManager` | 48 | `DatabaseManager` | 22 |

### 10.1 两套并行文案系统

| 系统 | 位置 | 可被模型覆写 |
| --- | --- | --- |
| **模型文案** | `models/*.yml` 的 `messages:` / `console:`，含外部仓库 `Lengbanlist-Models` | 是 |
| **`ConsoleText` 枚举** | `org.leng.utils.ConsoleText`，中英双份写在枚举常量上 | 机制上可以（`console.<key>`），但出厂没给 yml 条目 |

`consoleText(key)` 的取值顺序是「模型优先，`ConsoleText` 兜底」：

```java
String template = model.getConsole(key);
if (template == null || template.isEmpty()) return ConsoleText.builtIn(key, placeholders);
```

`_base.yml` 的 `console:` 段只有 7 个键（`ready`/`loading`/`tip`/`placeholder-hook`/`auto-update`/`shutdown`/`farewell`），
而 `ConsoleText` 有 16 项。**其余 9 项没有 yml 对应键**，所以用户"在语言文件里找不到"：

`eula-required`、`eula-hint`、`db-init-failed`、`model-detected`、`model-detect-save-failed`、
`preset-base`、`preset-builtin`、`storage-migrated`、`storage-migrate-failed`

### 10.2 合并语义（决定了外部模型要不要逐个改）

`CustomModel.raw(path)` 是**逐键回退**，不是整段替换：

```java
String value = config.getString(path);
if ((value == null || value.isEmpty()) && base != null) value = base.getString(path);
```

因此**功能上外部 25 个模型不必逐个加新键**——没被覆写的键会自动沿用 `_base.yml`。
但外部仓库的 25 个模型目前都把 `console:` 段写满了 7 个键（当作自己的一亩三分地），
所以新增键若要维持**风格一致**，需要在 `Lengbanlist-Models/models/*/*.yml` 里各写一份
人设化的文案。这是**外部仓库的内容任务**，与代码改动分开进行。

### 10.3 处理路径

- **0.11**（小）：把上面 9 项补进 `_base.yml`，并把 `ErrorLog` 的 11 处硬编码原因串改为可覆写文案
- **0.4 / 0.6c**（大）：`Messages` 门面 + 把命令层的用户可见文案从 Java 字面量迁到模型键。
  这是 1147 处的主要来源，与 models 扩展的拆分是同一件事的两面
- **外部仓库**：模型文案按人设补齐（内容任务，非代码）

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
