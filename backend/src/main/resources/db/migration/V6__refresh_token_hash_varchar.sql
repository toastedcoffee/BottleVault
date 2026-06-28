-- Align token_hash with the JPA mapping.
--
-- V5 created token_hash as CHAR(64), which Postgres stores as bpchar
-- (Types.CHAR). The RefreshToken entity maps it as a plain String, so
-- Hibernate's schema validation (ddl-auto: validate) expects varchar and
-- aborts SessionFactory startup. Convert to VARCHAR(64) to match. The hash
-- is always exactly 64 hex chars, so no data is lost in the conversion.
--
-- Written to be correct on its own, not just via Flyway's applied-version
-- tracking, so it survives a manual psql replay, a switch to another migration
-- tool, or a Flyway repair/baseline:
--   * ALTER TABLE IF EXISTS guards a missing table.
--   * SET DATA TYPE is SQL-standard (Postgres also accepts plain TYPE); both it
--     and the IF EXISTS clause parse under H2, which the test suite runs
--     migrations against, so this stays DB-agnostic like V1-V5.
-- Re-running converts VARCHAR(64) -> VARCHAR(64), a no-op without error on both
-- engines (Postgres skips the table rewrite when the typmod is unchanged). A
-- type-conditional guard would also skip the lock, but needs a plpgsql DO block
-- that H2 can't parse — not worth breaking the test harness for.

ALTER TABLE IF EXISTS refresh_tokens
    ALTER COLUMN token_hash SET DATA TYPE VARCHAR(64);
