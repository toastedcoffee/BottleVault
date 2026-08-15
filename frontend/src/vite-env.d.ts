// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Git SHA of the commit this bundle was built from, injected via the Docker
   * build arg GIT_SHA. Absent in local dev builds, so consumers must provide a
   * fallback rather than rendering `undefined`.
   */
  readonly VITE_GIT_SHA?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
