ALTER TABLE case_deadline_reminders
    ADD COLUMN task_id UUID REFERENCES case_tasks (id) ON DELETE CASCADE;

ALTER TABLE case_deadline_reminders
    DROP CONSTRAINT uq_case_deadline_reminder;

CREATE UNIQUE INDEX uq_case_deadline_reminder ON case_deadline_reminders (
    case_id,
    deadline_type,
    deadline_date,
    threshold_days,
    COALESCE(task_id, '00000000-0000-0000-0000-000000000000')
);
