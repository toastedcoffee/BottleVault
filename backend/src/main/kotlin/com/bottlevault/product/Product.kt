package com.bottlevault.product

import com.bottlevault.brand.Brand
import com.bottlevault.common.model.AlcoholType
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "products")
class Product(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", nullable = false)
    var brand: Brand,

    @Column(nullable = false)
    var name: String,

    @Column(unique = true)
    var barcode: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: AlcoholType,

    var subtype: String? = null,

    var size: String? = null,

    var abv: BigDecimal? = null,

    var description: String? = null,

    @Column(name = "image_url")
    var imageUrl: String? = null,

    @Column(name = "is_user_created")
    var isUserCreated: Boolean = false,

    @Column(name = "created_at", updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)
