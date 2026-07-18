# 1. 技术选型结论

## 1.1 推荐方案

第一版推荐采用：

| 层级      | 技术                                       |
| ------- | ---------------------------------------- |
| 开发语言    | Java                                     |
| Java 版本 | 当前稳定的 JDK LTS                            |
| 桌面 UI   | JavaFX                                   |
| UI 布局   | FXML + CSS                               |
| 构建工具    | Gradle Kotlin DSL                        |
| 本地数据库   | SQLite                                   |
| 数据访问    | JDBC                                     |
| 数据迁移    | 内置 SQL Migration                         |
| Git 调用  | Java `ProcessBuilder` 调用本机 Git           |
| 并发任务    | Java ExecutorService / CompletableFuture |
| 日志      | SLF4J + Logback，仅记录错误                    |
| JSON    | Jackson                                  |
| 安装版打包   | `jpackage` 生成 EXE 或 MSI                  |
| 免安装版    | `jpackage --type app-image`              |
| 系统托盘    | Java AWT `SystemTray`                    |
| 软件更新    | 自定义版本检查和独立 Updater                       |
| 测试      | JUnit + Testcontainers 不需要，使用临时 Git 仓库   |

JavaFX 是 Java 官方生态中的桌面客户端 UI 平台；`jpackage` 可以生成包含运行时的应用镜像，并在 Windows 上输出 `exe`、`msi` 等格式；Java 标准库也直接提供了 Windows 系统托盘所需的 `SystemTray` 和 `TrayIcon` 能力。

---

# 2. 为什么优先选择 JavaFX

## 2.1 与现有能力匹配

你已经有多年 Java 开发经验。使用 JavaFX 时，以下核心能力都可以继续使用熟悉的 Java 编程方式实现：

- 文件和目录扫描。
    
- 多线程任务。
    
- 进程调用。
    
- SQLite 数据访问。
    
- 异常处理。
    
- 分层架构。
    
- 单元测试。
    
- Windows 安装包构建。
    

因此，第一版不需要同时学习 Rust、C# 或 Electron 的进程安全模型，可以把主要精力放在产品功能本身。

## 2.2 适合当前产品特点

本产品主要由以下能力组成：

```
目录扫描
Git 命令调用
本地数据库
IDE 启动
系统托盘
少量网络请求
Windows 文件系统集成
```

这些功能不需要浏览器渲染能力，也不需要复杂的网页内容展示，JavaFX 可以满足当前 MVP。

## 2.3 安装版和免安装版

使用 `jpackage` 可以输出两种交付形态：

```
安装版
├─ EXE
└─ MSI

免安装版
└─ app-image 应用目录
```

`jpackage` 会将应用所需的 Java 运行时一起打包，最终用户不需要提前单独安装 JDK。

---

# 3. 备选技术方案对比

## 3.1 方案对比

|   |   |   |   |
|---|---|---|---|
|对比项|JavaFX|Tauri 2|Electron|
|对现有 Java 经验的复用|高|低|中|
|MVP 上手成本|低|高|中|
|现代 Web UI 开发体验|中|高|高|
|安装包体积|中|较小|较大|
|Git 和文件系统能力|强|强|强|
|系统托盘|支持|支持|支持|
|官方更新能力|需自行组合|有官方插件|有官方 API|
|SQLite|JDBC|官方 SQL 插件|Node SQLite 库|
|Windows 打包|jpackage|Tauri Bundler|Electron Forge|
|本项目推荐度|最高|后续重构候选|不优先|

Tauri 2 使用操作系统 WebView，可以搭配任意 Web 前端框架，并提供系统托盘、Shell、SQLite 和 Updater 等官方能力；如果未来需要更轻量的安装包或扩展 macOS，可以将它作为后续候选方案。

Electron 内置 Chromium 和 Node.js，系统托盘和 Windows 更新都有官方 API，但需要认真处理渲染进程隔离、IPC 校验、沙箱和依赖更新等安全问题。对于当前纯本地工具，它不是最优先方案。

## 3.2 最终决策

MVP 采用：

> **JavaFX + Java + SQLite + 本机 Git CLI**

后续只有在出现以下情况时，才考虑迁移到 Tauri：

- 准备同时支持 Windows、macOS 和 Linux。
    
- 对安装包体积有更严格要求。
    
- 需要大量 Web UI 组件。
    
- 团队成员以 Web 前端或 Rust 开发者为主。
    
- JavaFX 的界面定制成本显著影响迭代速度。
    

---

# 4. 系统总体架构

## 4.1 架构图

