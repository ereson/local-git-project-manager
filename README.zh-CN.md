# 本地 Git 项目管理器

[English](README.md) | [简体中文](README.zh-CN.md)

一款本地优先的 Windows 桌面应用，用于发现、整理、打开并安全同步电脑中的 Git 仓库。

应用基于 JavaFX 开发，项目元数据全部保存在本地 SQLite 数据库中，无需账号，也不会上传仓库信息。

## 功能特性

### 项目管理

- 扫描一个或多个目录中的 Git 仓库。
- 手动添加单个 Git 仓库。
- 识别嵌套仓库并避免重复导入。
- 按显示名称、目录名称或完整路径搜索。
- 支持卡片与表格两种视图。
- 可重命名、重新定位或移除项目记录，不修改项目文件。
- 识别路径失效和无权限状态。
- 配置默认 IDE 后，可从项目卡片直接启动项目。

### IDE 集成

- 自动检测 Visual Studio Code。
- 自动检测 JetBrains Toolbox 和独立安装的 JetBrains IDE。
- 保留同一 IDE 的多个已安装版本。
- 支持手动添加其他 IDE。
- 每个项目可配置不同的默认 IDE。
- 可使用默认 IDE 打开，或临时选择其他 IDE。
- 可在文件资源管理器或终端中打开项目目录。

### Git 操作

- 展示当前分支、未提交文件数量、最近提交、远程地址及缓存的 Ahead/Behind。
- 后台刷新本地状态，不阻塞 JavaFX 界面线程。
- 只在用户主动操作时执行 Fetch。
- 查看和搜索本地、远程分支。
- 切换本地分支，或从远程分支创建本地跟踪分支。
- 支持 Rebase、Merge 和遵循 Git 配置三种 Pull 策略。
- 识别冲突，并引导用户使用 IDE 或终端处理。
- 每个项目保留最近一次 Git 操作结果。

### 桌面体验

- 中文应用界面。
- 支持浅色、深色和跟随 Windows 系统主题，包括 JavaFX 弹框。
- 可配置关闭行为并支持 Windows 系统托盘。
- 错误日志只保存在本地。
- 可选的软件更新检查，打开更新包前校验 SHA-256。
- 支持免安装 ZIP 和当前用户 MSI 安装包。

## 安全边界

本项目有意限制 Git 操作范围，**不提供**：

- Commit 或 Push
- Force Push
- Reset 或 Clean
- 自动 Stash
- 文件 Checkout 或丢弃修改
- 删除分支
- 冲突解决
- Token、密码、凭据或 SSH 私钥管理

Git 命令使用 `ProcessBuilder` 参数列表执行，不通过 Shell 拼接。工作区存在未提交修改时默认阻止 Pull；从软件中移除项目时不会删除本地仓库文件。

## 环境要求

- Windows 10 或更高版本
- 开发和打包需要 JDK 25
- Git 状态、Fetch、Pull 和分支操作需要本机 Git
- 仅构建 MSI 时需要 WiX Toolset 3.14 或更高版本

发布包包含 Java 运行时，最终用户不需要单独安装 JDK。

## 从源码启动

在项目根目录打开 PowerShell，并将 `JAVA_HOME` 指向 JDK 25：

```powershell
$env:JAVA_HOME="C:\path\to\jdk-25"
$env:Path="$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat :app:run
```

首次使用流程：

1. 选择一个或多个扫描目录，也可以手动添加 Git 项目。
2. 检查扫描结果，并导入需要管理的项目。
3. 进入项目详情页，为项目选择默认 IDE。
4. 后续可直接使用项目卡片上的“启动项目”按钮。

## 构建与测试

```powershell
# 运行单元测试和集成测试
.\gradlew.bat test

# 运行测试并校验 FXML
.\gradlew.bat check

# 清理并完整构建
.\gradlew.bat clean build
```

Git 集成测试只会在临时目录创建仓库，不会修改软件中管理的用户仓库。

## 构建发布包

```powershell
# 免安装 ZIP
.\gradlew.bat :app:portableZip

# Windows MSI（需要 WiX）
.\gradlew.bat :app:jpackageInstaller
```

