# Lengbanlist API

写 **Lengbanlist 扩展**用的契约模块。扩展有两种形态，先选对路子：

| 形态 | 用什么写 | 放哪里 | 要编译吗 | 适合 |
| --- | --- | --- | --- | --- |
| **Lua 扩展** | Lua 5.2 脚本 | `plugins/Lengbanlist/extensions/<扩展名>.lua`（配置在 `<扩展名>/config.yml`） | **不用**，`/lban ext reload` 即可生效 | 加命令、定时任务、事件反应。绝大多数低频功能 |
| **Java 扩展** | Java 17 独立插件 | `plugins/` 下的一个 jar | 要，`mvn clean package` | 需要自己的监听器/复杂状态/直接读写核心表 |

本模块服务于后者。只想加个命令的话请走 Lua 路线——不用编译、不用发版、不用管 classloader；
现成的例子是核心自带的 `extensions/tp.lua`、`extensions/staffchat.lua`，更多见
[Lengbanlist-Extensions](https://github.com/Serendisand/Lengbanlist-Extensions)。

---

## 一、加依赖

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/serendisand/Lengbanlist</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>org.leng</groupId>
        <artifactId>lengbanlist-api</artifactId>
        <version>2.1.6</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

GitHub Packages 需要鉴权（`~/.m2/settings.xml` 里配 `github` 的账号与 token）。拿不到包时，把
Lengbanlist 仓库 clone 下来执行 `mvn install`，本地仓库里就有同样的坐标。

### 三条硬规矩

破了会在运行时炸，而且报错极难定位：

1. **`scope` 必须是 `provided`**，并且**绝不能**把 `org/leng/**` 打进自己的 jar。打进去会加载出
   第二份接口副本，核心传回的对象做类型检查时报 `ClassCastException`。
2. **`plugin.yml` 里必须写 `depend: [Lengbanlist]`**。这是扩展在运行时能拿到本模块与核心类的
   唯一来源（Spigot 上插件无法通过传递依赖加载库，SPIGOT-6502）。
3. 本模块**零第三方依赖**（只有 provided 的 spigot-api）。需要什么库自己 shade 进 jar，
   并 **relocate** 到自己的包名下，否则会和其他插件的同名库撞车。

---

## 二、完整的 pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>my-lengbanlist-extension</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>

    <properties>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>github</id>
            <url>https://maven.pkg.github.com/serendisand/Lengbanlist</url>
        </repository>
        <repository>
            <id>spigot-release-repo</id>
            <url>https://hub.spigotmc.org/nexus/content/repositories/releases/</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>org.spigotmc</groupId>
            <artifactId>spigot-api</artifactId>
            <version>1.17.1-R0.1-SNAPSHOT</version>
            <scope>provided</scope>
        </dependency>

        <!-- 契约模块：provided，绝不打进 jar（见"三条硬规矩"） -->
        <dependency>
            <groupId>org.leng</groupId>
            <artifactId>lengbanlist-api</artifactId>
            <version>2.1.6</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <finalName>${project.artifactId}-${project.version}</finalName>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.16.0</version>
                <configuration>
                    <source>17</source>
                    <target>17</target>
                </configuration>
            </plugin>

            <!-- 可选：只有需要带第三方库时才加。
                 注意 exclude 掉 org.leng.**，并给自带的库做 relocation。 -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.6.2</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>org/leng/**</exclude>
                                        <exclude>module-info.class</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                            <relocations>
                                <relocation>
                                    <pattern>com.example.lib</pattern>
                                    <shadedPattern>com.example.libs</shadedPattern>
                                </relocation>
                            </relocations>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

## 三、plugin.yml

```yaml
name: MyLengbanlistExtension      # 扩展 id 默认取这个名字（转小写、去掉非字母数字）
version: 1.0.0
main: com.example.myext.MyExtension
api-version: '1.17'
depend: [Lengbanlist]             # ← 少了这行，扩展拿不到核心
```

## 四、完整的扩展

```java
package com.example.myext;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtensionPlugin;
import org.leng.api.events.LengbanlistBanEvent;

/**
 * 欢迎扩展：给玩家发欢迎语、提供一个查询命令、封禁时在控制台记一笔。
 */
public final class MyExtension extends LengbanlistExtensionPlugin {

    @Override
    public String id() {
        return "welcome";
    }

    @Override
    protected void onExtensionEnable(ExtensionContext context) {
        // 1) 注册命令：挂成 /lban welcome <玩家>，与内置命令走同一条注册路径，
        //    权限、别名、补全、/lban help 过滤由核心统一处理
        context.commands().register(CommandSpec.sub(
                "welcome", "lban", id(), "lengbanlist.welcome",
                "/lban welcome <玩家>", "给玩家发欢迎语",
                (sender, command, label, args) -> {
                    if (args.length < 1) {
                        sender.sendMessage("§c用法: /lban welcome <玩家>");
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        sender.sendMessage("§c玩家不在线");
                        return true;
                    }
                    target.sendMessage(greeting(context));
                    sender.sendMessage("§a已发送欢迎语给 " + target.getName());
                    return true;
                }));

        // 2) 定时任务：走 context.scheduler()，它在 Folia 上用区域化调度
        context.scheduler().runSyncRepeating(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendMessage("§e欢迎回来，" + player.getName());
            }
        }, 20L * 10, 20L * 60 * 5);

        // 3) 监听核心事件
        getServer().getPluginManager().registerEvents(new BanWatcher(context), this);

        context.logger().info("欢迎扩展已启用");
    }

    private String greeting(ExtensionContext context) {
        // 读扩展自己的 config.yml（plugins/MyLengbanlistExtension/config.yml）
        return context.config().raw().getString("greeting", "§a欢迎回来！");
    }

    /** 封禁事件：读处罚数据 + 写日志。 */
    private static final class BanWatcher implements org.bukkit.event.Listener {

        private final ExtensionContext context;

        private BanWatcher(ExtensionContext context) {
            this.context = context;
        }

        @EventHandler
        public void onBan(LengbanlistBanEvent event) {
            context.logger().info("封禁 " + event.getTarget()
                    + " 理由 " + event.getReason()
                    + " 剩余 " + context.services().bans().activeBanCount() + " 条在用");
            Bukkit.getConsoleSender().sendMessage("§c[警戒] " + event.getTarget() + " 已被封禁");
        }
    }
}
```

## 五、编译、部署、验证

```bash
mvn clean package                 # 产物：target/my-lengbanlist-extension-1.0.0.jar
cp target/*.jar 服务端/plugins/   # 丢进 plugins，与 Lengbanlist-*.jar 同级
```

启动服务端，日志里应当出现（核心的 `CoreService.enable` 打的）：

```
[Lengbanlist] 已启用扩展 welcome v1.0.0（N 条命令在册）
```

自己验证三件事，不用等别人：

1. **依赖没打歪**：`unzip -l target/*.jar | grep org/leng` 必须是**空**的。
2. **命令真在**：游戏里敲 `/lban welcome <名字>`；`/lban help` 里也应出现这一行。
3. **停用干净**：`/lban reload` 或停服后再看日志，不应有 `ClassCastException`、`NoSuchMethodError`。

---

## 六、方法参考

### LengbanlistExtension（扩展入口）

| 方法 | 说明 |
| --- | --- |
| `String id()` | 唯一标识，全小写无连字符。也是功能开关与市场索引里的 id |
| `String name()` | 展示名，默认等于 `id()` |
| `String version()` | 扩展版本，会打进启用日志 |
| `String requiredApi()` | 需要的契约版本范围（`[2.0,3.0)`、`2.1`、`2.x`），留空不校验 |
| `void onEnable(ExtensionContext)` | 启用：在这里注册命令、钩子、任务、监听器 |
| `void onDisable()` | 停用：取消任务、存配置。核心还会兜底调用一次 |
| `Set<String> features()` | 本扩展负责的**没有命令**的功能键（如只提供钩子的功能） |

**推荐继承 `LengbanlistExtensionPlugin`**：它把 `onEnable`/`onDisable` 收成 `final`，替你做
"查核心 → `core.enable(this)` → 失败就 `disablePlugin`"，你只写 `id()` 与
`onExtensionEnable(ExtensionContext)`（`onExtensionDisable()` 可选）。手动实现当然也行，
那就自己在 `onEnable` 里 `Bukkit.getServicesManager().load(LengbanlistCore.class)`。

### LengbanlistCore（核心门面）

| 方法 | 说明 |
| --- | --- |
| `String apiVersion()` | 核心版本，形如 `2.1.6` |
| `ExtensionContext enable(LengbanlistExtension)` | 校验 `requiredApi`、建上下文、调 `onEnable`、接入命令表。失败抛 `ExtensionLoadException` |
| `void disable(String extensionId)` | 停用并注销命令；未注册时是空操作 |
| `boolean isEnabled(String)` | 是否已启用 |
| `Set<String> enabledExtensionIds()` | 已启用扩展的 id 快照 |

### ExtensionContext（每个扩展独占一份）

| 方法 | 返回 | 说明 |
| --- | --- | --- |
| `extensionId()` | `String` | 本上下文的扩展 id |
| `commands()` | `CommandRegistrar` | 注册命令 |
| `hooks()` | `HookRegistry` | 挂进核心决策点 |
| `messages()` | `Messages` | 当前人设模型的文案 |
| `data()` | `DataStore` | 复用核心连接池 |
| `services()` | `Services` | 处罚读写服务 |
| `config()` | `ExtensionConfig` | 扩展自己的 `config.yml` |
| `scheduler()` | `Scheduler` | Folia 安全的调度器 |
| `logger()` | `Logger` | 带扩展 id 前缀的日志器 |

### CommandRegistrar / CommandSpec

| 成员 | 说明 |
| --- | --- |
| `register(CommandSpec...)` | 注册命令；不完整的声明会被拒绝并抛异常 |
| `registeredNames()` | 本扩展已注册的命令名 |
| `CommandSpec.of(name, feature, permission, usage, description, executor)` | 顶层命令（`/ban` 这类） |
| `CommandSpec.sub(name, parent, feature, permission, usage, description, executor)` | 子命令，`parent="lban"` 即 `/lban <name>` |
| `CommandSpec(name, feature, permission, usage, description, aliases, executor, tabCompleter[, parent])` | 要别名或独立补全器时用完整构造 |
| `isComplete()` | `name`/`feature`/`permission`/`usage` 都非空才算完整 |

`executor` 返回 `false` 表示"没处理"：顶层命令会回落到服务端的用法提示；子命令由核心打印
`usage`。`tabCompleter` 不传时，若 `executor` 同时实现了 `TabCompleter` 会自动沿用。

**`feature` 决定这条命令的总开关**：关掉后命令不会注册，命令名可以让给别的插件。

### Scheduler

| 方法 | 说明 |
| --- | --- |
| `boolean isFolia()` | 当前服务端是否 Folia |
| `Cancellable runSync(Runnable)` | 下一个 tick 在主线程执行 |
| `Cancellable runSyncLater(Runnable, long delayTicks)` | 延迟若干 tick |
| `Cancellable runSyncRepeating(Runnable, long delayTicks, long periodTicks)` | 周期任务 |
| `void runAsync(Runnable)` | 异步执行（**不要**在异步里碰 Bukkit API） |
| `Cancellable runAsyncLater(Runnable, long delayMillis)` | 异步延迟（毫秒） |
| `Cancellable runAsyncRepeating(Runnable, long delayTicks, long periodTicks)` | 异步周期 |

### ExtensionConfig

| 方法 | 说明 |
| --- | --- |
| `FileConfiguration raw()` | Bukkit 配置对象，直接 `getString`/`getInt` |
| `File file()` | 配置文件路径（外部扩展在 `plugins/<扩展名>/config.yml`） |
| `void save()` | 写盘 |
| `void reload()` | 从盘上重读 |

### Services（处罚读写）

| 接口 | 方法 |
| --- | --- |
| `BanService` | `ban(target, staff, durationMillis, reason, silent)`、`banIp(...)`、`unban(target, actor)`、`unbanIp(ip, actor)`、`isBanned`、`isIpBanned`、`findBan`、`findIpBan`、`activeBans()`、`activeIpBans()`、`activeBanCount()`、`activeIpBanCount()` |
| `MuteService` | `mute(target, staff, durationMillis, reason)`（返回时长）、`unmute(target, actor)`、`isMuted`、`activeMutes()`、`activeMuteCount()` |
| `WarnService` | `warn(target, staff, reason)`、`unwarn(target, warnId, actor)`、`countActiveWarnings(target)`、`activeWarnings(target)`、`warnedPlayers()` |

**写处罚请走这里**，不要直接写核心表——同步、审计、事件广播、Webhook 都在服务层里。

### DataStore（自己的表）

| 方法 | 说明 |
| --- | --- |
| `Connection connection()` | 借出核心连接池里的一个连接；**用完自己关**（try-with-resources），连接会回到池里 |
| `String tablePrefix()` | 本扩展专属的表名前缀，形如 `ext_<扩展名>_`（连字符换成下划线） |
| `boolean isNetworkDatabase()` | 是否 MySQL/MariaDB（跨服共享） |

### HookRegistry（插进核心判定）

| 方法 | 说明 |
| --- | --- |
| `register(Class<T>, T)` | 注册钩子，扩展停用时自动摘掉 |
| `unregister(Class<T>)` | 手动摘掉 |

| 钩子 | 方法 | 用途 |
| --- | --- | --- |
| `PunishmentDecisionHook` | `operatorWeight(sender)`、`webOperatorWeight()`、`playerWeight(target)`、`ipWeight(ip)` | 权重免疫：权重低的操作者罚不动权重高的目标 |
| `DurationPolicyHook` | `ban(target)`、`ipBan(ip)`、`mute(target)` → `Decision(durationMillis, offenseCount)` | 时长策略：惯犯加重 |

钩子缺席时核心回落到默认行为，所以「取消注册钩子」本身就是一个安全的降级路径。

### Messages / 事件

| 方法 | 说明 |
| --- | --- |
| `Messages.current()` | 当前人设模型（取不到返回 `null`，要兜底） |
| `Messages.currentName()` | 模型名 |

事件（`org.leng.api.events`，普通 Bukkit 事件，`registerEvents` 即可）：

| 事件 | 可读字段 |
| --- | --- |
| `LengbanlistBanEvent` | `getEntry()`、`getTarget()`、`getStaff()`、`getReason()`、`getDurationMillis()`、`isSilent()` |
| `LengbanlistBanIpEvent` | `getEntry()`、`getIp()`、`getStaff()`、`getReason()`、`getDurationMillis()`、`isSilent()` |
| `LengbanlistUnbanEvent` | `getTarget()`、`isIp()`、`getActor()` |
| `LengbanlistMuteEvent` | `getEntry()`、`getTarget()`、`getStaff()`、`getReason()`、`getDurationMillis()` |
| `LengbanlistUnmuteEvent` | `getTarget()`、`getActor()` |
| `LengbanlistWarnEvent` | `getEntry()`、`getTarget()`、`getStaff()`、`getReason()` |
| `LengbanlistUnwarnEvent` | `getTarget()`、`getWarnId()`、`getActor()` |
| `LengbanlistReportEvent` | `getEntry()`、`getReporter()`、`getTarget()`、`getReason()` |

### ApiVersion（自己比较版本时用）

| 方法 | 说明 |
| --- | --- |
| `satisfies(String requiredRange, String actualVersion)` | 范围判断：`[2.0,3.0)`、`2.1`（=2.1.x）、`2.x` |
| `compare(String a, String b)` | 逐段比较，返回负/零/正 |

---

## 七、生命周期与线程

- 扩展的 `onEnable` 在**主线程**、核心启用之后调用；`onDisable` 同理。
- 命令执行器、事件监听器都在主线程（`runAsync` 出去的除外）。
- **Folia 上必须用 `context.scheduler()`**：`Bukkit.getScheduler()` 会直接抛
  `UnsupportedOperationException`；涉及实体的操作要用 `SchedulerUtils` 同款的区域化调度。
- 扩展崩溃/超时只影响该扩展：核心捕获异常、记日志、回滚这次注册，服务器照常跑。

## 八、版本与兼容

- `requiredApi()` 写范围，核心在 `enable` 前校验，不满足就**拒绝启用并给出可读原因**，
  而不是等到运行时抛 `NoSuchMethodError`。
- 契约**只增不改**：已有方法不会改签名、不会删。想提前挡住未来的不兼容，把 `requiredApi()`
  写成上界封闭的区间。
- `lengbanlist-api` 会连源码包一起发布，IDE 里点进接口就能看到这些注释。

## 九、排查

| 现象 | 原因 |
| --- | --- |
| 日志 `没有找到 Lengbanlist 核心` | `plugin.yml` 少了 `depend: [Lengbanlist]`，或服务端没装核心 |
| `ClassCastException` / `NoSuchMethodError` | 把 `org/leng/**` 打进自己 jar 了，或依赖 `scope` 不是 `provided` |
| Folia 上抛 `UnsupportedOperationException` | 用了 `Bukkit.getScheduler()`，改用 `context.scheduler()` |
| 命令没出现 | `feature` 被别的提供者占用、玩家没权限、或命令名被别的插件占了（核心日志里有警告） |
| 扩展启用了但 `onEnable` 里的东西没生效 | 检查是不是把逻辑写在了构造函数里——那时核心还没给上下文 |
