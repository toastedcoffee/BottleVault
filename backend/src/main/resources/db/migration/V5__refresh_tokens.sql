-- Server-side refresh token store.
--
-- Refresh tokens are opaque 256-bit random values handed to the client.
-- Only a SHA-256 hash is stored here, so a database leak does not yield
-- usable tokens. Rows are deleted on use (rotation), on logout, and on
-- password change — deletion IS revocation, no status flag needed.

CREATE TABLE refresh_tokens (
    id          UUID DEFAULT gen_random_uuid() NOT NULL PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  CHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT NOW() NOT NULL
);

-- Supports "revoke all sessions for user" (logout everywhere, password change).
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
