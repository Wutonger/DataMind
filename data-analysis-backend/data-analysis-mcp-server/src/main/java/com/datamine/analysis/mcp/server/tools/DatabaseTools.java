package com.datamine.analysis.mcp.server.tools;

import com.datamine.analysis.common.enums.DatabaseType;
import com.datamine.analysis.mcp.server.util.PasswordUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseTools {

    private static final Set<String> READ_ONLY_SQL_KEYWORDS = Set.of(
            "SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN");
    /** 匹配sql前的块注释*/
    private static final Pattern BLOCK_COMMENT_PATTERN = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);

    /** 匹配sql前的行注释*/
    private static final Pattern LINE_COMMENT_PATTERN = Pattern.compile("(?m)^\\s*(--|#).*?$");


    private static final Pattern FIRST_KEYWORD_PATTERN = Pattern.compile("^([A-Z]+)\\b");


    private static final Pattern MUTATING_SQL_PATTERN = Pattern.compile("\\b(INSERT|UPDATE|DELETE|REPLACE|MERGE|ALTER|DROP|" +
            "CREATE|TRUNCATE|GRANT|REVOKE|COMMIT|ROLLBACK|CALL|DO|HANDLER|LOAD|LOCK|UNLOCK|SET|USE|ANALYZE|OPTIMIZE|REPAIR|FLUSH|RESET|START|BEGIN)\\b");


    private static final Pattern READ_ONLY_DENY_PATTERN = Pattern.compile("\\bFOR\\s+UPDATE\\b|\\bLOCK\\s+IN\\s+SHARE\\s+MODE\\b|\\bINTO\\s+OUTFILE\\b|\\bINTO\\s+DUMPFILE\\b");

    private final JdbcTemplate jdbcTemplate;
    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    @Tool(description = "List all tables in the selected database.")
    public List<Map<String, Object>> dbListTables(
            @ToolParam(description = "Database connection id") Long connectionId) {
        Map<String, Object> conn = getConnection(connectionId);
        JdbcTemplate jdbc = getJdbcTemplate(conn);
        return jdbc.queryForList(
                "SELECT TABLE_NAME, TABLE_COMMENT, TABLE_ROWS FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?",
                conn.get("database_name"));
    }

    @Tool(description = "Get all columns for a table.")
    public List<Map<String, Object>> dbGetColumns(
            @ToolParam(description = "Database connection id") Long connectionId,
            @ToolParam(description = "Table name") String tableName) {
        Map<String, Object> conn = getConnection(connectionId);
        JdbcTemplate jdbc = getJdbcTemplate(conn);
        return jdbc.queryForList(
                "SELECT COLUMN_NAME, COLUMN_TYPE, COLUMN_COMMENT, IS_NULLABLE, COLUMN_KEY FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?",
                conn.get("database_name"), tableName);
    }

    @Tool(description = "Get database schema text for selected tables or the whole database.")
    public String dbGetSchema(
            @ToolParam(description = "Database connection id") Long connectionId,
            @ToolParam(description = "Optional table names") List<String> tableNames) {
        List<Map<String, Object>> tables = dbListTables(connectionId);
        StringBuilder sb = new StringBuilder();

        for (Map<String, Object> table : tables) {
            String tableName = (String) table.get("TABLE_NAME");

            if (tableNames != null && !tableNames.isEmpty() && !tableNames.contains(tableName)) {
                continue;
            }

            sb.append("Table ").append(tableName);
            String comment = (String) table.get("TABLE_COMMENT");
            if (comment != null && !comment.isEmpty()) {
                sb.append(" (").append(comment).append(")");
            }
            sb.append("\n");

            List<Map<String, Object>> columns = dbGetColumns(connectionId, tableName);
            for (Map<String, Object> col : columns) {
                sb.append("  - ").append(col.get("COLUMN_NAME"))
                        .append(" ").append(col.get("COLUMN_TYPE"));
                if ("PRI".equals(col.get("COLUMN_KEY"))) {
                    sb.append(" PRIMARY KEY");
                }
                String colComment = (String) col.get("COLUMN_COMMENT");
                if (colComment != null && !colComment.isEmpty()) {
                    sb.append(" (").append(colComment).append(")");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @Tool(description = "Execute one read-only SQL query and return result rows. Statements that modify schema or data are not allowed.")
    public Map<String, Object> dbExecute(
            @ToolParam(description = "Database connection id") Long connectionId,
            @ToolParam(description = "SQL statement") String sql) {
        String validationError = validateReadOnlyQuery(sql);
        if (validationError != null) {
            log.warn("Rejected non-read-only SQL. connectionId={}, sql={}, reason={}", connectionId, sql, validationError);
            return Map.of("error", true, "message", validationError);
        }

        Map<String, Object> conn = getConnection(connectionId);
        JdbcTemplate jdbc = getJdbcTemplate(conn);

        try {
            return jdbc.execute((StatementCallback<Map<String, Object>>) statement -> {
                try {
                    boolean hasResultSet = statement.execute(sql);
                    if (hasResultSet) {
                        try (ResultSet resultSet = statement.getResultSet()) {
                            return buildQueryResult(resultSet);
                        }
                    }

                    int affected = statement.getUpdateCount();
                    return Map.of(
                            "affectedRows", affected,
                            "message", "SQL executed successfully, affected rows: " + affected
                    );
                } catch (SQLException e) {
                    log.warn("dbExecute failed. connectionId={}, sql={}", connectionId, sql, e);
                    return Map.of("error", true, "message", e.getMessage());
                }
            });
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            String message = root.getMessage() != null ? root.getMessage() : e.getMessage();
            log.warn("dbExecute wrapped failure. connectionId={}, sql={}", connectionId, sql, e);
            return Map.of("error", true, "message", message);
        }
    }

    private Map<String, Object> buildQueryResult(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        List<String> columns = new ArrayList<>();
        for (int index = 1; index <= metaData.getColumnCount(); index++) {
            columns.add(metaData.getColumnLabel(index));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        while (resultSet.next()) {
            Map<String, Object> row = new HashMap<>();
            for (String column : columns) {
                row.put(column, resultSet.getObject(column));
            }
            rows.add(row);
        }

        return Map.of(
                "columns", columns,
                "rows", rows,
                "rowCount", rows.size()
        );
    }

    private String validateReadOnlyQuery(String sql) {
        if (!StringUtils.hasText(sql)) {
            return "SQL 不能为空，dbExecute 仅支持只读查询语句。";
        }

        String normalized = normalizeSqlForValidation(sql);
        if (!StringUtils.hasText(normalized)) {
            return "SQL 不能为空，dbExecute 仅支持只读查询语句。";
        }

        String[] statements = normalized.split(";");
        int statementCount = 0;
        String firstStatement = null;
        for (String statement : statements) {
            if (!StringUtils.hasText(statement)) {
                continue;
            }
            statementCount++;
            if (firstStatement == null) {
                firstStatement = statement.trim();
            }
        }

        if (statementCount != 1 || !StringUtils.hasText(firstStatement)) {
            return "仅支持执行一条只读查询语句，不允许提交多条 SQL。";
        }

        var matcher = FIRST_KEYWORD_PATTERN.matcher(firstStatement);
        if (!matcher.find()) {
            return "不支持当前 SQL 类型。dbExecute 仅允许执行 SELECT、WITH、SHOW、DESCRIBE、DESC、EXPLAIN 等只读查询语句。";
        }

        String firstKeyword = matcher.group(1);
        if (!READ_ONLY_SQL_KEYWORDS.contains(firstKeyword)) {
            return "不支持执行 " + firstKeyword + " 语句。dbExecute 仅允许执行只读查询，禁止修改表结构或修改表数据。";
        }

        if (READ_ONLY_DENY_PATTERN.matcher(firstStatement).find()) {
            return "仅支持纯只读查询，不允许使用 FOR UPDATE、LOCK IN SHARE MODE、INTO OUTFILE 等带锁或导出能力的语法。";
        }

        if ("WITH".equals(firstKeyword) && MUTATING_SQL_PATTERN.matcher(firstStatement).find()) {
            return "WITH 语句中仅允许只读查询，不允许通过 CTE 修改表结构或表数据。";
        }

        return null;
    }

    private String normalizeSqlForValidation(String sql) {
        String normalized = BLOCK_COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        normalized = LINE_COMMENT_PATTERN.matcher(normalized).replaceAll(" ");
        return normalized.trim().toUpperCase();
    }

    /**
     * Release cached data sources when the MCP server stops.
     */
    @PreDestroy
    public void shutdown() {
        int closedCount = 0;
        for (HikariDataSource dataSource : dataSourceCache.values()) {
            if (dataSource == null) {
                continue;
            }
            try {
                dataSource.close();
                closedCount++;
            } catch (Exception e) {
                log.warn("Failed to close MCP datasource", e);
            }
        }
        dataSourceCache.clear();
        log.info("Closed {} cached MCP datasource(s).", closedCount);
    }

    private Map<String, Object> getConnection(Long connectionId) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
                "SELECT id, name, type, host, port, database_name, username, password FROM connections WHERE id = ?",
                connectionId);
        if (results.isEmpty()) {
            throw new RuntimeException("Connection not found: " + connectionId);
        }

        Map<String, Object> conn = new HashMap<>(results.get(0));
        String encryptedPassword = (String) conn.get("password");
        conn.put("password", PasswordUtil.decrypt(encryptedPassword));
        return conn;
    }

    private HikariDataSource getOrCreateDataSource(Map<String, Object> conn) {
        Long id = ((Number) conn.get("id")).longValue();
        return dataSourceCache.computeIfAbsent(id, cid -> {
            DatabaseType dbType = DatabaseType.fromValue((String) conn.get("type"));
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbType.buildJdbcUrl(
                    (String) conn.get("host"),
                    ((Number) conn.get("port")).intValue(),
                    (String) conn.get("database_name")));
            config.setDriverClassName(dbType.getDriverClass());
            config.setUsername((String) conn.get("username"));
            config.setPassword((String) conn.get("password"));
            config.setMaximumPoolSize(3);
            config.setMinimumIdle(1);
            config.setPoolName("MCP-DataSource-" + cid);
            return new HikariDataSource(config);
        });
    }

    private JdbcTemplate getJdbcTemplate(Map<String, Object> conn) {
        return new JdbcTemplate(getOrCreateDataSource(conn));
    }
}
