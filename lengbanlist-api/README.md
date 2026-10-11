# Lengbanlist API

给 **Lengbanlist 扩展**用的契约模块。扩展可以是两种形态：

| 形态 | 语言 | 放哪里 | 适合 |
| --- | --- | --- | --- |
| **Lua 扩展** | Lua 5.2 | `plugins/Lengbanlist/extensions/<扩展名>/` | 低频小功能，不写 Java、不编译 |
| **Java 扩展** | Java 17 | `plugins/` 下的独立插件 jar | 需要自定义监听器、自己建表、复杂逻辑 |

只是加个命令、发点消息的话，先看 [Lua 扩展](../../docs/LuaExtensions.md)——不用编译、不用发版。
本模块面向的是后者。

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

> GitHub Packages 需要鉴权（`~/.m2/settings.xml` 里配 `github` 的账号与 token）。
> 拿不到包时也可以把本仓库 clone 下来 `mvn install`，本地仓库里就有同样的坐标。

**三条硬规矩**（破了会在运行时炸，且很难查）：

1. **`scope` 必须是 `provided`**，而且**绝不能**把 `org/leng/**` 打进自己的 jar。
   打进去就会加载出第二份接口副本，核心传回的对象做类型检查时报 `ClassCastException`。
2. **`plugin.yml` 里必须写 `depend: [Lengbanlist]`**。这是扩展在运行时能拿到本模块与核心类的
   唯一来源（Spigot 上插件无法通过传递依赖加载库）。
3. 本模块**零第三方依赖**（只有 provided 的 spigot-api），所以别指望它替你带进来任何库——
   需要什么自己 shade，并 relocate。

---

## 二、最小扩展

```java
package com.example.myext;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.leng.api.CommandSpec;
import org.leng.api.ExtensionContext;
import org.leng.api.LengbanlistExtensionPlugin;

public final class MyExtension extends LengbanlistExtensionPlugin {

    @Override
    public String id() {
        return "myext";
    }

    @Override
    protected void onExtensionEnable(ExtensionContext context) {
        context.commands().register(CommandSpec.of(
                "hello", id(), "lengbanlist.myext",
                "/hello", "打个招呼",
                (sender, command, label, args) -> {
                    sender.sendMessage("§a你好，" + sender.getName());
                    return true;
                }));

        // 定时任务请走 context.scheduler()：它在 Folia 上会用区域化调度，
        // Bukkit.getScheduler() 在 Folia 上会直接抛异常。
        context.scheduler().runSyncRepeating(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar("§e欢迎回来");
            }
        }, 20L, 20L * 60);
    }
}
```

`plugin.yml`：

```yaml
name: Lengbanlist-Ext-MyExt
version: 1.0.0
main: com.example.myext.MyExtension
api-version: '1.17'
depend: [Lengbanlist]      # ← 少了这行，扩展拿不到核心
```

不继承 `LengbanlistExtensionPlugin` 也可以——直接实现 `LengbanlistExtension`，自己在
`onEnable` 里 `Bukkit.getServicesManager().load(LengbanlistCore.class)` 然后 `core.enable(this)`，
在 `onDisable` 里 `core.disable(id())`。基类只是把这段样板（连同"没装核心怎么办"、
"启用失败别停在半死状态"）一次做对。

---

## 三、`ExtensionContext` 里有什么

每个扩展拿到的是自己专属的上下文：

| 方法 | 用途 |
| --- | --- |
| `commands()` | 注册命令（`CommandSpec`，支持 `parent` 挂成 `/lban <子命令>`） |
| `hooks()` | 挂进核心决策点：`PunishmentDecisionHook`（能不能罚）、`DurationPolicyHook`（罚多久） |
| `messages()` | 取当前人设模型，让文案口吻与服务器一致 |
| `data()` | 复用核心连接池读写扩展自己的表（**别自建连接池**） |
| `services()` | 封禁 / 禁言 / 警告的读写服务 |
| `config()` | 扩展自己的 `config.yml` |
| `scheduler()` | 调度器，Folia 安全 |
| `logger()` | 带扩展 id 前缀的日志器 |

事件：`org.leng.api.events` 下有 `LengbanlistBanEvent`、`LengbanlistMuteEvent`、
`LengbanlistWarnEvent`、`LengbanlistReportEvent`、`LengbanlistUnbanEvent` 等，
按普通 Bukkit 事件注册即可。

---

## 四、版本与兼容

- `LengbanlistExtension#requiredApi()` 写一个范围（`[2.0,3.0)`、`2.1`、`2.x`），核心在启用前校验，
  不满足就**拒绝启用并给出可读原因**，而不是等到运行时抛 `NoSuchMethodError`。
- 契约只**增不改**：已有方法不会改签名、不会删。想提前挡住未来的不兼容，就把 `requiredApi()`
  写成上界封闭的区间。
- `LengbanlistCore#apiVersion()` 返回当前核心提供的版本。

---

## 五、排查

| 现象 | 原因 |
| --- | --- |
| 日志 `没有找到 Lengbanlist 核心` | `plugin.yml` 少了 `depend: [Lengbanlist]`，或服务端没装核心 |
| `ClassCastException` / `NoSuchMethodError` | 把 `org/leng/**` 打进自己 jar 了，或 `scope` 不是 `provided` |
| Folia 上抛 `UnsupportedOperationException` | 用了 `Bukkit.getScheduler()`，改用 `context.scheduler()` |
| 扩展启用了但命令没出现 | 功能键被别的提供者占用、权限节点没给玩家、或命令名被其他插件占了（日志里有警告） |
