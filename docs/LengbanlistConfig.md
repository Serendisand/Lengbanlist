# Lengbanlist 配置速览

配置文件分两份，改之前先确认自己要改的是哪一份：

| 文件 | 管什么 |
|------|--------|
| `config.yml` | 功能开关、广播、冻结规则、Web 面板、更新检查、各功能参数 |
| `storage.yml` | 数据库、数据保留策略、封禁缓存、本服标识 `server-name` |
| `models/_base.yml` | 所有模型文案的全局默认（模型文件只写覆写部分） |
| `broadcast.yml` / `chatconfig.yml` | 广播内容 / 违禁词与禁言规则 |
| `eula.yml` | 使用前需要同意的许可 |

旧版本写在 `config.yml` 里的 `database` 段与 `history-retention-days`，首次启动会自动迁移到 `storage.yml`。

---

## config.yml 主要字段

```yaml
prefix: "§f§l[§bLengbanlist§f§l]"   # 消息前缀
opensendtime: true                  # 是否开启封禁人数循环广播
sendtime: 5                         # 广播间隔（分钟）
Model: "Default"                    # 当前模型（说话语气）
model-auto-detect: true             # 首次加载按系统语言自动选模型（zh→Default，其它→English）

features:                           # 每个功能都能单独关掉
                                    # 关掉后独立命令（/ban、/report 等）直接不注册，名字让给别的插件
                                    # /lban 的子命令（/lban freeze、/lban vanish 等）无法单独注销，会提示"已被管理员禁用"
  ban: true                         # 封禁
  vanish: true                      # 管理员隐身（/lban vanish）
  freeze: true                      # 冻结玩家（/lban freeze）
  # ……其余见 config.yml 内注释

freeze:
  block-commands: true              # 冻结期间禁止使用命令（防 /spawn /home 逃脱）
  allowed-commands: []              # 仍然放行的命令，装了登录插件可填 [login, register]

update-check:
  enabled: true                     # 更新检查
  mirrors: [...]                    # 镜像链，按顺序尝试
```

## storage.yml 主要字段

```yaml
server-name: "default"              # 本服标识：多子服共用数据库时标记数据来源

database:
  type: "sqlite"                    # sqlite / mysql / mariadb / postgresql
  sqlite:
    file: "lengbanlist.db"
    synchronous: "NORMAL"           # WAL 落盘策略，NORMAL 为推荐值
    auto-vacuum: true
  retention:
    ip-history-days: 0              # IP 历史保留天数，0=永久
    history-days: 7                 # 处罚历史保留天数
  cache:
    ban-ttl-seconds: 5              # 封禁缓存 TTL（多服共享库时的兜底刷新间隔）
    warn-ttl-seconds: 5
  mysql:
    host: "localhost"
    port: 3306
    database: "lengbanlist"
    username: "root"
    password: "password"
    pool-size: 10
  mariadb:        # 与 mysql 段同构，可整段省略（省略时沿用 mysql 段）
    host: "localhost"
    port: 3306
    database: "lengbanlist"
    username: "root"
    password: "password"
  postgresql:
    host: "localhost"
    port: 5432
    database: "lengbanlist"
    username: "postgres"
    password: "password"
    # 布尔字段（active / is_auto / revoked / success）在 PostgreSQL 里以 SMALLINT 存 0/1，
    # 与 MySQL、SQLite 保持同一套 SQL；用外部工具查库时按 0/1 比较，不要写 true/false。
```

> 多子服共用一份 MySQL / MariaDB / PostgreSQL 时：
> - 每台子服起个不同的 `server-name`，审计日志会记下每条操作来自哪台服（`/lban audit`、Web 面板可见）；
> - `database.retention.*` 建议只在一台子服开启清理，避免多台同时删数据。
