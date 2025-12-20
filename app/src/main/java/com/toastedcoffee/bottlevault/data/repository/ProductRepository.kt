package com.toastedcoffee.bottlevault.data.repository

import com.toastedcoffee.bottlevault.data.local.database.BrandDao
import com.toastedcoffee.bottlevault.data.local.database.ProductDao
import com.toastedcoffee.bottlevault.data.model.AlcoholType
import com.toastedcoffee.bottlevault.data.model.Brand
import com.toastedcoffee.bottlevault.data.model.Product
import com.toastedcoffee.bottlevault.data.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for product catalog operations.
 *
 * RESPONSIBILITIES:
 * - Manage product and brand data
 * - Handle barcode lookups
 * - Support product search
 * - Allow user-created products (when existing product not found)
 *
 * DESIGN DECISION:
 * Products and Brands are tightly coupled (every product has a brand).
 * It makes sense to manage them together in one repository.
 *
 * ALTERNATIVE:
 * We could split into ProductRepository + BrandRepository.
 * But that adds complexity without much benefit for this app.
 */
@Singleton
class ProductRepository @Inject constructor(
    private val productDao: ProductDao,
    private val brandDao: BrandDao
) {

    // ==========================================
    // PRODUCT OPERATIONS
    // ==========================================

    /**
     * Gets all products for a specific brand.
     *
     * RETURN TYPE (Flow):
     * Flow emits updates whenever products change.
     * Perfect for dropdown menus that need to stay current.
     *
     * USE CASE:
     * User selects "Jack Daniel's" brand.
     * This returns all Jack Daniel's products (Old No. 7, Single Barrel, etc.)
     *
     * @param brandId The brand's unique identifier
     * @return Flow of products for this brand, sorted alphabetically
     */
    fun getProductsByBrand(brandId: String): Flow<List<Product>> {
        return productDao.getProductsByBrand(brandId)
    }

    /**
     * Gets all products of a specific alcohol type.
     *
     * USE CASE:
     * User filters by "WHISKEY" - show all whiskey products.
     *
     * @param type The alcohol type (WHISKEY, VODKA, etc.)
     * @return Flow of products of this type
     */
    fun getProductsByType(type: AlcoholType): Flow<List<Product>> {
        return productDao.getProductsByType(type)
    }

    /**
     * Searches products by name or brand name.
     *
     * HOW IT WORKS:
     * SQL LIKE query: searches product name AND brand name.
     * Case-insensitive (SQL handles this).
     *
     * EXAMPLE:
     * Query "jack" finds:
     * - "Jack Daniel's Old No. 7"
     * - "Captain Jack Rum"
     *
     * WHY SUSPEND (not Flow):
     * Search is a one-time action, not continuous monitoring.
     *
     * @param query Search term (partial match OK)
     * @return List of matching products
     */
    suspend fun searchProducts(query: String): List<Product> {
        return productDao.searchProducts(query)
    }

    /**
     * Finds product by barcode (for scanner feature).
     *
     * CRITICAL FOR:
     * Barcode scanning feature (Week 2 task).
     *
     * FLOW:
     * 1. User scans barcode
     * 2. Call this method
     * 3. If found: Pre-fill product info
     * 4. If not found: Allow manual entry
     *
     * @param barcode UPC/EAN barcode number
     * @return Product if found, null otherwise
     */
    suspend fun getProductByBarcode(barcode: String): Product? {
        return productDao.getProductByBarcode(barcode)
    }

    /**
     * Gets product by its unique ID.
     *
     * WHEN NEEDED:
     * - Displaying product details
     * - Editing a bottle (need product info)
     *
     * @param productId The product's unique identifier
     * @return Product if found, null otherwise
     */
    suspend fun getProductById(productId: String): Product? {
        return productDao.getProductById(productId)
    }

    /**
     * Creates a new user-defined product.
     *
     * WHEN TO USE:
     * User scans barcode not in our database.
     * OR: User wants to add custom/rare bottle.
     *
     * IMPORTANT FLAGS:
     * - isUserCreated = true (so we know it's not pre-seeded data)
     * - syncStatus = PENDING_CREATE (needs to sync to backend)
     *
     * SYNC STRATEGY (Future - Month 4):
     * User-created products sync to backend.
     * Other users can then discover them.
     *
     * @param product Product data to create
     * @return The new product's ID
     */
    suspend fun createProduct(product: Product): String {
        val productWithMetadata = product.copy(
            // Generate unique ID if not provided
            id = if (product.id.isBlank()) UUID.randomUUID().toString() else product.id,

            // Mark as user-created
            isUserCreated = true,

            // Set sync status for backend
            syncStatus = SyncStatus.PENDING_CREATE,

            // Timestamp creation
            createdAt = Date(),
            lastModified = Date()
        )

        productDao.insertProduct(productWithMetadata)
        return productWithMetadata.id
    }

    // ==========================================
    // BRAND OPERATIONS
    // ==========================================

    /**
     * Gets all available brands.
     *
     * USE CASE:
     * Populate brand dropdown in "Add Bottle" screen.
     *
     * RETURN TYPE (Flow):
     * Updates automatically when brands added/changed.
     *
     * @return Flow of all brands, sorted alphabetically
     */
    fun getAllBrands(): Flow<List<Brand>> {
        return brandDao.getAllBrands()
    }

    /**
     * Searches brands by name.
     *
     * USE CASE:
     * User types "Jack" in brand search.
     * Returns "Jack Daniel's", "Captain Jack", etc.
     *
     * @param query Search term (partial match OK)
     * @return List of matching brands
     */
    suspend fun searchBrands(query: String): List<Brand> {
        return brandDao.searchBrands(query)
    }

    /**
     * Gets brand by its unique ID.
     *
     * @param brandId The brand's unique identifier
     * @return Brand if found, null otherwise
     */
    suspend fun getBrandById(brandId: String): Brand? {
        return brandDao.getBrandById(brandId)
    }

    /**
     * Creates a new user-defined brand.
     *
     * WHEN TO USE:
     * User wants to add product for brand not in database.
     * Example: Small craft distillery, local winery.
     *
     * @param brand Brand data to create
     * @return The new brand's ID
     */
    suspend fun createBrand(brand: Brand): String {
        val brandWithMetadata = brand.copy(
            id = if (brand.id.isBlank()) UUID.randomUUID().toString() else brand.id,
            syncStatus = SyncStatus.PENDING_CREATE,
            lastModified = Date()
        )

        brandDao.insertBrand(brandWithMetadata)
        return brandWithMetadata.id
    }

    // ==========================================
    // HELPER / CONVENIENCE METHODS
    // ==========================================

    /**
     * Gets product with its brand information in one call.
     *
     * WHY USEFUL:
     * Often need both product AND brand (e.g., displaying bottle details).
     * This avoids two separate database queries.
     *
     * IMPLEMENTATION NOTE:
     * Could be optimized with a JOIN query in DAO.
     * For now, two separate queries is fine (cached by Room).
     *
     * @param productId The product's ID
     * @return Pair of (Product, Brand) or null if product not found
     */
    suspend fun getProductWithBrand(productId: String): Pair<Product, Brand>? {
        val product = getProductById(productId) ?: return null
        val brand = getBrandById(product.brandId) ?: return null
        return Pair(product, brand)
    }
}