// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package com.bottlevault.integration

import com.bottlevault.auth.dto.AuthResponse
import com.bottlevault.auth.dto.RegisterRequest
import com.bottlevault.bottle.dto.BottleCreateRequest
import com.bottlevault.common.model.BottleStatus
import com.bottlevault.support.AbstractPostgresIntegrationTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Proves that a signed-in user B cannot reach, change or even detect user A's
 * data through any backend endpoint. This is the baseline the invite-only beta
 * rests on: later work on admission, limits and uploads must keep it green.
 *
 * Fixture, rebuilt for every test: the "owner" (A) has one bottle with a private
 * note, purchase place, shelf, cost, rating and an uploaded image; the
 * "intruder" (B) has one bottle of the SAME product with the same status, so any
 * unscoped list or aggregate query would visibly pick up both rows.
 *
 * What counts as "no leak" is a fixed set of fingerprints (A's ids, email, free
 * text, cost and image path) that must not appear anywhere in a response to B.
 * For the id-addressed endpoints B must additionally get exactly what B gets for
 * an id that does not exist: a different status or message would let B probe
 * which ids are real.
 *
 * Deliberately out of scope: the shared brand/product catalog (writable by any
 * signed-in user; tracked separately and not pinned here as correct), rate
 * limiting, input bounds and upload content checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrossUserAccessTest : AbstractPostgresIntegrationTest() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    private class Account(val email: String, val password: String, val userId: String, val token: String)

    private lateinit var owner: Account
    private lateinit var intruder: Account
    private lateinit var productId: String
    private lateinit var ownerBottleId: String
    private lateinit var intruderBottleId: String
    private lateinit var ownerImagePath: String
    private lateinit var ownerBottleSnapshot: String
    // The owner's photo as stored and served. Uploads are re-encoded, so this is
    // not the uploaded OWNER_IMAGE bytes; it is what must never change or leak.
    private lateinit var ownerStoredImage: ByteArray

    /** label -> text that identifies the owner's data; none may appear in a response to anyone else. */
    private lateinit var ownerFingerprints: Map<String, String>

    @BeforeEach
    fun setup() {
        val nonce = System.nanoTime()
        owner = register("owner-$nonce@example.com", "Owner Account")
        intruder = register("intruder-$nonce@example.com", "Intruder Account")

        val products = mockMvc.perform(get("/api/products").param("search", "Old No. 7").header("Authorization", "Bearer ${owner.token}"))
            .andExpect(status().isOk)
            .andReturn()
        productId = objectMapper.readTree(products.response.contentAsString)[0]["id"].asText()

        val ownerNotes = "owner-private-notes-$nonce"
        val ownerPlace = "owner-private-shop-$nonce"
        val ownerShelf = "owner-private-shelf-$nonce"
        ownerBottleId = createBottle(
            owner.token,
            notes = ownerNotes,
            place = ownerPlace,
            shelf = ownerShelf,
            cost = "123.45",
            rating = 5,
            date = LocalDate.of(2024, 3, 15)
        )
        intruderBottleId = createBottle(
            intruder.token,
            notes = "intruder-notes-$nonce",
            place = "intruder-shop-$nonce",
            shelf = "intruder-shelf-$nonce",
            cost = "10.00",
            rating = 2,
            date = LocalDate.of(2025, 6, 1)
        )

        mockMvc.perform(
            multipart("/api/bottles/$ownerBottleId/image")
                .file(MockMultipartFile("file", "owner.png", "image/png", OWNER_IMAGE))
                .bearer(owner.token)
        ).andExpect(status().isOk)
        ownerStoredImage = mockMvc.perform(get("/api/bottles/$ownerBottleId/image").bearer(owner.token))
            .andExpect(status().isOk)
            .andReturn().response.contentAsByteArray

        val view = mockMvc.perform(get("/api/bottles/$ownerBottleId").bearer(owner.token))
            .andExpect(status().isOk)
            .andReturn()
        // Normalised the same way observe() normalises later reads, so the two compare equal.
        ownerBottleSnapshot = withoutTimestamp(view.response.contentAsString)
        ownerImagePath = objectMapper.readTree(ownerBottleSnapshot)["imagePath"].asText()
        assertTrue(ownerImagePath.isNotBlank(), "fixture: owner bottle should have an image")

        ownerFingerprints = mapOf(
            "bottle id" to ownerBottleId,
            "user id" to owner.userId,
            "email" to owner.email,
            "notes" to ownerNotes,
            "purchase location" to ownerPlace,
            "storage location" to ownerShelf,
            "purchase cost" to "123.45",
            "image path" to ownerImagePath
        )
    }

    // ---------------------------------------------------------------------
    // Id-addressed endpoints: B uses A's bottle id
    // ---------------------------------------------------------------------

    @Test
    fun `read bottle - other user is told the bottle does not exist`() {
        assertIndistinguishableFromMissing { id, token -> get("/api/bottles/$id").bearer(token) }
        assertOwnerStateUnchanged()
    }

    @Test
    fun `update bottle - other user is told the bottle does not exist and nothing changes`() {
        assertIndistinguishableFromMissing { id, token ->
            put("/api/bottles/$id")
                .bearer(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(OVERWRITE_EVERYTHING)
        }
        assertOwnerStateUnchanged()
    }

    @Test
    fun `update bottle status - other user is told the bottle does not exist and nothing changes`() {
        assertIndistinguishableFromMissing { id, token ->
            patch("/api/bottles/$id/status")
                .bearer(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"status": "EMPTY"}""")
        }
        assertOwnerStateUnchanged()
    }

    @Test
    fun `delete bottle - other user is told the bottle does not exist and it survives`() {
        assertIndistinguishableFromMissing { id, token -> delete("/api/bottles/$id").bearer(token) }
        assertOwnerStateUnchanged()
    }

    @Test
    fun `get bottle image - other user is told the bottle does not exist`() {
        assertIndistinguishableFromMissing { id, token -> get("/api/bottles/$id/image").bearer(token) }
        assertOwnerStateUnchanged()
    }

    @Test
    fun `upload bottle image - other user is told the bottle does not exist and the image is untouched`() {
        assertIndistinguishableFromMissing { id, token ->
            multipart("/api/bottles/$id/image")
                .file(MockMultipartFile("file", "intruder.png", "image/png", INTRUDER_IMAGE))
                .bearer(token)
        }
        assertOwnerStateUnchanged()
    }

    @Test
    fun `delete bottle image - other user is told the bottle does not exist and the image survives`() {
        assertIndistinguishableFromMissing { id, token -> delete("/api/bottles/$id/image").bearer(token) }
        assertOwnerStateUnchanged()
    }

    @Test
    fun `uploaded images are not served from any static path`() {
        val probes = listOf(
            "/$ownerImagePath",
            "/uploads/$ownerImagePath",
            "/data/uploads/$ownerImagePath",
            "/api/uploads/$ownerImagePath"
        )
        for (probe in probes) {
            for ((who, request) in listOf(
                "intruder" to get(probe).bearer(intruder.token),
                "anonymous" to get(probe)
            )) {
                val response = mockMvc.perform(request).andReturn().response
                assertTrue(response.status in 400..499, "$who GET $probe should be a client error, was ${response.status}")
                assertFalse(
                    response.contentAsByteArray.contentEquals(ownerStoredImage),
                    "$who GET $probe returned the owner's image bytes"
                )
            }
        }
    }

    // ---------------------------------------------------------------------
    // Collections and aggregates: B's results hold none of A's data
    // ---------------------------------------------------------------------

    @Test
    fun `list bottles - unfiltered list holds only the callers bottles`() {
        assertListScopedToIntruder()
    }

    @Test
    fun `list bottles - status filter holds only the callers bottles`() {
        assertListScopedToIntruder("status" to "OPENED")
    }

    @Test
    fun `list bottles - type filter holds only the callers bottles`() {
        assertListScopedToIntruder("type" to "WHISKEY")
    }

    @Test
    fun `list bottles - search holds only the callers bottles`() {
        assertListScopedToIntruder("search" to "Old No. 7")
    }

    @Test
    fun `list bottles - status type and search together hold only the callers bottles`() {
        assertListScopedToIntruder("status" to "OPENED", "type" to "WHISKEY", "search" to "Old No. 7")
    }

    @Test
    fun `list bottles - a large page size does not reach other users bottles`() {
        assertListScopedToIntruder("page" to "0", "size" to "100")
    }

    @Test
    fun `list bottles - a user with no bottles sees none`() {
        val newcomer = register("newcomer-${System.nanoTime()}@example.com", "Newcomer")
        for (filters in listOf(
            emptyList(),
            listOf("status" to "OPENED"),
            listOf("type" to "WHISKEY"),
            listOf("search" to "Old No. 7"),
            listOf("page" to "0", "size" to "100")
        )) {
            val request = get("/api/bottles").bearer(newcomer.token)
            filters.forEach { (k, v) -> request.param(k, v) }
            val response = mockMvc.perform(request)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content.length()").value(0))
                .andReturn().response
            assertNoOwnerData(response.contentAsString, "list for a new user, filters=$filters")
        }
    }

    @Test
    fun `statistics - totals cover only the callers bottles`() {
        // Control: the owner's own numbers are the ones that must not show up.
        mockMvc.perform(get("/api/statistics").bearer(owner.token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalValue").value(123.45))

        val response = mockMvc.perform(get("/api/statistics").bearer(intruder.token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalBottles").value(1))
            .andExpect(jsonPath("$.totalValue").value(10.0))
            .andExpect(jsonPath("$.averageCost").value(10.0))
            .andExpect(jsonPath("$.averageRating").value(2.0))
            .andExpect(jsonPath("$.percentageOpened").value(100.0))
            .andExpect(jsonPath("$.statusBreakdown.length()").value(1))
            .andExpect(jsonPath("$.statusBreakdown[0].status").value("OPENED"))
            .andExpect(jsonPath("$.statusBreakdown[0].count").value(1))
            .andExpect(jsonPath("$.typeDistribution.length()").value(1))
            .andExpect(jsonPath("$.typeDistribution[0].count").value(1))
            .andExpect(jsonPath("$.spendingOverTime.length()").value(1))
            .andExpect(jsonPath("$.spendingOverTime[0].year").value(2025))
            .andExpect(jsonPath("$.spendingOverTime[0].month").value(6))
            .andExpect(jsonPath("$.spendingOverTime[0].total").value(10.0))
            .andExpect(jsonPath("$.topRatedBottles.length()").value(1))
            .andExpect(jsonPath("$.topRatedBottles[0].id").value(intruderBottleId))
            .andExpect(jsonPath("$.recentAdditions.length()").value(1))
            .andExpect(jsonPath("$.recentAdditions[0].id").value(intruderBottleId))
            .andReturn().response
        assertNoOwnerData(response.contentAsString, "statistics for the intruder")
    }

    @Test
    fun `statistics - a user with no bottles sees zeroed totals`() {
        val newcomer = register("newcomer-${System.nanoTime()}@example.com", "Newcomer")
        val response = mockMvc.perform(get("/api/statistics").bearer(newcomer.token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalBottles").value(0))
            .andExpect(jsonPath("$.totalValue").value(0))
            .andExpect(jsonPath("$.averageCost").doesNotExist())
            .andExpect(jsonPath("$.averageRating").doesNotExist())
            .andExpect(jsonPath("$.percentageOpened").value(0.0))
            .andExpect(jsonPath("$.statusBreakdown.length()").value(0))
            .andExpect(jsonPath("$.typeDistribution.length()").value(0))
            .andExpect(jsonPath("$.spendingOverTime.length()").value(0))
            .andExpect(jsonPath("$.topRatedBottles.length()").value(0))
            .andExpect(jsonPath("$.recentAdditions.length()").value(0))
            .andReturn().response
        assertNoOwnerData(response.contentAsString, "statistics for a new user")
    }

    // ---------------------------------------------------------------------
    // Writes that name no id: they must land on the caller, never on someone else
    // ---------------------------------------------------------------------

    @Test
    fun `create bottle - the new bottle is filed under the caller only`() {
        val created = createBottle(
            intruder.token, notes = "second-intruder-bottle", place = "p", shelf = "s",
            cost = "1.00", rating = 1, date = LocalDate.of(2025, 1, 1)
        )

        mockMvc.perform(get("/api/bottles").bearer(owner.token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(ownerBottleId))
        // And the owner cannot open it by id either.
        mockMvc.perform(get("/api/bottles/$created").bearer(owner.token))
            .andExpect(status().isNotFound)
        assertOwnerStateUnchanged()
    }

    @Test
    fun `update profile - changing the callers profile leaves other users profiles alone`() {
        val before = loginUserJson(owner)

        mockMvc.perform(
            put("/api/user/profile")
                .bearer(intruder.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"displayName": "Renamed Intruder", "defaultCurrency": "EUR", "measurementUnit": "oz"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(intruder.email))
            .andExpect(jsonPath("$.id").value(intruder.userId))

        assertEquals(before, loginUserJson(owner), "owner profile changed after the intruder edited their own")
    }

    @Test
    fun `change password - changing the callers password leaves other users passwords alone`() {
        mockMvc.perform(
            put("/api/user/password")
                .bearer(intruder.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "${intruder.password}", "newPassword": "intruder-new-password-1"}""")
        ).andExpect(status().isNoContent)

        // The owner still signs in with the original password.
        loginUserJson(owner)
    }

    // ---------------------------------------------------------------------
    // No credentials, or a bad token: every endpoint above is closed
    // ---------------------------------------------------------------------

    @Test
    fun `unauthenticated - list bottles is rejected`() {
        assertRejectedWithoutValidToken { get("/api/bottles") }
        assertRejectedWithoutValidToken { get("/api/bottles").param("status", "OPENED").param("type", "WHISKEY").param("search", "Old") }
    }

    @Test
    fun `unauthenticated - read bottle is rejected`() {
        assertRejectedWithoutValidToken { get("/api/bottles/$ownerBottleId") }
    }

    @Test
    fun `unauthenticated - create bottle is rejected`() {
        assertRejectedWithoutValidToken {
            post("/api/bottles").contentType(MediaType.APPLICATION_JSON).content("""{"productId": "$productId"}""")
        }
        mockMvc.perform(get("/api/bottles").bearer(owner.token))
            .andExpect(jsonPath("$.totalElements").value(1))
    }

    @Test
    fun `unauthenticated - update bottle is rejected and nothing changes`() {
        assertRejectedWithoutValidToken {
            put("/api/bottles/$ownerBottleId").contentType(MediaType.APPLICATION_JSON).content(OVERWRITE_EVERYTHING)
        }
    }

    @Test
    fun `unauthenticated - update bottle status is rejected and nothing changes`() {
        assertRejectedWithoutValidToken {
            patch("/api/bottles/$ownerBottleId/status").contentType(MediaType.APPLICATION_JSON).content("""{"status": "EMPTY"}""")
        }
    }

    @Test
    fun `unauthenticated - delete bottle is rejected and it survives`() {
        assertRejectedWithoutValidToken { delete("/api/bottles/$ownerBottleId") }
    }

    @Test
    fun `unauthenticated - get bottle image is rejected`() {
        assertRejectedWithoutValidToken { get("/api/bottles/$ownerBottleId/image") }
    }

    @Test
    fun `unauthenticated - upload bottle image is rejected and the image is untouched`() {
        assertRejectedWithoutValidToken {
            multipart("/api/bottles/$ownerBottleId/image")
                .file(MockMultipartFile("file", "anon.png", "image/png", INTRUDER_IMAGE))
        }
    }

    @Test
    fun `unauthenticated - delete bottle image is rejected and the image survives`() {
        assertRejectedWithoutValidToken { delete("/api/bottles/$ownerBottleId/image") }
    }

    @Test
    fun `unauthenticated - statistics is rejected`() {
        assertRejectedWithoutValidToken { get("/api/statistics") }
    }

    @Test
    fun `unauthenticated - update profile is rejected`() {
        assertRejectedWithoutValidToken {
            put("/api/user/profile").contentType(MediaType.APPLICATION_JSON).content("""{"displayName": "Hacker"}""")
        }
    }

    @Test
    fun `unauthenticated - change password is rejected`() {
        assertRejectedWithoutValidToken {
            put("/api/user/password").contentType(MediaType.APPLICATION_JSON)
                .content("""{"currentPassword": "${owner.password}", "newPassword": "hijacked-password-3"}""")
        }
        loginUserJson(owner) // the owner's original password still works
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private data class Observed(val status: Int, val contentType: String?, val body: String)

    /**
     * Sends the same request twice as the intruder, once with the owner's real
     * bottle id and once with a random id that exists nowhere, and requires the
     * two answers to be identical (the timestamp aside) and a plain 404.
     */
    private fun assertIndistinguishableFromMissing(
        request: (bottleId: String, token: String) -> RequestBuilder
    ) {
        val againstOwners = observe(request(ownerBottleId, intruder.token))
        val againstMissing = observe(request(UUID.randomUUID().toString(), intruder.token))

        assertEquals(404, againstMissing.status, "baseline: an id that exists nowhere should be a 404")
        assertEquals(againstMissing, againstOwners, "answer for the owner's bottle differs from the answer for a missing one")
        assertNoOwnerData(againstOwners.body, "response to the intruder")
    }

    /**
     * Lists with the given query parameters as both users. The owner's list is
     * the control (the filter really would match their bottle); the intruder's
     * must hold exactly the intruder's own bottle and nothing of the owner's.
     */
    private fun assertListScopedToIntruder(vararg filters: Pair<String, String>) {
        fun list(token: String) = get("/api/bottles").bearer(token).also { r -> filters.forEach { (k, v) -> r.param(k, v) } }

        mockMvc.perform(list(owner.token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(ownerBottleId))

        val response = mockMvc.perform(list(intruder.token))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].id").value(intruderBottleId))
            .andReturn().response
        assertNoOwnerData(response.contentAsString, "list for the intruder, filters=${filters.toList()}")
    }

    /** The owner's bottle and image are byte-for-byte what they were after setup. */
    private fun assertOwnerStateUnchanged() {
        val bottle = observe(get("/api/bottles/$ownerBottleId").bearer(owner.token))
        assertEquals(200, bottle.status, "the owner's bottle should still exist")
        assertEquals(ownerBottleSnapshot, bottle.body, "the owner's bottle changed")

        val image = mockMvc.perform(get("/api/bottles/$ownerBottleId/image").bearer(owner.token))
            .andExpect(status().isOk)
            .andReturn().response.contentAsByteArray
        assertContentEquals(ownerStoredImage, image, "the owner's image changed")
    }

    private fun assertRejectedWithoutValidToken(request: () -> RequestBuilder) {
        for ((label, builder) in listOf(
            "no Authorization header" to request(),
            "malformed bearer token" to withBearer(request(), "not-a-valid-token")
        )) {
            val response = mockMvc.perform(builder).andReturn().response
            assertEquals(401, response.status, label)
            assertNoOwnerData(response.contentAsString, label)
        }
        assertOwnerStateUnchanged()
    }

    private fun assertNoOwnerData(body: String, context: String) {
        ownerFingerprints.forEach { (label, needle) ->
            assertFalse(body.contains(needle), "$context leaked the owner's $label")
        }
    }

    private fun observe(request: RequestBuilder): Observed {
        val response = mockMvc.perform(request).andReturn().response
        return Observed(response.status, response.contentType, withoutTimestamp(response.contentAsString))
    }

    /** Error bodies carry a creation timestamp; drop it so two answers can be compared. */
    private fun withoutTimestamp(raw: String): String {
        val tree = runCatching { objectMapper.readTree(raw) }.getOrNull()
        if (tree is ObjectNode) {
            tree.remove("timestamp")
            return tree.toString()
        }
        return raw
    }

    private fun register(email: String, displayName: String): Account {
        val password = "password123"
        val result = mockMvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(RegisterRequest(email, password, displayName)))
        )
            .andExpect(status().isCreated)
            .andReturn()
        val auth = objectMapper.readValue(result.response.contentAsString, AuthResponse::class.java)
        return Account(email, password, auth.user.id, auth.accessToken)
    }

    /** Signs in and returns the `user` object, proving the password still works. */
    private fun loginUserJson(account: Account): String {
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email": "${account.email}", "password": "${account.password}"}""")
        )
            .andExpect(status().isOk)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["user"].toString()
    }

    private fun createBottle(
        token: String,
        notes: String,
        place: String,
        shelf: String,
        cost: String,
        rating: Int,
        date: LocalDate
    ): String {
        val result = mockMvc.perform(
            post("/api/bottles")
                .bearer(token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        BottleCreateRequest(
                            productId = productId,
                            status = BottleStatus.OPENED,
                            percentageLeft = 42,
                            purchaseDate = date,
                            purchaseLocation = place,
                            purchaseCost = BigDecimal(cost),
                            notes = notes,
                            rating = rating,
                            storageLocation = shelf
                        )
                    )
                )
        )
            .andExpect(status().isCreated)
            .andReturn()
        return objectMapper.readTree(result.response.contentAsString)["id"].asText()
    }

    private fun <B : AbstractMockHttpServletRequestBuilder<B>> B.bearer(token: String): B =
        header("Authorization", "Bearer $token")

    /** For a request already reduced to a [RequestBuilder] (see [assertRejectedWithoutValidToken]). */
    private fun withBearer(builder: RequestBuilder, token: String) =
        RequestBuilder { context -> builder.buildRequest(context).apply { addHeader("Authorization", "Bearer $token") } }

    private companion object {
        /** Sets every updatable field, so a partial write to the owner's bottle cannot slip through unseen. */
        const val OVERWRITE_EVERYTHING = """{
            "status": "EMPTY", "percentageLeft": 0, "purchaseDate": "2000-01-01",
            "purchaseLocation": "overwritten", "purchaseCost": 0.01, "notes": "overwritten",
            "rating": 1, "storageLocation": "overwritten"
        }"""

        // A real, decodable PNG: uploads are decoded and re-encoded, so a
        // hand-assembled byte string that no decoder accepts would be refused (422).
        val OWNER_IMAGE: ByteArray = java.io.ByteArrayOutputStream().also { out ->
            val img = java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            img.createGraphics().apply { color = java.awt.Color(0x2E7D32); fillRect(0, 0, 8, 8); dispose() }
            javax.imageio.ImageIO.write(img, "png", out)
        }.toByteArray()

        /**
         * A real, decodable photo that differs from [OWNER_IMAGE] (size and colour),
         * so that if an ownership bug let it through, the re-encoded result would
         * replace the owner's photo and the comparison would catch the swap.
         */
        val INTRUDER_IMAGE: ByteArray = java.io.ByteArrayOutputStream().also { out ->
            val img = java.awt.image.BufferedImage(16, 9, java.awt.image.BufferedImage.TYPE_INT_RGB)
            img.createGraphics().apply { color = java.awt.Color.MAGENTA; fillRect(0, 0, 16, 9); dispose() }
            javax.imageio.ImageIO.write(img, "png", out)
        }.toByteArray()
    }
}
