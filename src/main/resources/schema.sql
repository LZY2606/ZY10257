-- 反例套索查验台 SQLite 结构（幂等建表）
CREATE TABLE IF NOT EXISTS models (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS states (
    model_id TEXT NOT NULL,
    state_id TEXT NOT NULL,
    ord INTEGER NOT NULL,
    variables TEXT NOT NULL,
    accept_sets TEXT NOT NULL,
    fair_sets TEXT NOT NULL,
    PRIMARY KEY (model_id, state_id)
);

CREATE TABLE IF NOT EXISTS transitions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_id TEXT NOT NULL,
    ord INTEGER NOT NULL,
    source TEXT NOT NULL,
    target TEXT NOT NULL,
    guard TEXT NOT NULL,
    description TEXT
);

CREATE TABLE IF NOT EXISTS counterexamples (
    id TEXT PRIMARY KEY,
    model_id TEXT NOT NULL,
    name TEXT NOT NULL,
    property_kind TEXT NOT NULL DEFAULT 'liveness',
    description TEXT
);

CREATE TABLE IF NOT EXISTS ce_traces (
    ce_id TEXT NOT NULL,
    pos INTEGER NOT NULL,
    state_id TEXT NOT NULL,
    PRIMARY KEY (ce_id, pos)
);

CREATE TABLE IF NOT EXISTS ce_entries (
    ce_id TEXT NOT NULL,
    state_id TEXT NOT NULL,
    legal INTEGER NOT NULL DEFAULT 1,
    note TEXT,
    PRIMARY KEY (ce_id, state_id)
);

CREATE TABLE IF NOT EXISTS run_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at TEXT NOT NULL,
    action TEXT NOT NULL,
    detail TEXT
);
