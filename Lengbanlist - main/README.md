# Lengbanlist 开发说明

Lengbanlist 是一个面向高版本 Bukkit / Paper / Folia 服务端的轻量处罚与管理插件。当前代码以 `org.leng.Lengbanlist` 为插件入口，在生命周期中初始化配置、数据库、业务管理器、事件监听器、命令、Web 管理面板、统计和更新检查。

## 项目结构

```text
src/main/java/org/leng/
├── Lengbanlist.java                 # 插件主类，负责生命周期、配置加载、命令注册、监听器注册和任务启动
├── BroadCastBanCountMessage.java    # 自动广播当前封禁统计
├── Metrics.java                     # bStats 统计上报
├── commands/                        # Bukkit 命令实现
├── listeners/                       # 玩家、聊天、GUI、OP 入服等事件监听
├── manager/                         # 封禁、禁言、警告、举报、数据库、模型等业务管理器
├── models/                          # 不同角色模型的提示文本实现
├── object/                          # 封禁、禁言、警告、举报等数据对象
├── utils/                           # 时间、调度器、更新检查、IP 保存等工具类
└── web/                             # Web 管理面板 HTTP API
```

资源文件位于 `src/main/resources/`：

- `plugin.yml`：命令、权限、插件版本占位符和 Folia 支持声明。
- `config.yml`：功能开关、数据库、Web、广播、更新检查等主配置。
- `broadcast.yml`：自动广播文本配置。
- `chatconfig.yml`：聊天过滤和审核相关配置。
- `eula.yml`：插件启用前的 EULA 确认文件。

## 启动流程

1. `Lengbanlist.onLoad()` 初始化 `SchedulerUtils`，检查 EULA，加载默认配置。
2. 数据库通过 `DatabaseManager` 初始化，旧版 YAML 数据由 `StorageMigrationManager` 迁移。
3. 创建 `BanManager`、`MuteManager`、`WarnManager`、`ReportManager`、`IpAssociationManager` 和 `WebServer`。
4. `Lengbanlist.onEnable()` 注册事件监听器和命令执行器。
5. 根据配置启动自动广播、Web 管理面板、历史封禁清理、更新检查和 bStats。
6. `onDisable()` 取消任务、停止 Web 服务、保存广播配置并关闭数据库连接。

## commands 包职责

`commands` 包内每个类都负责一个 Bukkit 命令或一组主命令子命令。命令类通常只做权限检查、参数解析、功能开关检查和用户反馈，核心业务交给 `manager` 包处理。

| 类 | 命令 | 实现内容 |
| --- | --- | --- |
| `LengbanlistCommand` | `/lban` | 主命令入口，处理广播开关、手动广播、封禁列表、配置重载、封禁/解封、帮助、GUI、IP 查询、模型切换、LBAC、Web 面板配置、赞助入口等子命令。 |
| `BanCommand` | `/ban` | 封禁玩家，解析时长与原因，调用 `BanManager.banPlayer()`。 |
| `BanIpCommand` | `/ban-ip` | 封禁 IP，解析时长与原因，调用 `BanManager.banIp()`。 |
| `SetBanCommand` | `/setban` | 修改玩家或 IP 的封禁时间与原因，支持永久和自动时长。 |
| `UnbanCommand` | `/unban` | 根据参数判断玩家名或 IP，并执行对应解封。 |
| `WarnCommand` | `/warn` | 给玩家添加警告，触发警告管理器的自动处罚逻辑。 |
| `UnwarnCommand` | `/unwarn` | 移除玩家指定警告或全部有效警告，并按需解除自动封禁。 |
| `WarnMsgCommand` | `/warnmsg` | 管理员对违规聊天进行警告处理。 |
| `AllowMsgCommand` | `/allowmsg` | 放行被聊天审核拦截的玩家消息。 |
| `MuteCommand` | `/mute` | 禁言玩家，写入禁言记录。 |
| `UnmuteCommand` | `/unmute` | 解除玩家禁言。 |
| `ListMuteCommand` | `/listmute` | 输出当前禁言列表。 |
| `CheckCommand` | `/check` | 查询玩家或 IP 的处罚状态、历史、关联信息和赞助提示。 |
| `HistoryCommand` | `/history` | 查询玩家处罚历史记录，提供补全。 |
| `ReportCommand` | `/report` | 玩家提交举报。 |
| `AdminReportCommand` | `/admin` | 管理员查看、处理和关闭举报。 |
| `KickCommand` | `/kick` | 踢出在线玩家并发送原因。 |
| `InfoCommand` | `/info` | 输出插件版本、服务端核心、内存、CPU、在线人数和更新状态。 |
| `GetIPCommand` | `/getip` | 查询玩家 IP 和地理位置。 |
| `StaffChatCommand` | `/sc` | 管理员工作频道聊天。 |

