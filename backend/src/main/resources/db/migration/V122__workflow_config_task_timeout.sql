ALTER TABLE wf_approval_config
    ADD COLUMN task_timeout_hours INT NOT NULL DEFAULT 24 AFTER status;

UPDATE wf_approval_config
SET task_timeout_hours = 24
WHERE task_timeout_hours IS NULL OR task_timeout_hours < 1;