```
┌────────────────────────────────────────────────────────────┐
│                         JavaFX UI                          │
│                                                            │
│  项目首页   项目详情   扫描页面   设置页面   通用弹窗      │
└────────────────────────────┬───────────────────────────────┘
                             │ ViewModel 调用
┌────────────────────────────▼───────────────────────────────┐
│                      Application Layer                     │
│                                                            │
│ ProjectApplicationService                                  │
│ ScanApplicationService                                     │
│ GitApplicationService                                      │
│ IdeApplicationService                                      │
│ SettingsApplicationService                                 │
│ UpdateApplicationService                                   │
└────────────────────────────┬───────────────────────────────┘
                             │
┌────────────────────────────▼───────────────────────────────┐
│                        Domain Layer                        │
│                                                            │
│ Project   GitStatus   IdeConfig   ScanRoot                 │
│ PullStrategy   GitOperation   PathStatus                   │
│                                                            │
│ 领域规则：项目去重、状态判断、Pull 校验、分支切换校验      │
└────────────────────────────┬───────────────────────────────┘
                             │
┌────────────────────────────▼───────────────────────────────┐
│                    Infrastructure Layer                    │
│                                                            │
│ SQLite Repository                                          │
│ Git CLI Adapter                                            │
│ File System Scanner                                        │
│ IDE Detector                                               │
│ Windows Integration                                        │
│ Tray Manager                                               │
│ Update Client                                              │
│ Error Logger                                               │
└────────────────────────────────────────────────────────────┘
```

---

# 5. 架构原则

## 5.1 UI 不直接执行系统操作

JavaFX Controller 或 ViewModel 不得直接：

- 调用 Git。
    
- 遍历文件目录。
    
- 写数据库。
    
- 启动 IDE。
    
- 下载更新。
    
- 修改配置文件。
    

UI 只负责：

- 获取用户输入。
    
- 调用应用服务。
    
- 展示任务状态。
    
- 展示成功或失败结果。
    

## 5.2 Git 命令集中封装

所有 Git 操作必须通过统一接口：

```
public interface GitClient {

    GitVersion getVersion();

    boolean isRepository(Path projectPath);

    GitStatus getLocalStatus(Path projectPath);

    RemoteStatus fetchRemoteStatus(Path projectPath);

    List<GitBranch> listBranches(Path projectPath);

    BranchSwitchResult switchBranch(
        Path projectPath,
        BranchSwitchCommand command
    );

    PullResult pull(
        Path projectPath,
        PullStrategy pullStrategy
    );
}
```

不得在 Controller、ViewModel 或 Repository 中直接拼接 Git 命令。

## 5.3 数据库与领域对象分离

数据库记录对象和领域对象分开：

```
ProjectRecord
    ↓ Mapper
Project
```

这样后续修改数据库字段时，不会直接影响所有 UI 和业务代码。

## 5.4 后台任务不阻塞 UI

以下操作必须在后台线程执行：

- 扫描目录。
    
- 刷新 Git 状态。
    
- Fetch。
    
- Pull。
    
- 切换分支。
    
- 检测 IDE。
    
- 检查更新。
    

所有 JavaFX UI 更新必须切换回 JavaFX Application Thread。

---

# 6. 推荐工程结构

```
local-project-manager/
├─ build.gradle.kts
├─ settings.gradle.kts
├─ README.md
├─ docs/
│  ├─ prd.md
│  ├─ architecture.md
│  └─ database.md
│
├─ app/
│  ├─ build.gradle.kts
│  └─ src/main/
│     ├─ java/com/example/projectmanager/
│     │
│     │  ├─ bootstrap/
│     │  │  ├─ ApplicationBootstrap.java
│     │  │  └─ DependencyContainer.java
│     │  │
│     │  ├─ ui/
│     │  │  ├─ common/
│     │  │  ├─ welcome/
│     │  │  ├─ scan/
│     │  │  ├─ projectlist/
│     │  │  ├─ projectdetail/
│     │  │  ├─ settings/
│     │  │  └─ dialog/
│     │  │
│     │  ├─ application/
│     │  │  ├─ project/
│     │  │  ├─ scan/
│     │  │  ├─ git/
│     │  │  ├─ ide/
│     │  │  ├─ settings/
│     │  │  └─ update/
│     │  │
│     │  ├─ domain/
│     │  │  ├─ project/
│     │  │  ├─ git/
│     │  │  ├─ ide/
│     │  │  ├─ scan/
│     │  │  └─ settings/
│     │  │
│     │  └─ infrastructure/
│     │     ├─ database/
│     │     ├─ git/
│     │     ├─ scanner/
│     │     ├─ ide/
│     │     ├─ windows/
│     │     ├─ update/
│     │     └─ logging/
│     │
│     └─ resources/
│        ├─ fxml/
│        ├─ css/
│        ├─ icons/
│        ├─ database/migrations/
│        └─ application.properties
│
├─ updater/
│  └─ 独立更新程序
│
└─ packaging/
   ├─ windows/
   ├─ installer/
   └─ portable/
```

