# 如何成为贡献者

欢迎来到本项目！无论是修 bug、加功能还是改文档，都欢迎~

- [开始之前](#开始之前)
- [开发环境搭建](#开发环境搭建)
- [提交规范](#提交规范)
- [工程结构](#工程结构)
- [资源文件](#资源文件)
- [扩展系统](#扩展系统)
- [启动流程](#启动流程)
- [命令注册](#命令注册)
- [各包职责](#各包职责)
- [Web 管理面板](#web-管理面板)
- [开发事项](#开发事项)
- [构建与验证](#构建与验证)
- [验证数据库后端](#验证数据库后端)

## 开始之前

- **Java 17**：本项目需要 Java 17 或更高版本；
- **Maven**：构建工具，用于管理项目依赖和构建；
- **Git**：版本控制工具。

想给 Lengbanlist 写扩展（而不是改核心）的话，直接看
[`lengbanlist-api/README.md`](../lengbanlist-api/README.md)：扩展契约、完整示例、方法参考都在那儿。

## 开发环境搭建

1. **克隆仓库**：

   ```bash
   git clone https://github.com/Serendisand/Lengbanlist.git
   cd Lengbanlist
   ```

2. **构建一次**，确认本地环境没问题：

   ```bash
   mvn clean install
   ```

   产物在 `Lengbanlist - main/target/Lengbanlist-<version>.jar`。

3. 把 jar 丢进测试服的 `plugins/`，重启即可。

## 提交规范

commit message 只写一行，不超过 50 字符，格式是「前缀: 一句话」，例如：

```
fix: 修复玩家退出时任务未取消
refactor: 拆分配置加载逻辑
```

前缀取下面这些词之一——发布时按它归类：

| 类别 | 可用前缀 |
| --- | --- |
| 新功能 | `feat` `add` `feature` `新增` `添加` `新功能` |
| 问题修复 | `fix` `hotfix` `bug` `修复` `修正` |
| 优化改进 | `improve` `refactor` `perf` `优化` `重构` `改进` `改善` |
| 其他 | `docs` `style` `chore` `test`，以及其它没匹配上的前缀 |

**一条 commit 只做一件事**：几个互不相干的改动就拆成几条提交，别塞进同一条。

## 工程结构

多模块 Maven 工程，父工程 `Lengbanlist-Parent`：

```text
Lengbanlist/
├── lengbanlist-api/          # 契约模块：扩展接口、不可变数据对象、文案模型接口
├── Lengbanlist - main/       # 主插件（artifactId: Lengbanlist）
└── docs/
```

- `lengbanlist-api` **零第三方依赖**（只有 spigot-api，`provided`）。扩展也以 `provided`
  依赖它编译，运行时用核心 classloader 里那份——扩展把 `org/leng/api`、`org/leng/object`
  打进自己的 jar 会加载出第二份接口副本，对核心传回的对象做类型检查时报
  `ClassCastException`，而且极难定位。
- 主插件的 spigot-api 固定按支持区间**下限**（1.17.1）编译，单个 jar 覆盖 1.17.1 ~ 26.3+。
  写代码时不能随手用高于 1.17.1 的 API，除非做了反射兜底。

主插件包结构（`Lengbanlist - main/src/main/java/org/leng/`）：

```text
├── Lengbanlist.java              # 主类：生命周期、配置加载、命令与监听器注册、任务启动
├── command/                      # 命令实现；CommandRegistry 是从扩展注册表派生的命令表
├── extension/                    # 扩展注册表、统一门控、内置提供者声明
│   └── lua/                      # Lua 子系统：沙箱、加载器、命令桥、事件桥、门面
├── gui/                          # 箱子 GUI 会话与聊天向导
├── integration/                  # 角色模型、主题、更新检查、IP 归属、Webhook、PlaceholderAPI
├── listener/                     # 玩家、聊天、GUI、OP 入服等事件监听
├── net/                          # 出站网络统一入口（下载服务、镜像链、HTTP 传输）
├── service/                      # 业务管理器：封禁、禁言、警告、举报、申诉、审计、同步……
├── storage/                      # 数据库连接与方言、缓存、身份解析、schema 迁移
├── util/                         # 时间、调度器（Folia 安全）、文本、IP 匹配、bStats
└── web/                          # Web 管理面板 HTTP API
```

数据对象（`BanEntry`、`MuteEntry`、`WarnEntry`、`ReportEntry`、`AppealEntry` …）与文案模型
接口 `Model` 在 `lengbanlist-api` 的 `org.leng.object` / `org.leng.models` 下：它们属于契约，
核心与扩展共用同一份类型。

## 资源文件

`Lengbanlist - main/src/main/resources/`：

| 文件 | 内容 |
| --- | --- |
| `plugin.yml` | `/lban` 主命令、权限节点、版本占位符、Folia 支持声明。独立命令（`/ban`、`/mute` 等）**不在此声明**，由 `CommandRegistry` 按功能开关在运行时注册——功能关掉时命令名还给别的插件 |
| `config.yml` | 功能开关、广播、更新检查、下载镜像等主配置 |
| `storage.yml` | 数据库连接、保留策略、多子服的 `server-name` |
| `broadcast.yml` | 自动广播文案 |
| `chatconfig.yml` | 聊天过滤与审核配置 |
| `eula.yml` | 启用前的 EULA 确认，内容与说明见[使用限制与责任声明](Mustn't_zh.md) |
| `models/` | 内置角色模型：`_base.yml`（基准与自定义模板）、`default.yml`、`english.yml` |
| `web/` | Web 面板的静态页面与资源 |
| `extensions/` | 随 jar 打包的官方 Lua 扩展（`tp.lua`、`staffchat.lua` 及各自的配置目录），仅在数据目录 `extensions/` **整个不存在**时释放一次 |

## 扩展系统

低频功能不再写进核心，而是做成扩展。**内置提供者、Java 扩展、Lua 脚本三种来源走同一条注册
路径**（`ExtensionRegistry`），因此命令表、帮助过滤、补全、权限与门控全都自动一致
（`/lban ext list` 会同时列出 Lua 与 Java 扩展，`enable/disable` 两边都支持）。

| 形态 | 入口 | 开关来源 |
| --- | --- | --- |
| 内置提供者 | `BuiltinExtensions.declarations()` | 核心 `config.yml` 的 `features.<键>` |
| Java 扩展 | 独立 Bukkit 插件调 `CoreService.enable()` | 扩展自己的 `plugins/<扩展名>/config.yml`（基类已实现 `SelfGatedExtension`） |
| Lua 扩展 | 数据目录 `extensions/<id>.lua` | 扩展自己的 `extensions/<id>/config.yml` |

门控只有一条语义：`ExtensionRegistry.isFeatureActive(键)` = **归属提供者已启用 且 开关为 true**。
核心代码判断"某功能现在能不能用"必须问它，而不是 `plugin.isFeatureEnabled()`——后者只回答
配置里的开关，会把未安装的扩展当成已启用。

命令由核心按这个语义自动门控；监听器和调度任务不会——扩展停用后 Bukkit 插件本身仍处于启用
状态，已注册的监听器还会触发。所以扩展要在事件与任务入口自己问一句
`context.isFeatureActive()`。

相关类：

- `ExtensionRegistry`：注册表、统一门控、按扩展隔离存放的钩子
- `CoreService`：注册进 Bukkit `ServicesManager`，是外部扩展触达核心的唯一入口
- `ExtensionContextImpl`：每个扩展一份上下文（配置、日志前缀、调度器、命令、钩子、门面）
- `BuiltinExtensions`：内置提供者与**唯一的命令声明表**
- `extension/lua/`：Lua 子系统——沙箱（白名单库 + 指令预算与墙钟双超时）、加载器、命令桥、事件桥、门面

## 启动流程

1. `onLoad()`：初始化 `SchedulerUtils`，检查 EULA，加载默认配置。
2. `onEnable()`：建 `ExtensionRegistry` 并装入内置提供者（命令与纯功能键），把
   `CoreService` 注册进 `ServicesManager`，再由 `CommandRegistry` 从注册表派生命令表。
   Java 扩展作为独立插件自行启用，通过 `CoreService.enable()` 把命令与钩子交进来。
3. `LuaLoader.loadAll()` 扫数据目录，加载 Lua 扩展。
4. `DatabaseManager` 初始化数据库，旧版 YAML 数据由 `StorageMigrationManager` 迁移。
5. 创建业务管理器（`BanManager`、`MuteManager`、`WarnManager`、`ReportManager`、
   `IpAssociationManager` 等）与 `WebServer`。
6. 注册监听器；按配置启动自动广播、Web 面板、历史清理、到期提醒、更新检查与 bStats。
7. `onDisable()`：注销扩展与命令、取消任务、停 Web 服务、保存广播配置、关闭数据库。

## 命令注册

命令表只有**一处真相**：`BuiltinExtensions.declarations()`。每条声明只写一次功能键，由
`BuiltinProvider` 注入到该功能的每条命令上；命令注册与 `/lban help` 过滤都从它派生，
不会出现两处漂移。

新增一条内置命令要动的地方：

1. 对应 `XxxCommands` 里的嵌套类（一个类一条命令）；
2. `BuiltinExtensions.declarations()` 加一行：功能键 / 命令名 / 权限 / 用法 / 描述 / 执行器工厂；
3. `plugin.yml` 的 `permissions:` 段加权限节点；
4. 需要出现在 `/lban help` 里的，按模型文件的编排手工补一行。

独立命令不写进 `plugin.yml` 的 `commands:`——`CommandRegistry` 在运行时按 `features.*`
注册与注销，功能关掉时连命令名都不占用。`CommandRegistryTest` 会把声明表与 `plugin.yml`
权限、`config.yml` 开关逐条对照，写错或忘写测试就红。

## 各包职责

### command

| 文件 | 内含命令 | 实现内容 |
| --- | --- | --- |
| `CommandRegistry` | — | 从扩展注册表派生命令表：按 `features.*` 在运行时注册/注销独立命令，维护权限、用法与 `/lban help` 的功能键映射，把扩展子命令挂进 `/lban` 的分派表 |
| `FeatureCommand` | — | 独立命令的外壳：统一做开关兜底、权限校验与 Tab 补全转发 |
| `LengbanlistCommand` | `/lban` | 主命令入口：广播开关、封禁列表、配置重载、封禁/解封、帮助、GUI、模型切换、审计、Web 面板、赞助入口等子命令 |
| `BanCommands` | `/ban` `/ban-ip` `/setban` `/unban` `/kick` | 封禁一条线：解析时长与理由、区分玩家名与 IP、改期、解封、踢出 |
| `MuteCommands` | `/mute` `/unmute` `/listmute` `/allowmsg` | 禁言与消息管控 |
| `WarnCommands` | `/warn` `/unwarn` `/warnmsg` | 警告体系：写入/撤销警告，触发自动处罚与自动解封 |
| `FreezeCommands` | `/lban freeze` `/lban unfreeze` | 冻结与解冻 |
| `QueryCommands` | `/check` `/history` `/getip` `/info` | 处罚状态与历史、IP 归属地、插件信息 |
| `ReportCommands` | `/report` `/admin` | 举报流程：提交、查看、处理、关闭 |
| `StaffCommands` | `/lban vanish` `/lban rollback` | 隐身与按时间批量回滚（`/sc` 已搬进 Lua 扩展） |
| `GuiCommands` | `/alts` `/lban open` | 箱子 GUI：管理菜单、模型选择、同 IP 小号查询 |
| `ModelsCommand` | `/lban models` `/lban model` | 云端角色模型：列出、按需下载安装、切换 |
| `LuaExtCommand` | `/lban ext list\|reload\|enable\|disable` | Lua 扩展管理 |

命令类只做权限检查、参数解析、开关兜底与用户反馈，核心业务交给 `service/`。

### listener

- `PlayerJoinListener`：入服检查封禁、IP 封禁、IP 关联、VPN 检测与待处理举报提示
- `ChatListener`：禁言、聊天过滤、聊天审核与管理员放行
- `OpJoinListener`：OP 入服后异步检查更新，并提示待处理举报
- `ModelChoiceListener`：模型选择 GUI 与当前模型切换
- `FreezeListener`：冻结期间拦截移动、破坏、交互、丢弃、命令与传送
- `MuteCommandBlockListener`：禁言期间的指令屏蔽
- `VanishListener`：隐身可见性维护
- `GuiCleanupListener`：GUI 会话清理

### service

- `BanManager` / `MuteManager` / `WarnManager` / `ReportManager`：处罚闭环的四个管理器
- `AppealManager`：封禁申诉
- `AuditManager` / `RollbackManager`：审计日志（含哈希链）与按操作人、时间回滚
- `SyncManager`：多子服数据同步
- `IpAssociationManager` / `PlayerIpService`：历史 IP、同 IP 小号、代理与 VPN 判定
- 权重免疫与累犯升级已搬进 `punishpolicy` 扩展（分别实现
  `PunishmentDecisionHook` / `DurationPolicyHook`），核心只保留钩子缺席时的兜底
- `VanishManager` / `FreezeManager` / `BroadcastManager` / `ExpiryReminderTask` /
  `MuteCommandBlockPolicy` / `BanMutationFeedback`

### storage

- `DatabaseManager`：SQLite / MySQL / MariaDB / PostgreSQL 的连接、建表与读写
- `DatabaseDialect` / `SchemaMigrations`：方言差异与 schema 迁移
- `BanCache` / `WarnCache`：处罚缓存
- `PlayerIdentityResolver`：玩家名与 UUID 解析
- `StorageMigrationManager`：旧版 YAML 数据 → 数据库

### integration

- `ModelManager` / `CustomModel` / `ModelCloudManager`：内置与自定义角色模型、云端模型仓库
- `ThemeManager`：界面主题
- `AutoUpdateManager` / `GitHubUpdateChecker`：更新检查与安装
- `IpGeoLookup`：IP 归属地
- `WebhookNotifier`：Webhook 推送
- `PlaceholderAPIHook`：PlaceholderAPI 变量

### gui / net / util / web

- `gui/`：`GuiSessionManager`（箱子菜单会话）、`WizardManager`（聊天向导）
- `net/`：`DownloadService`、`MirrorChain`、`HttpTransport`——所有出站下载都走这里
- `util/`：`SchedulerUtils`（Folia 安全调度）、`TimeUtils`、`Utils`、`IpMatcher`、
  `PlayerProfileHelper`、`ConsoleText`、`ErrorLog`、`Metrics`
- `web/`：`WebServer` 与各 `*Controller`，见下

## Web 管理面板

`web/WebServer.java` 用 JDK 的 `HttpServer` 起 REST API，JWT 鉴权加限流，静态页面在
`resources/web/`。功能受 `features.web` 与 `web.enabled` 双重控制。请求处理跑在 HTTP 线程上，
**不要**在里面直接碰主线程敏感操作，需要时用 `SchedulerUtils` 回到主线程。

## 开发事项

- 新功能先在 `config.yml` 的 `features.*` 里给开关，入口判断走
  `getExtensionRegistry().isFeatureActive()`，不是 `isFeatureEnabled()`。
- 命令类只做入口逻辑，持久化与核心规则放 `service/`。
- 数据库字段变更要同时考虑四种方言，优先在 `DatabaseManager` 里集中处理。
- 主线程敏感操作经 `SchedulerUtils.runTask()` 回到主线程；网络与数据库查询优先异步。
- 更新链接、下载文件名与版本比较统一放 `GitHubUpdateChecker`，不要在命令或监听器里硬编码。
- 自动更新只装新 jar 并提示重启，不做运行时热重载。
- 新增内置角色模型要实现 `Model`、注册到 `ModelManager`，并确认模型切换 GUI 能显示；
  玩家自定义模型走数据目录 `models/`。
- 改完处罚逻辑，至少把封禁、解封、警告、禁言、历史查询的主路径走一遍。

## 构建与验证

```bash
mvn clean install                          # 全量：契约模块 + 主插件
mvn -pl "Lengbanlist - main" -am package   # 只打主插件
mvn test                                   # 跑测试
```

`CommandRegistryTest` 校验命令声明表与 `plugin.yml`、`config.yml` 的一致性；
`ExtensionRegistryTest`、`LuaExtensionLoadTest` 覆盖扩展注册与 Lua 加载。

## 验证数据库后端

默认测试全部跑在临时 SQLite 上，不依赖任何外部服务：

```bash
mvn test
```

MariaDB / PostgreSQL 的分支（方言 SQL、布尔列读写、审计链行锁）需要真实数据库才跑得到。
指向一个**一次性测试库**并设置环境变量后，`DatabaseManagerNetworkTest` 会自动接管：

```bash
export LENGBANLIST_TEST_MARIADB_URL="jdbc:mariadb://127.0.0.1:3306/lengbanlist_test"
export LENGBANLIST_TEST_MARIADB_USER="root"
export LENGBANLIST_TEST_MARIADB_PASSWORD="your-password"
export LENGBANLIST_TEST_POSTGRESQL_URL="jdbc:postgresql://127.0.0.1:5432/lengbanlist_test"
export LENGBANLIST_TEST_POSTGRESQL_USER="postgres"
export LENGBANLIST_TEST_POSTGRESQL_PASSWORD="your-password"
mvn test -Dtest=DatabaseManagerNetworkTest
```

未设置变量的方言会被自动跳过（而不是失败）。测试会建表、写入以 `Lbtest` 开头的少量数据，
覆盖封禁/IP封禁/禁言/警告/举报/IP历史/审计链，最后清理掉自己建的封禁行。