输出文件：

| 发布形式 | 输出路径 |
| --- | --- |
| 免安装 ZIP | `app/build/distributions/LocalProjectManager-0.1.0-portable.zip` |
| MSI 安装包 | `app/build/jpackage/installer/LocalProjectManager-0.1.0.msi` |

## 本地数据

| 发布形式 | 数据目录 |
| --- | --- |
| 安装版 | `%LOCALAPPDATA%\LocalProjectManager` |
| 免安装版 | 可写的免安装程序目录 |

运行数据结构：

```text
LocalProjectManager/
├─ data/app.db
├─ logs/
└─ updates/
```

SQLite 数据库保存项目记录、扫描目录、IDE 配置、应用设置、Git 状态缓存和最近一次 Git 操作。卸载软件或移除应用内的项目记录不会删除被管理的 Git 仓库。

## 软件更新配置

未配置清单地址时，软件更新检查默认不可用。通过以下 JVM 参数启用：

```text
-Dlpm.update.url=https://example.com/update.json
```

更新清单格式：

```json
{
  "version": "0.2.0",
  "publishedAt": "2026-08-01T10:00:00Z",
  "releaseNotes": ["在这里填写更新说明"],
  "installerUrl": "https://example.com/LocalProjectManager-0.2.0.msi",
  "portableUrl": "https://example.com/LocalProjectManager-0.2.0-portable.zip",
  "sha256": "0000000000000000000000000000000000000000000000000000000000000000"
}
```

启用后，应用可以静默检查版本；下载和打开更新包始终需要用户确认。

## 架构

```text
JavaFX FXML/CSS 界面
        ↓
Controller 与 ViewModel
        ↓
Application Service
        ↓
Domain Model
        ↓
SQLite / Git CLI / 文件系统 / IDE / Windows 集成
```

主要约束：

- Controller 只负责控件绑定、导航和弹框。
- Application Service 编排用例。
- 所有数据库访问通过 Repository。
- 所有 Git 命令通过 `GitClient`。
- 耗时操作不得运行在 JavaFX Application Thread。
- 同一项目的 Git 操作串行执行。

## 技术栈

| 范围 | 技术 |
| --- | --- |
| 开发语言 | Java 25 |
| 桌面界面 | JavaFX 26、FXML、CSS |
| 构建工具 | Gradle Kotlin DSL |
| 本地存储 | SQLite、JDBC |
| JSON | Jackson |
| 日志 | SLF4J、Logback |
| 测试 | JUnit 6、临时 Git 仓库 |
| 打包 | `jpackage` |

## 项目结构

```text
app/src/main/java/com/localprojectmanager/
├─ bootstrap/       应用启动与依赖装配
├─ ui/              JavaFX Controller 和 ViewModel
├─ application/     项目、扫描、Git、IDE、设置和更新用例
├─ domain/          领域对象与业务规则
└─ infrastructure/  SQLite、Git、IDE 检测和 Windows 适配

app/src/main/resources/
├─ fxml/            JavaFX 页面
├─ css/             深色和浅色主题
└─ database/        版本化 SQL Migration
```

## 常见问题

### Gradle 找不到 Java 25

安装 JDK 25，更新 `JAVA_HOME`，然后检查 Java 和 Gradle 工具链：

```powershell
java -version
.\gradlew.bat -q javaToolchains
```

### Git 功能不可用

确认 Git 已安装并位于 `PATH` 中：

```powershell
git --version
```

未安装 Git 时仍可导入项目并使用 IDE 打开，但 Git 状态、Fetch、Pull 和分支操作会被禁用。

### 免安装版无法保存设置

请将解压后的程序移动到可写目录，不要放在 `C:\Program Files` 中运行。

### MSI 构建失败

安装 WiX Toolset 3.14 或更高版本。构建免安装 ZIP 不需要 WiX。

## 项目文档

- [产品需求](docs/product-spec.md)
- [系统架构](docs/architecture.md)
- [数据库设计](docs/database.md)
- [开发计划](docs/development-plan.md)
- [Codex 开发说明](AGENTS.md)

## 许可证

本项目使用 [MIT License](LICENSE)。