---

# 7. UI 架构

## 7.1 推荐模式

采用：

> **FXML + Controller + ViewModel + Application Service**

调用关系：

```
FXML
  ↓
Controller
  ↓
ViewModel
  ↓
Application Service
  ↓
Domain / Infrastructure
```

## 7.2 Controller 职责

Controller 只负责：

- 绑定 FXML 控件。
    
- 将点击事件转发给 ViewModel。
    
- 控制页面跳转。
    
- 控制弹窗打开和关闭。
    

## 7.3 ViewModel 职责

ViewModel 负责：

- 页面状态。
    
- 加载状态。
    
- 表单状态。
    
- 用户选择。
    
- 错误信息。
    
- 调用 Application Service。
    
- 将领域数据转换为页面展示数据。
    

示例：

```
public final class ProjectListViewModel {

    private final ObservableList<ProjectListItem> projects;
    private final StringProperty keyword;
    private final BooleanProperty refreshing;
    private final ProjectApplicationService projectService;

    public void search(String keyword) {
        // 更新过滤后的页面数据
    }

    public CompletableFuture<Void> refreshAll() {
        // 调用应用服务，不直接执行 Git
        return null;
    }
}
```

---

# 8. 本地数据库设计

## 8.1 数据库位置

### 安装版

```
%LOCALAPPDATA%\LocalProjectManager\data\app.db
```

### 免安装版

```
软件目录\data\app.db
```

## 8.2 核心数据表

### projects

```
CREATE TABLE projects (
    id                    TEXT PRIMARY KEY,
    display_name          TEXT NOT NULL,
    directory_name        TEXT NOT NULL,
    project_path          TEXT NOT NULL UNIQUE,
    normalized_path       TEXT NOT NULL UNIQUE,
    scan_root_id          TEXT,
    default_ide_id        TEXT,
    pull_strategy         TEXT,
    last_opened_at        TEXT,
    path_status           TEXT NOT NULL,
    is_nested_repository  INTEGER NOT NULL DEFAULT 0,
    parent_repository     TEXT,
    created_at            TEXT NOT NULL,
    updated_at            TEXT NOT NULL
);
```

### git_status_cache

```
CREATE TABLE git_status_cache (
    project_id                 TEXT PRIMARY KEY,
    current_branch             TEXT,
    uncommitted_file_count     INTEGER NOT NULL DEFAULT 0,
    latest_commit_hash         TEXT,
    latest_commit_message      TEXT,
    latest_commit_time         TEXT,
    remote_url                 TEXT,
    upstream_branch            TEXT,
    ahead_count                INTEGER,
    behind_count               INTEGER,
    conflict_file_count        INTEGER NOT NULL DEFAULT 0,
    has_conflict               INTEGER NOT NULL DEFAULT 0,
    local_status_updated_at    TEXT,
    remote_status_updated_at   TEXT,
    refresh_status             TEXT,
    refresh_error              TEXT,
    FOREIGN KEY(project_id) REFERENCES projects(id)
        ON DELETE CASCADE
);
```

### ide_configs

```
CREATE TABLE ide_configs (
    id                TEXT PRIMARY KEY,
    name              TEXT NOT NULL,
    ide_type          TEXT NOT NULL,
    version           TEXT,
    executable_path   TEXT NOT NULL,
    launch_arguments  TEXT,
    icon_path         TEXT,
    source            TEXT NOT NULL,
    enabled           INTEGER NOT NULL DEFAULT 1,
    available         INTEGER NOT NULL DEFAULT 1,
    created_at        TEXT NOT NULL,
    updated_at        TEXT NOT NULL
);
```

### scan_roots

```
CREATE TABLE scan_roots (
    id                 TEXT PRIMARY KEY,
    root_path          TEXT NOT NULL,
    normalized_path    TEXT NOT NULL UNIQUE,
    enabled            INTEGER NOT NULL DEFAULT 1,
    last_scan_at       TEXT,
    last_scan_status   TEXT,
    last_scan_error    TEXT,
    created_at         TEXT NOT NULL
);
```

### ignore_rules

```
CREATE TABLE ignore_rules (
    id             TEXT PRIMARY KEY,
    pattern        TEXT NOT NULL,
    rule_type      TEXT NOT NULL,
    built_in       INTEGER NOT NULL DEFAULT 0,
    enabled        INTEGER NOT NULL DEFAULT 1,
    created_at     TEXT NOT NULL
);
```

