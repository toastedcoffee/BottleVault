-- Negative-result cache for barcode lookups.
-- Avoids re-hitting external APIs for UPCs that no provider has data for.

CREATE TABLE barcode_lookup_miss (
    barcode         VARCHAR(50) NOT NULL PRIMARY KEY,
    last_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    attempts        INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_barcode_lookup_miss_last_attempt ON barcode_lookup_miss(last_attempt_at);
