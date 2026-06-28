-- Align token_hash with the JPA mapping.
--
-- V5 created token_hash as CHAR(64), which Postgres stores as bpchar
-- (Types.CHAR). The RefreshToken entity maps it as a plain String, so
-- Hibernate's schema validation (ddl-auto: validate) expects varchar and
-- aborts SessionFactory startup. Convert to VARCHAR(64) to match. The hash
-- is always exactly 64 hex chars, so no data is lost in the conversion.

ALTER TABLE refresh_tokens ALTER COLUMN token_hash TYPE VARCHAR(64);