### last_git_operations

```
CREATE TABLE last_git_operations (
    project_id     TEXT PRIMARY KEY,
    operation_type TEXT NOT NULL,
    started_at     TEXT NOT NULL,
    finished_at    TEXT,
    status         TEXT NOT NULL,
    summary        TEXT,
    raw_error      TEXT,
    FOREIGN KEY(project_id) REFERENCES projects(id)
        ON DELETE CASCADE
);
```

### application_settings

```
CREATE TABLE application_settings (
    setting_key    TEXT PRIMARY KEY,
    setting_value  TEXT,
    updated_at     TEXT NOT NULL
);
```

## 8.3 路径标准化

Windows 路径去重时，不能仅比较原始字符串。

建议保存两个字段：

```
project_path
    用户看到的真实路径

normalized_path
    用于比较和去重的标准化路径
```

标准化规则：

- 转换为绝对路径。
    
- 移除末尾多余分隔符。
    
- 统一目录分隔符。
    
- Windows 下忽略路径大小写差异。
    
- 尽可能解析 `.` 和 `..`。
    
- 不强制解析不存在路径的符号链接。
    

---

# 9. 数据库迁移

## 9.1 迁移目录

```
resources/database/migrations/
├─ V001__initial_schema.sql
├─ V002__add_project_pull_strategy.sql
└─ V003__add_update_settings.sql
```

## 9.2 版本表

```
CREATE TABLE schema_version (
    version       INTEGER PRIMARY KEY,
    description   TEXT NOT NULL,
    installed_at  TEXT NOT NULL
);
```

## 9.3 迁移规则

软件启动时：

1. 打开数据库。
    
2. 检查 `schema_version`。
    
3. 按版本顺序执行未执行的 SQL。
    
4. 每个迁移在事务中完成。
    
5. 迁移失败时停止启动数据库写入。
    
6. 保留原数据库文件。
    
7. 展示“数据库升级失败”提示。
    

---

# 10. Git 命令执行设计

## 10.1 基本原则

必须使用 `ProcessBuilder` 参数列表执行命令。

正确方式：

```
new ProcessBuilder(
    gitExecutable,
    "-C",
    projectPath.toString(),
    "status",
    "--porcelain=v2",
    "-z"
);
```

禁止：

```
new ProcessBuilder(
    "cmd.exe",
    "/c",
    "git -C " + projectPath + " status"
);
```

原因是字符串拼接容易出现：

- 路径空格错误。
    
- 特殊字符错误。
    
- 命令注入风险。
    
- 引号转义错误。
    
- 中文路径兼容问题。
    

## 10.2 统一结果对象

```
public record CommandResult(
    int exitCode,
    byte[] stdout,
    byte[] stderr,
    Duration duration,
    boolean timedOut
) {
    public boolean successful() {
        return !timedOut && exitCode == 0;
    }
}
```

## 10.3 命令执行器

```
public interface CommandExecutor {

    CommandResult execute(
        List<String> command,
        Path workingDirectory,
        Duration timeout,
        CancellationToken cancellationToken
    );
}
```

## 10.4 输出处理

必须同时读取：

- Standard Output。
    
- Standard Error。
    

不得先等待进程结束，再读取输出，否则在输出量较大时可能出现进程阻塞。

---

# 11. Git 状态命令设计

## 11.1 判断 Git 仓库

```
git -C <projectPath> rev-parse --is-inside-work-tree
```

## 11.2 获取当前分支

```
git -C <projectPath> branch --show-current
```

需要处理 Detached HEAD：

```
git -C <projectPath> rev-parse --short HEAD
```

页面展示：

```
Detached HEAD · a1b2c3d
```

## 11.3 获取未提交文件数量

```
git -C <projectPath> status --porcelain=v2 -z
```

Git 的 Porcelain 格式用于机器解析，`-z` 使用 NUL 分隔路径，可以避免空格、换行及特殊字符导致的解析问题。

统计规则：

- 普通修改记录算一个文件。
    
- 重命名记录算一个文件。
    
- 冲突记录算一个冲突文件。
    
- 未跟踪文件算未提交文件。
    
- 子模块状态第一版算一个修改项。
    

## 11.4 获取最近提交

```
git -C <projectPath> log -1 \
  --pretty=format:%H%x00%s%x00%cI
```

返回字段：

```
完整 Commit Hash
提交标题
ISO 8601 提交时间
```

## 11.5 获取远程地址

优先：

```
git -C <projectPath> remote get-url origin
```

不存在 `origin` 时：

```
git -C <projectPath> remote
```

第一版只展示一个主要远程仓库，不管理多个远程仓库。

