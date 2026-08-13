---

prefix: "§f§l[§bLengbanlist§f§l]"
sendtime: 5 #已分钟为单位
opensendtime: true #开启循环播报封禁人数
Model: "Default"
valid-models: "Default English HuTao Furina Zhongli Keqing Xiao Ayaka Zero Herta" # 内置模型列表，自定义模型自动从 plugins/Lengbanlist/models/ 加载
disable-update-check: false # 是否禁用更新检查（默认：false）
database:
  type: "sqlite"  # 或 "mysql"
  mysql:
    host: "localhost"
    port: 3306
    database: "lengbanlist"
    username: "root"
    password: "password"

---
