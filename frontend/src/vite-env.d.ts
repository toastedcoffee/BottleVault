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
  /**
   * Repository the in-app source offer points at, injected via the Docker build
   * arg SOURCE_URL. Absent in upstream and unmodified builds, which fall back to
   * the upstream repository. A self-hoster who modifies the code sets this to
   * their own repository so the AGPL section 13 offer points at the source they
   * actually run.
   */
  readonly VITE_SOURCE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
