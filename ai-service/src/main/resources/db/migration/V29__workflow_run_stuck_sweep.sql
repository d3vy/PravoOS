CREATE INDEX idx_workflow_runs_running_started
    ON workflow_runs (started_at)
    WHERE status = 'RUNNING';