## 11.6 获取上游分支

```
git -C <projectPath> rev-parse \
  --abbrev-ref \
  --symbolic-full-name @{upstream}
```

如果没有 upstream：

```
当前分支未设置远程跟踪分支
```

## 11.7 计算 Ahead / Behind

```
git -C <projectPath> rev-list \
  --left-right \
  --count \
  HEAD...@{upstream}
```

解析为：

```
左侧数量：本地独有提交
右侧数量：远程跟踪分支独有提交
```

---

# 12. Fetch 设计

## 12.1 命令

```
git -C <projectPath> fetch --prune
```

## 12.2 执行流程

```
用户点击检查远程更新
    ↓
检查项目路径
    ↓
检查 Git 可用
    ↓
检查是否已有 Git 操作
    ↓
执行 Fetch
    ↓
刷新远程分支
    ↓
重新计算 Ahead / Behind
    ↓
保存远程状态更新时间
```

## 12.3 超时

建议：

- 本地状态命令：10 秒。
    
- 分支列表：10 秒。
    
- Fetch：120 秒。
    
- Pull：180 秒。
    
- IDE 启动命令：10 秒内确认进程成功创建。
    

超时数值应集中配置，不散落在业务代码中。

---

# 13. 分支管理设计

## 13.1 本地分支列表

```
git -C <projectPath> for-each-ref \
  --format=%(refname:short) \
  refs/heads
```

## 13.2 远程分支列表

```
git -C <projectPath> for-each-ref \
  --format=%(refname:short) \
  refs/remotes
```

需要排除：

```
origin/HEAD
```

## 13.3 切换本地分支

```
git -C <projectPath> switch <branchName>
```

Git 在切换可能造成用户本地修改丢失时会中止操作；本软件不传递 `--discard-changes` 或其他强制参数。

## 13.4 创建远程跟踪分支

```
git -C <projectPath> switch \
  -c <localBranch> \
  --track <remoteBranch>
```

Git 官方文档支持使用 `git switch -c <branch> --track <remote>/<branch>` 创建并切换到远程跟踪分支。

## 13.5 分支名称校验

用户不能自由输入任意参数并直接交给 Git。

需要：

- 分支名称必须来自软件读取到的分支列表。
    
- 新本地分支名称由远程分支名称推导。
    
- 禁止分支名称被解释为命令选项。
    
- Git 参数中在必要位置加入 `--`。
    
- 不通过 Shell 执行命令。
    

---

# 14. Pull 设计

## 14.1 Rebase

```
git -C <projectPath> pull --rebase
```

## 14.2 Merge

```
git -C <projectPath> pull --no-rebase
```

## 14.3 遵循 Git 配置

```
git -C <projectPath> pull
```

Git 官方文档说明，`git pull` 会先 Fetch，再将远程分支集成到当前分支；`--rebase` 使用 Rebase，`--no-rebase` 使用 Merge。

## 14.4 Pull 前置校验

执行 Pull 前必须检查：

```
项目路径正常
Git 可用
当前不是 Detached HEAD
当前分支存在 upstream
当前没有正在执行的 Git 操作
工作区没有未提交修改
项目不存在未处理冲突
```

## 14.5 不自动 Stash

软件执行 Pull 时不得使用：

```
git pull --autostash
```

也不得自动执行：

```
git stash
git stash pop
```

---

# 15. 冲突检测

## 15.1 检查冲突文件

```
git -C <projectPath> diff \
  --name-only \
  --diff-filter=U \
  -z
```

## 15.2 检查 Rebase 状态

检查 Git 目录下是否存在：

```
rebase-merge
rebase-apply
```

Git 目录不能假设一定是：

```
<projectPath>\.git
```

必须先通过以下命令获取实际 Git 目录：

```
git -C <projectPath> rev-parse --git-dir
```

这样可以兼容：

- Git Worktree。
    
- 子模块。
    
- `.git` 文件而不是 `.git` 目录的场景。
    

## 15.3 冲突状态

```
public enum ConflictType {
    NONE,
    MERGE,
    REBASE,
    CHERRY_PICK,
    UNKNOWN
}
```

MVP 页面主要处理：

- Merge 冲突。
    
- Rebase 冲突。
    

其他状态显示通用提示：

```
当前仓库存在未完成的 Git 操作，请使用 IDE 或终端处理。
```

---

# 16. 目录扫描设计

## 16.1 扫描策略

使用深度优先或广度优先均可，建议采用显式队列：

```
待扫描目录队列
    ↓
读取目录内容
    ↓
应用忽略规则
    ↓
检查当前目录是否为 Git 仓库
    ↓
继续扫描允许进入的子目录
```

