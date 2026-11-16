-- SPDX-License-Identifier: AGPL-3.0-only
-- SPDX-FileCopyrightText: 2025-2026 toastedcoffee
--
-- Invite-only registration. A code is 16 characters of Crockford base32 (80
-- bits); only its SHA-256 is stored, so a database leak yields no usable codes.
-- Codes are created, listed and revoked with scripts/invite.sh.
--
-- Parses under both PostgreSQL and H2 in PostgreSQL mode.

CREATE TABLE invite_codes (
    id          UUID DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    code_hash   VARCHAR(64) NOT NULL UNIQUE,
    email       VARCHAR(255),
    note        VARCHAR(200),
    max_uses    INTEGER DEFAULT 1 NOT NULL CHECK (max_uses >= 1),
    use_count   INTEGER DEFAULT 0 NOT NULL CHECK (use_count >= 0),
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at  TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

-- Which invite created each account (NULL for accounts created before invites,
-- or while registration is open).
ALTER TABLE users ADD COLUMN invite_code_id UUID REFERENCES invite_codes(id);
