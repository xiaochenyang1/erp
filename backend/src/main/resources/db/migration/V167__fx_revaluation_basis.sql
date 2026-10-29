-- Persist the exact period-end balances and rates used by a revaluation.
-- Legacy runs intentionally retain NULL: their old aggregate cannot establish
-- an equivalent basis and must be cancelled/revalued before closing an open period.
ALTER TABLE fin_fx_revaluation ADD COLUMN basis_hash VARCHAR(64) NULL;
ALTER TABLE fin_fx_revaluation ADD COLUMN basis_json LONGTEXT NULL;
