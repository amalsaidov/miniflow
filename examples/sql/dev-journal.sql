-- Table for the "dev-journal" and "standup-note" example workflows.
-- Run once in the database behind your "miniflow-db" credential.

-- PostgreSQL
CREATE TABLE IF NOT EXISTS dev_journal (
    day            DATE PRIMARY KEY,
    summary        TEXT        NOT NULL,
    commits        INTEGER     NOT NULL DEFAULT 0,
    pull_requests  INTEGER     NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- H2 note: H2 does not support "ON CONFLICT ... DO UPDATE". If you use H2, change the
-- query of the "save" node in dev-journal to:
--   MERGE INTO dev_journal (day, summary, commits, pull_requests, created_at) KEY (day)
--   VALUES (CAST(:day AS DATE), :summary, :commits, :prs, CURRENT_TIMESTAMP)

-- Handy queries
-- Last 10 entries:
--   SELECT day, commits, pull_requests, summary FROM dev_journal ORDER BY day DESC LIMIT 10;
-- Busiest days this month:
--   SELECT day, commits + pull_requests AS activity FROM dev_journal
--   WHERE day >= DATE_TRUNC('month', CURRENT_DATE) ORDER BY activity DESC;
