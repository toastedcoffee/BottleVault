// SPDX-License-Identifier: AGPL-3.0-only
// SPDX-FileCopyrightText: 2025-2026 toastedcoffee
package db.migration

import com.bottlevault.common.text.NameNormalizer
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.util.UUID

/**
 * Backfills display_name and normalized_name for every existing brand and
 * product, using the same NameNormalizer the application uses at runtime.
 *
 * This is a JVM migration rather than SQL because diacritic folding (Patrón to
 * patron) has no portable SQL form, and because sharing the normalizer makes it
 * impossible for the backfill to drift from runtime behavior.
 *
 * BaseJavaMigration does not checksum class content, so the SPDX header above
 * cannot break Flyway validation the way one added to an applied .sql migration
 * would.
 */
class V8__backfill_normalized_names : BaseJavaMigration() {

    override fun migrate(context: Context) {
        backfill(context, table = "brands")
        backfill(context, table = "products")
    }

    private fun backfill(context: Context, table: String) {
        val connection = context.connection
        val rows = mutableListOf<Pair<UUID, String>>()

        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT id, display_name FROM $table").use { resultSet ->
                while (resultSet.next()) {
                    rows += resultSet.getObject("id", UUID::class.java) to
                        resultSet.getString("display_name")
                }
            }
        }

        connection.prepareStatement(
            "UPDATE $table SET display_name = ?, normalized_name = ? WHERE id = ?",
        ).use { statement ->
            rows.forEach { (id, raw) ->
                statement.setString(1, NameNormalizer.displayName(raw))
                statement.setString(2, NameNormalizer.normalize(raw))
                statement.setObject(3, id)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
