ALTER TABLE fin_payment
    ADD COLUMN cancel_reason VARCHAR(255);

ALTER TABLE fin_payment
    ADD COLUMN cancelled_by BIGINT;

ALTER TABLE fin_payment
    ADD COLUMN cancelled_time TIMESTAMP;

ALTER TABLE fin_receipt
    ADD COLUMN cancel_reason VARCHAR(255);

ALTER TABLE fin_receipt
    ADD COLUMN cancelled_by BIGINT;

ALTER TABLE fin_receipt
    ADD COLUMN cancelled_time TIMESTAMP;
