package com.ssutime.notification.infrastructure

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.DependsOn
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@DependsOn("entityManagerFactory")
class BoardMigration(
    private val jdbcTemplate: JdbcTemplate,
) {
    @PostConstruct
    fun migrate() {
        val legacyTableExists =
            jdbcTemplate.dataSource!!.connection.use { connection ->
                connection.metaData.getTables(connection.catalog, null, "%", arrayOf("TABLE")).use { tables ->
                    generateSequence { if (tables.next()) tables.getString("TABLE_NAME") else null }
                        .any { it.equals("user_boards", ignoreCase = true) }
                }
            }
        if (!legacyTableExists) return

        jdbcTemplate.update(
            "INSERT IGNORE INTO boards (board_id, created_at, updated_at) " +
                "SELECT DISTINCT board_id, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM user_boards",
        )
        jdbcTemplate.execute("DROP TABLE user_boards")
    }
}
