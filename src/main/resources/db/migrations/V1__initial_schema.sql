-- V1: initial schema
CREATE TABLE IF NOT EXISTS schema_version (
    version     INTEGER PRIMARY KEY,
    applied_at  TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS runs (
    run_id       TEXT PRIMARY KEY,
    agent_id     TEXT NOT NULL,
    status       TEXT NOT NULL,
    heartbeat_ms INTEGER NOT NULL,
    checkpoint_step_index   INTEGER,
    checkpoint_context      TEXT,
    checkpoint_artifact_refs TEXT  -- JSON array stored as text
);

CREATE INDEX IF NOT EXISTS idx_runs_agent_status ON runs(agent_id, status);

CREATE TABLE IF NOT EXISTS steps (
    step_id   TEXT PRIMARY KEY,
    run_id    TEXT NOT NULL REFERENCES runs(run_id),
    step_idx  INTEGER NOT NULL,
    tool_name TEXT NOT NULL,
    status    TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_steps_run ON steps(run_id);

CREATE TABLE IF NOT EXISTS idempotency_entries (
    idem_key    TEXT PRIMARY KEY,
    status      TEXT NOT NULL,
    result_json TEXT,
    created_at  INTEGER NOT NULL,
    expires_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS approval_requests (
    approval_id    TEXT PRIMARY KEY,
    run_id         TEXT NOT NULL REFERENCES runs(run_id),
    step_id        TEXT NOT NULL,
    risk_score     INTEGER NOT NULL,
    impact_preview TEXT NOT NULL,
    status         TEXT NOT NULL DEFAULT 'PENDING',
    decided_at     INTEGER
);
