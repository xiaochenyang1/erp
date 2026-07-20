ALTER TABLE wf_approval_task
    ADD COLUMN approval_node_id BIGINT;

ALTER TABLE wf_approval_record
    ADD COLUMN approval_node_id BIGINT;

CREATE INDEX idx_wf_task_company_book_instance_node_status
    ON wf_approval_task (company_id, account_book_id, instance_id, approval_node_id, status);

CREATE INDEX idx_wf_record_company_book_instance_node
    ON wf_approval_record (company_id, account_book_id, instance_id, approval_node_id);
