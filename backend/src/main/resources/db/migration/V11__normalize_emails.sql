-- SPDX-License-Identifier: AGPL-3.0-only
-- SPDX-FileCopyrightText: 2025-2026 toastedcoffee
--
-- One spelling per email address. Registration and sign-in now normalize
-- (trim + lower case) before storing or comparing; this brings existing rows in
-- line. users.email is UNIQUE, so two rows that differ only by case would make
-- this migration fail and stop the API from starting — run the DEPLOY.md §7d
-- pre-check before deploying.
--
-- Parses under both PostgreSQL and H2 in PostgreSQL mode.

UPDATE users SET email = LOWER(TRIM(email)) WHERE email <> LOWER(TRIM(email));
