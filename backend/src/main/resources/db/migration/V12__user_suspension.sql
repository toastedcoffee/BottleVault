-- SPDX-License-Identifier: AGPL-3.0-only
-- SPDX-FileCopyrightText: 2025-2026 toastedcoffee
--
-- Account suspension. Set and cleared with scripts/suspend-user.sh; a suspended
-- account cannot sign in or refresh, and its access tokens stop working within a
-- minute (UserStatusCache).
--
-- Parses under both PostgreSQL and H2 in PostgreSQL mode.

ALTER TABLE users ADD COLUMN disabled_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN disabled_reason VARCHAR(200);