## 16.2 不因发现仓库而停止

即使当前目录已经发现 `.git`，也要继续检查子目录，因为需求明确支持嵌套仓库。

## 16.3 Git 仓库判断

需要识别：

```
.git 目录
.git 文件
```

`.git` 文件可能出现在 Worktree 或子模块场景。

## 16.4 默认忽略优化

扫描目录名称匹配以下规则时直接跳过：

```
node_modules
target
build
dist
out
vendor
.idea
.vscode
.gradle
.next
coverage
```

需要注意：

- 不进入忽略目录。
    
- 不删除已经导入的项目。
    
- 忽略规则只控制扫描行为。
    

## 16.5 扫描取消

扫描页面需要提供“取消扫描”。

取消后：

- 不再加入新的目录任务。
    
- 已经发现的项目可以继续展示。
    
- 不导入任何项目，除非用户明确确认。
    
- 释放目录句柄和后台线程。
    

---

# 17. 后台任务设计

## 17.1 任务类型

```
public enum BackgroundTaskType {
    DIRECTORY_SCAN,
    LOCAL_GIT_REFRESH,
    FETCH,
    PULL,
    SWITCH_BRANCH,
    IDE_DETECTION,
    UPDATE_CHECK
}
```

## 17.2 并发限制

建议初始设置：

|   |   |
|---|---|
|任务|最大并发|
|目录扫描|1|
|本地 Git 状态刷新|3|
|Fetch|每项目 1，全局最多 2|
|Pull|每项目 1|
|分支切换|每项目 1|
|IDE 检测|1|
|更新检查|1|

## 17.3 项目锁

每个项目需要一个操作锁。

以下操作不能同时执行：

```
Fetch
Pull
切换分支
刷新本地 Git 状态
```

读取缓存、打开 IDE、打开资源管理器不需要获得 Git 操作锁。

## 17.4 任务优先级

```
用户主动操作
    >
当前详情页状态刷新
    >
首页可见项目刷新
    >
其他项目后台刷新
```

---

# 18. IDE 检测设计

## 18.1 检测器接口

```
public interface IdeDetector {

    List<DetectedIde> detect();
}
```

实现：

```
JetBrainsToolboxIdeDetector
JetBrainsStandaloneIdeDetector
VisualStudioCodeDetector
ManualIdeDetector
```

## 18.2 JetBrains Toolbox

检查位置包括：

```
%LOCALAPPDATA%\JetBrains\Toolbox
%USERPROFILE%\AppData\Local\JetBrains\Toolbox
```

不要只固定查找单个版本目录，需要：

- 遍历 IDE 类型目录。
    
- 识别多个安装版本。
    
- 检查实际可执行文件。
    
- 按版本排序。
    
- 保留多个可用版本。
    

## 18.3 JetBrains 独立安装

检查常见路径：

```
%ProgramFiles%\JetBrains
%LOCALAPPDATA%\Programs
```

同时可以读取 Windows 注册表中的卸载记录。

## 18.4 VS Code

检测顺序：

```
用户手动配置
    ↓
系统 PATH 中的 code
    ↓
用户安装目录
    ↓
系统安装目录
    ↓
Windows 注册表
```

## 18.5 启动 IDE

```
new ProcessBuilder(
    ideExecutablePath.toString(),
    projectPath.toString()
).start();
```

如果用户配置了参数模板：

```
{projectPath}
```

软件负责将占位符替换为单个独立参数，不允许将整个参数字符串交给 `cmd.exe` 解析。

---

# 19. Windows 集成

## 19.1 打开资源管理器

```
new ProcessBuilder(
    "explorer.exe",
    projectPath.toString()
).start();
```

## 19.2 打开终端

优先顺序：

```
Windows Terminal
    ↓
PowerShell
    ↓
cmd
```

打开 Windows Terminal：

```
wt.exe -d <projectPath>
```

打开 PowerShell：

```
powershell.exe -NoExit -Command Set-Location -LiteralPath <path>
```

由于 PowerShell 参数涉及脚本解析，项目路径必须通过安全的参数和转义模块处理，不允许直接字符串拼接。

---

# 20. 系统托盘设计

Java 标准库的 `SystemTray` 可以在 Windows 通知区域添加图标、提示、弹出菜单和事件监听。

## 20.1 生命周期状态

```
public enum CloseBehavior {
    EXIT_APPLICATION,
    MINIMIZE_TO_TRAY
}
```

## 20.2 关闭窗口流程

```
用户关闭窗口
    ↓
读取 CloseBehavior
    ├─ EXIT_APPLICATION
    │      ↓
    │   停止任务并退出
    │
    └─ MINIMIZE_TO_TRAY
           ↓
       隐藏主窗口
       保持 Java 进程运行
```

