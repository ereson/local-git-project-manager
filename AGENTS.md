# Codex 开发说明

## 开始前

开发前依次阅读：

1. `docs/development-plan.md`：确认当前任务和完成标准。
2. `docs/product-spec.md`：确认产品范围和验收要求。
3. `docs/architecture.md`：确认模块边界和技术方案。
4. `docs/database.md`：涉及持久化时确认表结构和迁移规则。

只实现当前任务需要的 MVP 能力，不提前开发候选功能。

## 技术基线

- Java（当前稳定的 JDK LTS）与 JavaFX。
- FXML + CSS 构建界面，Gradle Kotlin DSL 构建项目。
- SQLite + JDBC；数据库变更使用内置 SQL Migration。
- 使用 `ProcessBuilder` 参数列表调用本机 Git，不通过 Shell 拼接命令。
- 后台任务使用 `ExecutorService` 或 `CompletableFuture`，UI 更新回到 JavaFX Application Thread。
- 日志使用 SLF4J + Logback，仅记录必要的错误信息；JSON 使用 Jackson；测试使用 JUnit。

## 架构边界

- Controller 只处理控件绑定、事件转发、导航和弹窗。
- ViewModel 管理页面状态并调用 Application Service。
- Application Service 编排用例；Domain 保存业务规则；Infrastructure 封装数据库、Git、文件系统、IDE 和 Windows 集成。
- UI 层不得直接执行 Git、扫描目录、写数据库、启动进程或访问网络。
- Git 操作统一经过 Git Client，同一项目的 Fetch、Pull、分支切换和状态刷新不得并发执行。

## 实现要求

- 优先复用项目现有代码、Java 标准库和平台能力；不要为单一实现增加抽象层或新依赖。
- Windows 路径必须支持中文、空格、大小写差异和 `.git` 文件场景。
- 所有外部命令使用独立参数并设置超时，同时消费 stdout 和 stderr；不得使用 `cmd /c` 拼接用户输入。
- 数据库迁移按版本追加，在事务中执行；失败时停止写入并保留原数据库。
- 不记录文件内容、Token、密码、SSH 私钥或凭据助手返回的敏感信息。
- 非平凡逻辑至少保留一个能复现边界条件的自动化检查；Git 集成测试使用临时仓库。
- 完成任务后更新 `docs/development-plan.md` 中的当前任务和状态；产品或架构决策变化时同步更新对应文档。

## 架构约束

- UI 不得直接调用 Git、数据库或 ProcessBuilder
- 所有 Git 命令通过 GitClient 执行
- 所有数据库操作通过 Repository 执行
- 耗时任务不得运行在 JavaFX Application Thread
- 不使用 Spring Boot
- 不使用 Lombok
- 不引入未经批准的大型依赖

## 安全约束

- 禁止通过 cmd /c 拼接 Git 命令
- 使用 ProcessBuilder 参数列表
- 不实现 Push、Force Push、Reset、Clean
- 不自动执行 Stash
- 不修改用户项目中的任何文件

## 构建与测试

- 构建：gradlew clean build
- 测试：gradlew test
- 每次修改后必须运行测试
- 完成任务前检查 git diff 和 git status

## 开发规则

- 一个任务只解决一个明确问题
- 优先添加测试
- 不修改任务范围外的文件
- 不擅自改变技术栈和架构

