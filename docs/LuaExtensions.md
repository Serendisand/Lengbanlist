# Lengbanlist Lua 扩展

低频功能可以做成 Lua 脚本扩展放进 `plugins/Lengbanlist/extensions/`，不必写 Java 插件。
扩展注册的命令与内置命令走**同一条注册路径**，所以权限、别名、补全、`/lban help` 过滤、
功能开关、`/lban` 子命令分派全部自动一致。

> 语言是 **Lua 5.2**（LuaJ）。`table.unpack`、`goto` 都有；没有整除 `//`、位运算符（5.3+ 特性）。

---

## 一、目录布局

```
plugins/Lengbanlist/extensions/
├── tp.lua                    ← 脚本（文件名 = 扩展名）
├── staffchat.lua
├── tp/
│   └── config.yml            ← 这个扩展自己的配置
└── staffchat/
    └── config.yml
```

- **脚本在 = 已安装**，删脚本 = 卸载。没有安装清单，也没有第二个地方记录"装了什么"。
- 脚本平铺在 `extensions/` 下，一眼能看全装了哪些；配置各回各家的同名子目录，不挤在根下。
- 缺配置目录的扩展，加载时会**自动生成**一份只含 `enabled` 的 `config.yml`；
  扩展自己要用什么键，由脚本作者往里补。已有的配置不会被覆盖。
- **开关**是 `extensions/<扩展名>/config.yml` 里的 `enabled`（缺省 `true`）。核心的 `config.yml` 里
  **没有**对应的键——一个功能只有一个开关来源。
- 官方扩展（`tp`、`staffchat`）随核心 jar 打包，只在 `extensions/` 目录**整个不存在**时
  释放一次；删掉某个官方扩展后不会被下次启动复活。

> 2.1.6 之前的老布局（`extensions/<扩展名>/extension.lua`）**已不再识别**。把脚本挪到
> `extensions/<扩展名>.lua` 即可，配置目录原地不动；漏挪的会在启动日志里收到提示。

管理命令：`/lban ext list`、`/lban ext reload`、`/lban ext enable|disable <扩展名>`
（权限 `lengbanlist.reload`）。

---

## 二、最小可用脚本

```lua
leng.name("示例")            -- 展示名，出现在 /lban ext list
leng.version("1.0.0")
leng.api("1.0")             -- 需要的脚本 API 版本；核心不满足就拒绝加载

leng.command{
    name = "hello",
    parent = "lban",                       -- 省略 = 顶层命令
    permission = "lengbanlist.admin",
    usage = "/lban hello <名字>",
    description = "打个招呼",
    run = function(ctx)
        ctx.reply("§a你好，" .. (ctx.args[1] or "陌生人"))
        return true
    end,
}
```

---

## 三、元数据（都在脚本顶层调用）

| 调用 | 说明 |
| --- | --- |
| `leng.name(文本)` | 展示名，默认等于扩展名 |
| `leng.version(文本)` | 版本号，出现在 `/lban ext list` |
| `leng.api(范围)` | 需要的脚本 API 版本，写法同 `ApiVersion`：`1.0`（=1.0.x）、`[1.0,2.0)`、`1.x` |

**扩展名就是脚本文件名**（`tp.lua` → `tp`），脚本无法改：注册表在脚本跑起来之前就要拿它建索引。

---

## 四、注册命令

```lua
leng.command{
    name       = "tp",                    -- 必填，命令名（不带斜杠）
    parent     = "lban",                  -- 省略/空 = 顶层命令；"lban" = /lban tp
    feature    = "tp",                    -- 功能键，默认 = 扩展名
    permission = "lengbanlist.admin",     -- 权限节点，默认 = lengbanlist.<功能键>
    usage      = "/lban tp <玩家名>",      -- 用法串；返回 false 时提示它
    description = "传送到玩家身边",         -- 也接受 desc
    aliases    = {"tpa"},                 -- 顶层命令的别名；子命令用不到
    run        = function(ctx) ... end,   -- 必填
    complete   = function(ctx) ... end,   -- 可选，返回字符串数组
}
```

