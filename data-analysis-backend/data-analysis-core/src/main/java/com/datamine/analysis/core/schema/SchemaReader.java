package com.datamine.analysis.core.schema;

import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.common.enums.DatabaseType;
import com.datamine.analysis.core.datasource.DynamicDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaReader {

    private final DynamicDataSource dynamicDataSource;

    public List<Map<String, Object>> readTables(Connection connection) {
        JdbcTemplate jdbc = createJdbcTemplate(connection);
        String sql = """
                SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS, TABLE_TYPE
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'
                ORDER BY TABLE_NAME
                """;
        return jdbc.queryForList(sql, connection.getDatabase());
    }

    public List<Map<String, Object>> readColumns(Connection connection, String tableName) {
        JdbcTemplate jdbc = createJdbcTemplate(connection);
        String sql = """
                SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY,
                       COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT, ORDINAL_POSITION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                ORDER BY ORDINAL_POSITION
                """;
        return jdbc.queryForList(sql, connection.getDatabase(), tableName);
    }

    public List<Map<String, Object>> readAllColumns(Connection connection) {
        JdbcTemplate jdbc = createJdbcTemplate(connection);
        String sql = """
                SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY,
                       COLUMN_DEFAULT, EXTRA, COLUMN_COMMENT, ORDINAL_POSITION
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = ?
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """;
        return jdbc.queryForList(sql, connection.getDatabase());
    }

    public List<Map<String, Object>> readIndexes(Connection connection, String tableName) {
        JdbcTemplate jdbc = createJdbcTemplate(connection);
        String sql = """
                SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE, SEQ_IN_INDEX
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?
                ORDER BY INDEX_NAME, SEQ_IN_INDEX
                """;
        return jdbc.queryForList(sql, connection.getDatabase(), tableName);
    }

    public List<Map<String, Object>> readForeignKeys(Connection connection) {
        JdbcTemplate jdbc = createJdbcTemplate(connection);
        String sql = """
                SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME, ORDINAL_POSITION
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = ?
                  AND REFERENCED_TABLE_SCHEMA = ?
                  AND REFERENCED_TABLE_NAME IS NOT NULL
                ORDER BY TABLE_NAME, ORDINAL_POSITION
                """;
        return jdbc.queryForList(sql, connection.getDatabase(), connection.getDatabase());
    }

    private JdbcTemplate createJdbcTemplate(Connection connection) {
        return new JdbcTemplate(dynamicDataSource.getDataSource(connection));
    }
}
