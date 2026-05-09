package com.bottlevault.barcode

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "barcode_lookup_miss")
class BarcodeLookupMiss(
    @Id
    val barcode: String,

    @Column(name = "last_attempt_at", nullable = false)
    var lastAttemptAt: Instant = Instant.now(),

    @Column(nullable = false)
    var attempts: Int = 1
)
