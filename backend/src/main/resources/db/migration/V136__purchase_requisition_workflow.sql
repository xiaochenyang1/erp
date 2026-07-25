-- V136: 采购请购接入审批工作流
ALTER TABLE pur_requisition ADD COLUMN approval_status VARCHAR(32) NOT NULL DEFAULT 'NOT_SUBMITTED';
UPDATE pur_requisition
SET approval_status = CASE
    WHEN status = 'DRAFT' THEN 'NOT_SUBMITTED'
    WHEN status = 'SUBMITTED' THEN 'IN_APPROVAL'
    WHEN status = 'APPROVED' THEN 'APPROVED'
    WHEN status = 'REJECTED' THEN 'REJECTED'
    WHEN status = 'CANCELLED' THEN 'CANCELLED'
    WHEN status = 'CONVERTED' THEN 'APPROVED'
    ELSE 'NOT_SUBMITTED'
END
WHERE approval_status IS NULL OR approval_status = 'NOT_SUBMITTED' OR approval_status = '';
CREATE INDEX idx_pur_requisition_company_book_approval
    ON pur_requisition (company_id, account_book_id, approval_status, status, deleted_flag);
