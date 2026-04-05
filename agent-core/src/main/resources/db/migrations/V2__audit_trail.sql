-- V2: audit trail
CREATE TABLE IF NOT EXISTS audit_events (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    event_type    TEXT NOT NULL,
    run_id        TEXT NOT NULL,
    step_id       TEXT,
    tool_name     TEXT,
    args_json     TEXT,
    result_json   TEXT,
    error_code    TEXT,
    detail        TEXT,
    timestamp_ms  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_run ON audit_events(run_id);
CREATE INDEX IF NOT EXISTS idx_audit_type ON audit_events(event_type);