需要设置 JavaFX：

```
Platform.setImplicitExit(false);
```

用户点击托盘“退出软件”时，再显式调用：

```
Platform.exit();
```

---

# 21. 软件更新设计

## 21.1 版本检查

客户端请求一个静态版本文件：

```
{
  "version": "1.1.0",
  "publishedAt": "2026-08-01T10:00:00Z",
  "releaseNotes": [
    "修复部分 JetBrains IDE 无法识别的问题",
    "优化 Git 项目扫描速度"
  ],
  "installerUrl": "...",
  "portableUrl": "...",
  "sha256": "..."
}
```

## 21.2 更新原则

- 启动时只检查版本。
    
- 不自动下载。
    
- 用户确认后才下载。
    
- 下载完成后校验 SHA-256。
    
- 校验失败不得执行安装。
    
- 安装版和免安装版使用不同更新包。
    
- 更新日志不得包含项目数据。
    

## 21.3 安装版更新

```
检查版本
    ↓
用户确认
    ↓
下载新版安装程序
    ↓
校验文件
    ↓
启动安装程序
    ↓
退出当前应用
```

## 21.4 免安装版更新

免安装版建议使用独立 Updater：

```
主程序下载更新包
    ↓
主程序启动 updater.exe
    ↓
主程序退出
    ↓
Updater 等待主进程结束
    ↓
替换应用文件
    ↓
保留 data / config / logs
    ↓
重新启动主程序
```

Updater 必须拒绝覆盖：

```
data/
config/
logs/
```

---

# 22. 错误处理设计

## 22.1 错误分类

```
public enum ErrorCategory {
    FILE_SYSTEM,
    GIT_NOT_FOUND,
    GIT_COMMAND,
    GIT_AUTHENTICATION,
    GIT_CONFLICT,
    IDE_NOT_FOUND,
    IDE_LAUNCH,
    DATABASE,
    CONFIGURATION,
    NETWORK,
    UPDATE,
    UNKNOWN
}
```

## 22.2 错误对象

```
public record ApplicationError(
    ErrorCategory category,
    String userMessage,
    String technicalMessage,
    Throwable cause,
    boolean retryable
) {
}
```

## 22.3 用户提示

页面默认展示：

```
无法检查远程更新。

可能原因：
Git 身份认证失败，或者当前网络无法访问远程仓库。

[复制详细信息] [在终端中打开] [关闭]
```

详细信息中可以包含：

- Git 命令名称。
    
- Exit Code。
    
- Standard Error。
    
- 执行时间。
    
- 软件版本。
    
- Git 版本。
    

不得包含：

- 文件内容。
    
- Token。
    
- 密码。
    
- SSH 私钥。
    
- Credential Helper 返回的敏感信息。
    

---

# 23. 安全要求

## 23.1 禁止 Shell 拼接

所有 Git 和 IDE 命令：

- 使用参数数组。
    
- 不使用 `cmd /c`。
    
- 不使用字符串拼接执行。
    
- 不接受未经验证的用户命令。
    

## 23.2 限制自定义 IDE 参数

手动添加 IDE 时，只允许配置：

```
固定参数
{projectPath} 占位符
```

不允许：

```
管道符
重定向
命令连接符
环境变量脚本
PowerShell 表达式
```

## 23.3 更新包校验

更新包至少需要：

- HTTPS 下载。
    
- SHA-256 校验。
    
- 发布版本号校验。
    
- 文件大小合理性检查。
    
- 防止目录穿越的解压检查。
    

正式公开发布时，安装程序和可执行文件应增加 Windows 代码签名。

---

# 24. 测试架构

## 24.1 单元测试

重点测试：

- Windows 路径标准化。
    
- 项目去重。
    
- 忽略规则匹配。
    
- Git Porcelain 输出解析。
    
- 分支列表解析。
    
- Ahead / Behind 解析。
    
- Git 错误分类。
    
- Pull 前置校验。
    
- IDE 参数占位符替换。
    
- 数据库迁移。
    

## 24.2 集成测试

测试时在临时目录中创建真实 Git 仓库：

```
创建临时目录
    ↓
git init
    ↓
创建测试文件
    ↓
git add / commit
    ↓
运行 GitClient
    ↓
验证返回状态
    ↓
删除临时目录
```

需要覆盖：

- 干净仓库。
    
- 未跟踪文件。
    
- 已修改文件。
    
- 删除文件。
    
- 重命名文件。
    
- Detached HEAD。
    
- 无 upstream。
    
- 本地领先。
    
- 本地落后。
    
