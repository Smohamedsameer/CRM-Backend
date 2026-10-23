package com.leadquote.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Flyway is disabled and Hibernate's ddl-auto=update never changes an existing column, so on MySQL the
 * enum-typed columns below would reject the new CALLED_TO_ORDER value ("Data truncated for column").
 * On startup, any of them that is still a MySQL ENUM is converted to a plain VARCHAR(40) - the values stay the
 * same, so existing rows are untouched, and future status additions need no database change.
 * It is a no-op once converted, and failures are logged instead of stopping the app.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        widenEnumColumn("leads", "status");
        widenEnumColumn("notifications", "type");
        allowNull("whatsapp_messages", "lead_id", "BIGINT");
        removeOrphanAttachments();
    }

    /** Messages from people who are not leads have no lead: the old NOT NULL on lead_id has to go (one time). */
    private void allowNull(String table, String column, String type) {
        try {
            List<String> nullable = jdbc.queryForList(
                    "SELECT IS_NULLABLE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    String.class, table, column);
            if (nullable.isEmpty() || !"NO".equalsIgnoreCase(nullable.get(0))) return;
            jdbc.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` " + type + " NULL");
            log.info("Made {}.{} nullable", table, column);
        } catch (Exception e) {
            log.warn("Could not make {}.{} nullable: {}. Chats from numbers that are not leads will fail to save until you run: "
                    + "ALTER TABLE {} MODIFY COLUMN {} {} NULL;", table, column, e.getMessage(), table, column, type);
        }
    }

    /** Chat files whose message no longer exists (e.g. left behind by an older delete) are dead weight in the database. */
    private void removeOrphanAttachments() {
        try {
            int n = jdbc.update("DELETE FROM chat_attachments WHERE message_id NOT IN (SELECT id FROM whatsapp_messages)");
            if (n > 0) log.info("Removed {} orphaned chat attachment(s)", n);
        } catch (Exception e) {
            log.debug("Orphan attachment cleanup skipped: {}", e.getMessage());
        }
    }

    private void widenEnumColumn(String table, String column) {
        try {
            List<String> types = jdbc.queryForList(
                    "SELECT DATA_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    String.class, table, column);
            if (types.isEmpty() || !"enum".equalsIgnoreCase(types.get(0))) return;
            jdbc.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` VARCHAR(40) NOT NULL");
            log.info("Converted {}.{} from ENUM to VARCHAR(40)", table, column);
        } catch (Exception e) {
            log.warn("Could not convert {}.{} to VARCHAR: {}. If 'Called to order' fails to save, run: "
                    + "ALTER TABLE {} MODIFY COLUMN {} VARCHAR(40) NOT NULL;", table, column, e.getMessage(), table, column);
        }
    }
}
