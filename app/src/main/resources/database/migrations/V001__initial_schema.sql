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

CREATE TABLE ignore_rules (
    id             TEXT PRIMARY KEY,
    pattern        TEXT NOT NULL,
    rule_type      TEXT NOT NULL,
    built_in       INTEGER NOT NULL DEFAULT 0,
    enabled        INTEGER NOT NULL DEFAULT 1,
    created_at     TEXT NOT NULL
);

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

CREATE TABLE application_settings (
    setting_key    TEXT PRIMARY KEY,
    setting_value  TEXT,
    updated_at     TEXT NOT NULL
);

