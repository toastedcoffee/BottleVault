package com.bottlevault.bottle

import com.bottlevault.auth.User
import com.bottlevault.common.model.BottleStatus
import com.bottlevault.product.Product
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "bottles")
class Bottle(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    var product: Product,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: BottleStatus = BottleStatus.UNOPENED,

    @Column(name = "percentage_left")
    var percentageLeft: Int = 100,

    @Column(name = "purchase_date")
    var purchaseDate: LocalDate? = null,

    @Column(name = "purchase_location")
    var purchaseLocation: String? = null,

    @Column(name = "purchase_cost")
    var purchaseCost: BigDecimal? = null,

    var notes: String? = null,

    var rating: Int? = null,

    @Column(name = "storage_location")
    var storageLocation: String? = null,

    @Column(name = "image_path")
    var imagePath: String? = null,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)
