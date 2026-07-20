DROP INDEX uk_inv_balance_company_warehouse_product ON inv_balance;
CREATE UNIQUE INDEX uk_inv_balance_company_book_warehouse_product
    ON inv_balance (company_id, account_book_id, warehouse_id, product_id);

DROP INDEX idx_inv_balance_company_product ON inv_balance;
CREATE INDEX idx_inv_balance_company_book_product
    ON inv_balance (company_id, account_book_id, product_id);

DROP INDEX idx_inv_txn_company_biz_no ON inv_txn;
CREATE INDEX idx_inv_txn_company_book_biz_no
    ON inv_txn (company_id, account_book_id, biz_no);

DROP INDEX idx_inv_txn_company_warehouse_product ON inv_txn;
CREATE INDEX idx_inv_txn_company_book_warehouse_product
    ON inv_txn (company_id, account_book_id, warehouse_id, product_id);

DROP INDEX idx_inv_txn_company_occurred_time ON inv_txn;
CREATE INDEX idx_inv_txn_company_book_occurred_time
    ON inv_txn (company_id, account_book_id, occurred_time);

DROP INDEX uk_inv_txn_company_biz_line_direction_lot_key ON inv_txn;
CREATE UNIQUE INDEX uk_inv_txn_company_book_biz_line_direction_lot_key
    ON inv_txn (company_id, account_book_id, biz_type, biz_line_id, direction, lot_key);

DROP INDEX idx_inv_txn_company_biz_line_direction ON inv_txn;
CREATE INDEX idx_inv_txn_company_book_biz_line_direction
    ON inv_txn (company_id, account_book_id, biz_type, biz_line_id, direction);

DROP INDEX idx_inv_txn_company_lot ON inv_txn;
CREATE INDEX idx_inv_txn_company_book_lot
    ON inv_txn (company_id, account_book_id, warehouse_id, product_id, lot_no);

DROP INDEX idx_sal_order_company_warehouse_id ON sal_order;
CREATE INDEX idx_sal_order_company_book_warehouse_id
    ON sal_order (company_id, account_book_id, warehouse_id);

DROP INDEX uk_inv_reservation_company_source_line ON inv_reservation;
CREATE UNIQUE INDEX uk_inv_reservation_company_book_source_line
    ON inv_reservation (company_id, account_book_id, source_type, source_line_id);

DROP INDEX idx_inv_reservation_company_source ON inv_reservation;
CREATE INDEX idx_inv_reservation_company_book_source
    ON inv_reservation (company_id, account_book_id, source_type, source_id);

DROP INDEX idx_inv_reservation_company_balance ON inv_reservation;
CREATE INDEX idx_inv_reservation_company_book_balance
    ON inv_reservation (company_id, account_book_id, warehouse_id, product_id, status);

DROP INDEX idx_inv_reservation_event_company_reservation ON inv_reservation_event;
CREATE INDEX idx_inv_reservation_event_company_book_reservation
    ON inv_reservation_event (company_id, account_book_id, reservation_id, created_time);

DROP INDEX idx_inv_reservation_event_company_source ON inv_reservation_event;
CREATE INDEX idx_inv_reservation_event_company_book_source
    ON inv_reservation_event (company_id, account_book_id, source_type, source_id);

DROP INDEX idx_inv_reservation_event_company_balance ON inv_reservation_event;
CREATE INDEX idx_inv_reservation_event_company_book_balance
    ON inv_reservation_event (company_id, account_book_id, warehouse_id, product_id, created_time);