- 冲突状态。
    
- 中文路径。
    
- 路径包含空格。
    

## 24.3 UI 测试

MVP 阶段重点进行手动 UI 测试：

- 页面导航。
    
- 主题切换。
    
- 扫描进度。
    
- 大量项目列表。
    
- 弹窗确认。
    
- 托盘恢复窗口。
    
- 路径失效状态。
    
- Git 操作期间按钮禁用。
    

---

# 25. MVP 开发迭代计划

## Sprint 0：工程准备

完成：

- Gradle 工程。
    
- JavaFX 启动。
    
- FXML 页面导航。
    
- SQLite 初始化。
    
- 数据库迁移。
    
- 统一错误模型。
    
- 基础主题。
    

交付结果：

```
应用可启动
页面可切换
数据库可读写
```

## Sprint 1：项目导入闭环

完成：

- 扫描目录配置。
    
- 目录递归扫描。
    
- 忽略规则。
    
- Git 仓库识别。
    
- 嵌套仓库识别。
    
- 扫描结果确认。
    
- 项目导入。
    
- 卡片列表。
    
- 搜索。
    

交付结果：

```
扫描项目
    ↓
导入项目
    ↓
搜索项目
    ↓
进入项目详情
```

## Sprint 2：IDE 打开闭环

完成：

- JetBrains IDE 检测。
    
- VS Code 检测。
    
- 手动添加 IDE。
    
- 首次 IDE 选择。
    
- 默认 IDE。
    
- 打开项目。
    
- 资源管理器和终端。
    
- 最近打开排序。
    

交付结果：

```
搜索项目
    ↓
选择 IDE
    ↓
成功打开项目
```

这是第一个真正可用版本。

## Sprint 3：本地 Git 状态

完成：

- Git 检测。
    
- 当前分支。
    
- 未提交数量。
    
- 最近提交。
    
- 远程地址。
    
- 本地状态缓存。
    
- 后台刷新。
    
- 路径失效。
    

交付结果：

```
打开软件后立即看到项目和缓存状态
后台逐步更新真实 Git 状态
```

## Sprint 4：Fetch 和分支

完成：

- Fetch。
    
- Ahead / Behind。
    
- 本地和远程分支。
    
- 分支搜索。
    
- 本地分支切换。
    
- 创建远程跟踪分支。
    
- Git 操作锁。
    
- 最近操作结果。
    

## Sprint 5：Pull 和冲突

完成：

- Pull 策略。
    
- Pull 前检查。
    
- Pull 确认。
    
- Rebase。
    
- Merge。
    
- 冲突检测。
    
- 冲突状态页。
    
- IDE 和终端引导。
    

## Sprint 6：发布能力

完成：

- 系统托盘。
    
- 错误日志。
    
- 更新检查。
    
- 安装版。
    
- 免安装版。
    
- 数据升级测试。
    
- 发布文档。
    

---

# 26. 第一批开发任务

建议首先创建以下任务：

|   |   |   |
|---|---|---|
|编号|任务|优先级|
|LPM-001|初始化 JavaFX Gradle 工程|P0|
|LPM-002|实现应用启动和单主窗口|P0|
|LPM-003|实现页面导航容器|P0|
|LPM-004|初始化 SQLite 数据库|P0|
|LPM-005|实现 SQL Migration|P0|
|LPM-006|定义 Project 领域对象|P0|
|LPM-007|定义 ProjectRepository|P0|
|LPM-008|实现扫描目录配置|P0|
|LPM-009|实现默认忽略规则|P0|
|LPM-010|实现 Git 仓库扫描器|P0|
|LPM-011|实现嵌套仓库识别|P0|
|LPM-012|实现扫描结果确认页|P0|
|LPM-013|实现项目导入和去重|P0|
|LPM-014|实现项目卡片列表|P0|
|LPM-015|实现项目搜索|P0|
|LPM-016|实现项目详情基础页面|P0|
|LPM-017|实现 VS Code 检测|P0|
|LPM-018|实现 JetBrains IDE 检测|P0|
|LPM-019|实现项目默认 IDE|P0|
|LPM-020|实现 IDE 打开项目|P0|

---

# 27. 首个可发布里程碑

第一个内部测试版本不需要立即完成 Fetch、Pull 和系统托盘。

首个里程碑范围：

```
扫描目录
    +
导入 Git 项目
    +
项目搜索
    +
项目详情
    +
IDE 检测
    +
使用 IDE 打开项目
    +
最近打开排序
```

该版本完成后，就已经解决了产品最初的问题：

> 不再需要进入多个文件目录寻找 Git 项目，可以在统一软件中搜索项目，并使用对应 IDE 打开。