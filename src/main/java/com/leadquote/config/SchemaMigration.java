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
