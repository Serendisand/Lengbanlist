# 📜 Lengbanlist 插件命令列表

## 🎯 核心命令
| 命令 | 功能 | 别名 |
|------|------|------|
| `/lban list` | 查看封禁名单 | - |
| `/lban a` | 广播当前封禁人数 | - |
| `/lban toggle` | 开关自动广播 | - |
| `/lban model <名称>` | 切换角色模型 | - |
| `/lban models list` | 查看本地与云端模型 | - |
| `/lban vanish` | 隐身/现身（完全隐身，含装备） | - |
| `/lban freeze <玩家> <理由>` | 冻结玩家 | - |
| `/lban unfreeze <玩家/all>` | 解除冻结 | - |
| `/lban reload` | 重载配置 | - |
| `/lban info` | 查看插件信息 | - |

## ⚔️ 封禁管理
| 命令 | 功能 | 别名 |
|------|------|------|
| `/lban add <玩家/IP段> <天数> <原因>` | 添加封禁 | `/ban` |
| `/lban remove <玩家/IP段>` | 移除封禁 | `/unban` |
| `/ban-ip <IP/IP段> <天数> <原因>` | 封禁IP | - |
| `/unban-ip <IP/IP段>` | 解封IP | - |
| `/lban check <玩家/IP/IP段>` | 检查封禁状态 | - |
| **示例** | `/ban-ip 172.198.2.x 7d 恶意刷屏` | `/unban 172.198.2.x` |

## 🔇 禁言系统
| 命令 | 功能 | 别名 |
|------|------|------|
| `/lban mute <玩家/IP段> <原因>` | 禁言玩家 | - |
| `/lban unmute <玩家/IP段>` | 解除禁言 | - |
| `/lban list-mute` | 查看禁言列表 | - |
| **示例** | `/lban mute 172.198.2.x 1d 违规发言` | - |

## ⚠️ 警告系统
| 命令 | 功能 | 别名 |
|------|------|------|
| `/lban warn <玩家/IP段> <原因>` | 警告玩家 | `/warn` |
| `/lban unwarn <玩家/IP段>` | 移除警告 | `/unwarn` |

## 🛠️ 实用工具
| 命令 | 功能 | 别名 |
|------|------|------|
| `/lban getIP <玩家>` | 查询玩家IP | - |
| `/kick <玩家> <原因>` | 踢出玩家 | - |
| `/report <玩家> <原因>` | 举报玩家 | - |
| `/report accept <ID>` | 受理举报 | - |
| `/report close <ID>` | 关闭举报 | - |
| `/history <玩家/IP/IP段>` | 查询处罚历史 | - |
| **示例** | `/lban check 172.198.2.x` | - |

## 🌐 多语言
| 命令 | 功能 |
|------|------|
| `/lban language` | 切换语言 |

## 🖥️ 界面操作
| 命令 | 功能 |
|------|------|
| `/lban open` | 打开GUI界面 |

## 🛡️ 管理员工具
| 命令 | 功能 | 权限 |
|------|------|------|
| `/lban vanish` | 切换自身隐身：其他玩家完全看不见你（手持物品与盔甲一并隐藏，玩家列表也不显示）。持有 `lengbanlist.vanish.see` 的管理员仍然看得见 | `lengbanlist.vanish` |
| `/lban freeze <玩家> <理由>` | 冻结玩家：不能移动/破坏/交互/丢物品/使用命令/传送，屏幕弹出红色标题与理由 | `lengbanlist.freeze` |
| `/lban unfreeze <玩家>` | 解除冻结（只能手动解冻） | `lengbanlist.freeze` |
| `/lban unfreeze all` | 一次性解除全部冻结 | `lengbanlist.freeze` |
| `/lban freeze list` | 查看当前冻结名单 | `lengbanlist.freeze` |
| **说明** | 冻结记录存在数据库里（重启不丢，共享数据库时跨子服生效）；`freeze.allowed-commands` 可放行登录类命令 | - |

## 🎭 模型管理
| 命令 | 功能 |
|------|------|
| `/lban model <名称>` | 切换模型（提示语气随之改变） |
| `/lban models list` | 列出本地已装 + 云端可下载的模型 |
| `/lban models install <ID\|all>` | 下载安装云端模型 |
| `/lban models refresh` | 重新拉取云端模型索引 |
| `/lban models pin/unpin <ID>` | 锁定/解锁本地模型（锁定后云端更新不覆盖） |
| `/lban models featured` | 查看本月精选模型 |
| `/lban models stats` | 查看本服模型安装统计 |

> 📌 模型文本支持继承：全局默认在 `plugins/Lengbanlist/models/_base.yml`，模型文件只写自己不一样的部分。
> 自己写模型时照抄 `_base.yml`，改个文件名放回 `models/` 目录即可（以 `_` 开头的文件不会被当成模型加载）。
> `help-overrides` 可以只覆写帮助菜单里的某几行：`"lban freeze"` 这类键按命令匹配，`"#title"` / `"#version"` 对应帮助框的标题行和页脚行。
> 值以 `"> "` 开头表示**只替换描述文字**（命令部分沿用全局默认），例如 `"lban freeze": "> 把人定住～"`；想连前面的符号一起改就直接写整行。
> `help-bullet: "§e✦"` 可以指定模型自己的命令行符号（默认 `§2✦`），设了它命令行就都能用短写法。

## ↩️ 操作回滚
| 命令 | 功能 |
|------|------|
| `/lban rollback <操作人> <开始时间> <结束时间> [操作类型]` | 回滚指定管理员在指定时间范围内的操作（基于审计日志） |
| **时间格式** | `YYYY-MM-DD` 或 `YYYY-MM-DD HH:mm:ss`（开始默认 00:00:00，结束默认 23:59:59） |
| **操作类型（留空为全部）** | `ban` / `ban-ip` / `unban` / `unban-ip` / `mute` / `unmute` / `warn` / `unwarn` / `kick` |
| **示例** | `/lban rollback Steve 2026-08-01 2026-08-14 ban` |
| **示例** | `/lban rollback Steve 2026-08-01 2026-08-14` |

> ⚠️ 回滚说明：
> - 封禁 → 解封；解封 → 重新封禁（30 天）；禁言 → 解除；解除禁言 → 重新禁言（7 天）
> - 警告 → 撤销警告；取消警告 → 恢复警告；踢出 → 无法回滚（自动跳过）
> - 解封/解除禁言的审计记录不含原时长，恢复时使用固定时长并记为 `rollback`
> - 需要 `lengbanlist.rollback` 权限（默认 OP），功能开关 `features.rollback`

> 💡 提示：所有命令都需要对应权限才能执行
> 📌 自定义模型：将 YAML 文件放入 plugins/Lengbanlist/models/ 目录（只写要覆写的字段即可），
>    然后使用 `/lban reload` 重载即可使用
> 文档内的命令可能并不是最新的，你需要👇
> 📌 使用 `/lban` 或`/lban help` 可查看游戏内帮助信息
