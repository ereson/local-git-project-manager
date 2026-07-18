# 本地 Git 项目管理器

面向 Windows 的本地桌面工具，用于统一扫描、搜索和管理 Git 项目，并通过常用 IDE 打开项目。MVP 采用 JavaFX、Java、SQLite 和本机 Git CLI。

已覆盖项目扫描与手动添加、卡片/表格视图、路径修复、IDE 检测与手动 IDE、Git 状态、Fetch、分支切换、Pull/冲突、系统托盘、主题、错误日志和软件更新检查。

## 开发环境

- JDK 25
- Windows 10 或更高版本

```powershell
.\gradlew.bat build
.\gradlew.bat :app:run
```

## 测试与发布包

```powershell
.\gradlew.bat clean build
.\gradlew.bat :app:portableZip
.\gradlew.bat :app:jpackageInstaller
```

- 免安装 ZIP：`app/build/distributions/LocalProjectManager-0.1.0-portable.zip`
- Windows MSI：`app/build/jpackage/installer/LocalProjectManager-0.1.0.msi`
- MSI 构建需要 WiX 3.14+，免安装包不需要 WiX。
- 免安装版数据保存在解压目录；安装版数据保存在 `%LOCALAPPDATA%\LocalProjectManager`，程序安装目录与数据目录相互隔离。
- 配置 JVM 参数 `-Dlpm.update.url=https://.../update.json` 后可启用更新检查；更新包只在用户确认后下载并校验 SHA-256。

## 项目文档

- [产品需求](docs/product-spec.md)
- [系统架构](docs/architecture.md)
- [数据库设计](docs/database.md)
- [开发计划](docs/development-plan.md)
- [Codex 开发说明](AGENTS.md)

实现范围、顺序和验收标准以 `docs/development-plan.md` 与 `docs/product-spec.md` 为准。
