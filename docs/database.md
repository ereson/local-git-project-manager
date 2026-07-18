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
