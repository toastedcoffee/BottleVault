-- SPDX-License-Identifier: AGPL-3.0-only
-- SPDX-FileCopyrightText: 2025-2026 toastedcoffee
--
-- Size of each bottle's stored photo, after re-encoding. Summed per user for the
-- photo storage quota. NULL means "not yet re-encoded": LegacyImageReprocessor
-- fills it for photos uploaded before this release.
--
-- Parses under both PostgreSQL and H2 in PostgreSQL mode.

ALTER TABLE bottles ADD COLUMN image_bytes BIGINT;
