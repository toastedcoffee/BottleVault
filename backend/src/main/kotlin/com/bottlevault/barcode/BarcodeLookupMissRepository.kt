package com.bottlevault.barcode

import org.springframework.data.jpa.repository.JpaRepository

interface BarcodeLookupMissRepository : JpaRepository<BarcodeLookupMiss, String>