## listeners 包职责

- `PlayerJoinListener`：玩家入服时检查封禁、IP 封禁、IP 关联、VPN 检测和待处理举报提示。
- `ChatListener`：处理禁言、聊天过滤、聊天审核和管理员放行流程。
- `OpJoinListener`：OP 入服后异步检查更新，并提示待处理举报。
- `ChestUIListener`：处理封禁列表等箱子 GUI 点击事件。
- `AnvilGUIListener`：处理铁砧输入界面中的封禁、解封和原因输入。
- `ModelChoiceListener`：处理模型选择 GUI 和当前模型切换。

## manager 包职责

- `DatabaseManager`：统一管理 SQLite / MySQL 连接、建表、读写、历史记录和过期数据维护。
- `BanManager`：封禁、IP 封禁、解封、封禁查询和入服拦截判断。
- `MuteManager`：禁言、解禁和禁言状态查询。
- `WarnManager`：警告记录、撤销警告、LBAC 自动封禁与自动解除。
- `ReportManager`：举报创建、查询、处理和待处理数量统计。
- `IpAssociationManager`：记录玩家历史 IP、查询同 IP 玩家、检测代理/VPN。
- `ModelManager`：注册角色模型、读取配置中的当前模型并提供切换能力。
- `StorageMigrationManager`：把旧版 YAML 数据迁移到数据库存储。

## object 和 models

`object` 包是数据层对象，主要承载数据库与业务逻辑之间传递的数据：

- `BanEntry` / `BanIpEntry`：玩家封禁和 IP 封禁记录。
- `MuteEntry`：禁言记录。
- `WarnEntry`：警告记录。
- `ReportEntry`：举报记录。

`models` 包实现不同角色模型的提示语。新增模型时需要实现 `Model` 接口，并在 `ModelManager` 中注册。

## utils 包职责

- `AutoUpdateManager`：下载 GitHub Release 中的新版本 jar，替换本地插件文件并提示重启加载。
- `GitHubUpdateChecker`：集中维护 GitHub Release 链接、获取最新版本、比较版本号、生成下载文件名。
- `SchedulerUtils`：封装 Bukkit / Folia 的同步、异步和循环任务调度。
- `TimeUtils`：解析处罚时长、计算结束时间、格式化剩余时间。
- `SaveIP`：保存和读取玩家 IP。
- `Utils`：统一消息发送等通用辅助方法。

## Web 管理面板

`web/WebServer.java` 使用 JDK `HttpServer` 实现 REST API，并通过 JWT 做管理端鉴权。Web 功能受 `features.web` 和 `web.enabled` 同时控制。涉及请求处理、认证、限流、JSON 响应和管理接口时，应优先在该类内保持现有结构，不要把 Bukkit 主线程敏感操作直接放进 HTTP 处理线程。

## 开发事项

- 新增功能时先在 `config.yml` 的 `features.*` 中提供开关，并在命令或监听器入口调用 `plugin.isFeatureEnabled()`。
- 命令类只负责入口逻辑，持久化和核心规则应放到对应 `manager`。
- 数据库字段变更需要同时考虑 SQLite 和 MySQL，优先在 `DatabaseManager` 中集中处理。
- Bukkit API 主线程敏感操作要通过 `SchedulerUtils.runTask()` 回到主线程；耗时网络和数据库查询优先异步执行。
- 更新链接、下载文件名和版本比较统一放在 `GitHubUpdateChecker`，不要在命令或监听器里硬编码 GitHub 地址。
- 自动更新只负责安装新 jar 并提示重启，不做运行时热重载。
- 新命令需要同时更新 `plugin.yml` 的 commands 和 permissions，并在 `Lengbanlist.onEnable()` 注册执行器。
- 新增角色模型需要实现 `Model`，注册到 `ModelManager`，并确认模型切换 GUI 能显示。
- 修改处罚逻辑后至少验证封禁、解封、警告、禁言和历史查询的主路径。

## 构建

```bash
mvn clean package
```

构建产物位于 `target/Lengbanlist - <version>.jar`。当前版本号由 `pom.xml` 控制，并会写入 `plugin.yml` 的 `${project.version}`。
