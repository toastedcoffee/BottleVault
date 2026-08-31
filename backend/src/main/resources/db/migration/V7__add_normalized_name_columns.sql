-- SPDX-License-Identifier: AGPL-3.0-only
-- SPDX-FileCopyrightText: 2025-2026 toastedcoffee
--
-- Phase 1 of the brand and product data quality work.
--
-- Adds the normalized matching key alongside the human-facing name, and renames
-- `name` to `display_name` now that the two are distinct. Columns are nullable
-- here; V8 backfills them in Kotlin and V9 applies the constraints. They are
-- split because diacritic folding has no portable SQL form -- `unaccent` is
-- Postgres-only and would break the H2 dev profile -- so the backfill runs
-- through the same NameNormalizer the application uses at runtime.
--
-- Parses under both PostgreSQL and H2 in PostgreSQL mode.

ALTER TABLE brands RENAME COLUMN name TO display_name;
ALTER TABLE brands ADD COLUMN normalized_name VARCHAR(255);
ALTER TABLE brands ADD COLUMN aliases VARCHAR(500);
ALTER TABLE brands ADD COLUMN normalized_aliases VARCHAR(500);

ALTER TABLE products RENAME COLUMN name TO display_name;
ALTER TABLE products ADD COLUMN normalized_name VARCHAR(255);