`leng.command` 只能在**脚本顶层**调用。运行期（命令回调、定时任务、事件里）再注册会被拒绝
并记一条日志——命令表是加载期一次建好的，运行期改动会让命令表、帮助、补全对不上。

### 回调上下文 `ctx`

`run` 与 `complete` 拿到的是同一个形状：

| 字段 | 说明 |
| --- | --- |
| `ctx.args` | 参数数组（1 开始）。子命令不含子命令名本身 |
| `ctx.label` | 实际输入的命令名，如 `lban tp` |
| `ctx.sender.name` | 发送者名 |
| `ctx.sender.is_player` | 是否玩家 |
| `ctx.sender.player` | 玩家对象；控制台为 `nil` |
| `ctx.sender:has_permission("节点")` | 权限判断 |
| `ctx.reply(文本)` | 带插件前缀回复发送者 |
| `ctx.usage()` | 回复用法提示 |

`run` 返回 `false` 表示"没处理"：顶层命令回落到服务端的用法提示，子命令会提示 `usage`。

### 玩家对象

`ctx.sender.player`、`leng.player(名字)`、`leng.players()` 拿到的是**代理表**（不是 Java 对象，
脚本没法顺着它摸到别的东西）：

| 成员 | 说明 |
| --- | --- |
| `p.name` / `p.uuid` / `p.world` | 名字、UUID、所在世界 |
| `p.x` / `p.y` / `p.z` | 坐标（取用时的快照） |
| `p.is_online` | 是否在线 |
| `p:send(文本)` | 发消息 |
| `p:has_permission("节点")` | 权限判断 |
| `p:teleport(x, y, z, "世界名")` | 传送到坐标，成功返回 true |
| `p:teleport_to(另一个玩家)` | 传送到该玩家身边 |
| `p:give("DIAMOND", 数量)` | 给物品，材料名不认识返回 false |

---

## 五、门面（`leng` 表）

| 调用 | 说明 |
| --- | --- |
| `leng.log(文本)` / `leng.log(级别, 文本)` | 写插件日志，级别 `info`/`warn`/`error`（默认 info） |
| `leng.config(键, 默认值)` | 读 `extensions/<扩展名>/config.yml`；支持 `a.b.c` 与表/数组 |
| `leng.player(名字)` | 按名取在线玩家（先精确、再前缀），没有返回 `nil` |
| `leng.players()` | 全部在线玩家的数组 |
| `leng.online_players(前缀)` | 名字数组（补全常用） |
| `leng.console(文本)` | 往控制台发一条消息 |
| `leng.task(延迟tick, 周期tick, 函数)` | 起定时任务，返回句柄；周期为 0 表示只跑一次 |
| `leng.cancel(句柄)` | 取消任务 |
| `leng.on(事件名, 函数)` | 订阅事件（见下） |

所有函数都能用 `leng.f(...)` 与 `obj:f(...)` 两种写法，参数从末尾数——少写一个冒号不会报错。

---

## 六、事件订阅

```lua
leng.on("player_join", function(ctx)
    ctx.player:send("§e欢迎回来，" .. ctx.player.name)
end)
```

| 事件名 | 时机 | ctx 额外字段 |
| --- | --- | --- |
| `player_join` | 玩家进入服务器 | — |
| `player_quit` | 玩家退出 | — |
| `player_command` | 玩家执行命令 | `ctx.command`、`ctx.cancelled`、`ctx.set_cancelled(布尔)` |

**只支持同步事件**。异步事件（`player_chat` 等）会被明确拒绝并说明理由：它们在别的线程触发，
而脚本与 Bukkit API 都只能在主线程跑。要支持异步事件得先设计一套"投递回主线程"的模型。

事件里抛异常只记日志，不影响核心，也不影响同一个事件的其它订阅者。

---

## 七、沙箱边界

