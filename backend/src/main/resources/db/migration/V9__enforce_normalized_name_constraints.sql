-- SPDX-License-Identifier: AGPL-3.0-only
-- SPDX-FileCopyrightText: 2025-2026 toastedcoffee
--
-- Applies the constraints now that V8 has populated every row.
--
-- If this migration fails with a unique violation, the database contains two
-- rows whose names differ only by case, whitespace, punctuation or diacritics.
-- See DEPLOY.md for the query that lists them; they must be merged by hand
-- before this migration can succeed.

ALTER TABLE brands ALTER COLUMN normalized_name SET NOT NULL;
ALTER TABLE brands ADD CONSTRAINT uq_brands_normalized_name UNIQUE (normalized_name);

ALTER TABLE products ALTER COLUMN normalized_name SET NOT NULL;
ALTER TABLE products ADD CONSTRAINT uq_products_brand_normalized_name
    UNIQUE (brand_id, normalized_name);
