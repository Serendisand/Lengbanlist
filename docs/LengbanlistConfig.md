---

prefix: "§f§l[§bLengbanlist§f§l]"
sendtime: 5 #已分钟为单位
opensendtime: true #开启循环播报封禁人数
Model: "Default"
valid-models: "Default English HuTao Furina Zhongli Keqing Xiao Ayaka Zero Herta" # 内置模型列表，自定义模型自动从 plugins/Lengbanlist/models/ 加载
disable-update-check: false # 是否禁用更新检查（默认：false）
database:
  type: "sqlite"  # 可选 sqlite / mysql / mariadb / postgresql
  mysql:
    host: "localhost"
    port: 3306
    database: "lengbanlist"
    username: "root"
    password: "password"
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

---
