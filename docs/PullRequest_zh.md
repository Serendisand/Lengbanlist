# 如何成为贡献者

欢迎来到本项目！我们非常期待你的贡献。无论是修复 bug、添加新功能还是改进文档，都欢迎您~

## 开始之前

在开始贡献之前，请确保你已经具备以下环境：

- **Java 17**：本项目需要 Java 17 或更高版本。
- **Maven**：构建工具，用于管理项目依赖和构建。
- **Git**：版本控制工具

## 开发环境搭建

1. **克隆仓库**：
   ```bash
   git clone https://github.com/Serendisand/Lengbanlist.git
   cd Lengbanlist

2. **提交规范**

> 详细说明请查看 [commit_help.md](commit_help.md)

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
