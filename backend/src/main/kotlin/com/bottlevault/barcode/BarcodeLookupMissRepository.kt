// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.barcode

import org.springframework.data.jpa.repository.JpaRepository

interface BarcodeLookupMissRepository : JpaRepository<BarcodeLookupMiss, String>
