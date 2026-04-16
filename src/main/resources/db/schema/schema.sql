PRAGMA journal_mode = WAL;
PRAGMA synchronous = NORMAL;
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;

CREATE TABLE IF NOT EXISTS app_instance (
    id TEXT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    name TEXT NOT NULL,
    app_type TEXT NOT NULL,
    adapter_type TEXT NOT NULL,
    runtime_env TEXT NOT NULL,
    launch_mode TEXT NOT NULL,
    executable_path TEXT,
    launch_command TEXT NOT NULL,
    args_json TEXT,
    workdir TEXT,
    env_json TEXT,
    path_mapping_rule TEXT,
    enabled INTEGER NOT NULL DEFAULT 1,
    auto_restart INTEGER NOT NULL DEFAULT 0,
    remark TEXT,
    last_start_at TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_app_instance_type_enabled
    ON app_instance (app_type, enabled);

CREATE TABLE IF NOT EXISTS session (
    id TEXT PRIMARY KEY,
    app_instance_id TEXT NOT NULL,
    title TEXT NOT NULL,
    project_path TEXT,
    project_path_linux TEXT,
    status TEXT NOT NULL,
    interaction_mode TEXT NOT NULL,
    pid INTEGER,
    started_at TEXT,
    ended_at TEXT,
    last_message_at TEXT,
    exit_code INTEGER,
    exit_reason TEXT,
    raw_log_path TEXT,
    summary TEXT,
    tags_json TEXT,
    extra_json TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (app_instance_id) REFERENCES app_instance(id)
);

CREATE INDEX IF NOT EXISTS idx_session_instance_status
    ON session (app_instance_id, status);

CREATE INDEX IF NOT EXISTS idx_session_last_message_at
    ON session (last_message_at DESC);

CREATE TABLE IF NOT EXISTS message (
    id TEXT PRIMARY KEY,
    session_id TEXT NOT NULL,
    seq_no INTEGER NOT NULL,
    role TEXT NOT NULL,
    message_type TEXT NOT NULL,
    content_text TEXT,
    content_json TEXT,
    raw_chunk TEXT,
    parent_id TEXT,
    is_structured INTEGER NOT NULL DEFAULT 0,
    source_adapter TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (session_id) REFERENCES session(id),
    FOREIGN KEY (parent_id) REFERENCES message(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_message_session_seq
    ON message (session_id, seq_no);

CREATE INDEX IF NOT EXISTS idx_message_session_created
    ON message (session_id, created_at);

CREATE TABLE IF NOT EXISTS operation_log (
    id TEXT PRIMARY KEY,
    target_type TEXT NOT NULL,
    target_id TEXT,
    action TEXT NOT NULL,
    result TEXT NOT NULL,
    operator_name TEXT NOT NULL DEFAULT 'local-user',
    detail_json TEXT,
    created_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_operation_log_target
    ON operation_log (target_type, target_id, created_at DESC);

CREATE TABLE IF NOT EXISTS config (
    id TEXT PRIMARY KEY,
    config_group TEXT NOT NULL,
    config_key TEXT NOT NULL,
    value_type TEXT NOT NULL,
    value_text TEXT,
    value_json TEXT,
    secret_ref TEXT,
    remark TEXT,
    updated_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_config_group_key
    ON config (config_group, config_key);

CREATE VIRTUAL TABLE IF NOT EXISTS message_fts
USING fts5(
    message_id UNINDEXED,
    session_id UNINDEXED,
    role,
    message_type,
    content_text,
    raw_chunk,
    tokenize = 'unicode61'
);

INSERT OR IGNORE INTO config (
    id,
    config_group,
    config_key,
    value_type,
    value_text,
    updated_at
) VALUES
    ('cfg_runtime_default_project_path', 'runtime', 'defaultProjectPath', 'string', '', CURRENT_TIMESTAMP),
    ('cfg_runtime_default_shell', 'runtime', 'defaultShell', 'string', 'powershell', CURRENT_TIMESTAMP),
    ('cfg_storage_session_log_retention_days', 'storage', 'sessionLogRetentionDays', 'number', '30', CURRENT_TIMESTAMP),
    ('cfg_ui_default_terminal_mode', 'ui', 'defaultTerminalMode', 'string', 'raw', CURRENT_TIMESTAMP),
    ('cfg_session_auto_reconnect', 'session', 'autoReconnect', 'boolean', 'false', CURRENT_TIMESTAMP);