**不给**：文件系统（`io`、`dofile`、`loadfile`）、`require`/`package`、`debug` 库、
`os.execute`/`os.exit`/`os.getenv`、Java 反射、协程（LuaJ 的协程是真线程且非守护，
挂起的协程会钉住线程，攒够几十个能让 `/stop` 卡死）。

**处罚写操作一律不给**：封禁/禁言/警告/踢出留在核心，脚本拿不到。脚本出问题最多是自己崩。

### 超时

脚本跑在主线程上，所以有两道硬中断，先到者生效：

| 保险 | 阈值 |
| --- | --- |
| 指令预算 | 单次调用 200,000 条指令 |
| 墙钟 | 单次调用 5 秒 |

触发后**停用该扩展**（而不只是这次失败）并写一条日志——死循环脚本不能留着每次调用都卡主线程。
`pcall`/`xpcall` 吞不掉这个中断。

---

## 八、从零做一个扩展（完整走一遍）

目标：玩家进服时按扩展自己的配置发欢迎语，另外提供一个命令手动补发。

**第 1 步**：写 `plugins/Lengbanlist/extensions/welcome.lua`（文件名就是扩展名，也是功能键）：

```lua
leng.name("欢迎语")
leng.version("1.0.0")
leng.api("1.0")

-- 读扩展自己的 config.yml；第二个参数是取不到时的默认值
local TEMPLATE = leng.config("message", "§e欢迎回来，%s！")
local PERMISSION = leng.config("permission", "lengbanlist.admin")

local function greet(player)
    player:send(string.format(TEMPLATE, player.name))
end

leng.on("player_join", function(ctx)
    greet(ctx.player)
end)

leng.command{
    name = "greet",
    parent = "lban",
    permission = PERMISSION,
    usage = "/lban greet <玩家名>",
    description = "给玩家补发欢迎语",
    run = function(ctx)
        local target = leng.player(ctx.args[1] or "")
        if not target then
            ctx.reply("§c玩家不在线")
            return true
        end
        greet(target)
        ctx.reply("§a已发送")
        return true
    end,
    complete = function(ctx)
        return leng.online_players(ctx.args[#ctx.args] or "")
    end,
}
```

**第 2 步**：写 `plugins/Lengbanlist/extensions/welcome/config.yml`：

```yaml
# 这个扩展自己的配置。核心的 config.yml 里没有它的键。
enabled: true
message: "§e欢迎回来，%s！"
permission: lengbanlist.admin
```

这份也可以不写——加载时会自动生成一份只有 `enabled` 的，脚本里的默认值照样生效；
但脚本读的键（这里是 `message`、`permission`）得由你自己写进去，插件猜不出来。

**第 3 步**：`/lban ext reload`（或重启服务端）。**Lua 扩展不需要编译**，改完脚本重载即可。

**第 4 步**：验证。`/lban ext list` 应显示 `welcome v1.0.0 [启用]`；进服能收到欢迎语；
`/lban greet <名字>` 能补发；`/lban help` 里出现这一行。

**第 5 步**：关掉用 `/lban ext disable welcome`——它写的是上面那个 `config.yml`（文本级改写，
注释原样保留），不会碰核心配置。

---

## 九、排障

| 现象 | 先看哪里 |
| --- | --- |
| 扩展没生效 | `/lban ext list`：有没有列出来、状态是启用还是禁用、有没有加载失败原因 |
| 命令没出现 | 子命令要在 `/lban` 底下用；顶层命令名可能被别的插件占了（日志里有警告） |
| 改完脚本没变化 | `/lban ext reload` |
| 升级后扩展集体消失 | 脚本得是 `extensions/<扩展名>.lua`；老布局的 `extensions/<扩展名>/extension.lua` 已不识别，日志里会指路 |
| 日志里的报错 | 前缀是 `[扩展名]`；语法错、事件名拼错、API 版本不符都会在加载期就报出来 |

脚本报错不会阻止服务端启动，也不会影响别的扩展。
