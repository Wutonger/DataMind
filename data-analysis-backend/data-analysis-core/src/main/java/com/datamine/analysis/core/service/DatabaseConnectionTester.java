package com.datamine.analysis.core.service;

import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.core.util.PasswordEncoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.DriverManager;
import java.sql.SQLException;

@Slf4j
@Service
@RequiredArgsConstructor
public class DatabaseConnectionTester {

    private final PasswordEncoder passwordEncoder;

    public boolean testConnection(Connection connection) {
        return doTest(connection, passwordEncoder.decode(connection.getPassword()));
    }

    public boolean testRawConnection(Connection connection) {
        return doTest(connection, passwordEncoder.decode(connection.getPassword()));
    }

    public boolean testEncryptedConnection(Connection connection) {
        return doTest(connection, passwordEncoder.decode(connection.getPassword()));
    }

    private boolean doTest(Connection connection, String password) {
        String url = buildJdbcUrl(connection);
        String username = connection.getUsername();

        try {
            Class.forName(getDriverClass(connection.getType()));
            try (java.sql.Connection conn = DriverManager.getConnection(url, username, password)) {
                return conn.isValid(5);
            }
        } catch (ClassNotFoundException e) {
            log.error("JDBC Driver not found: {}", connection.getType(), e);
            return false;
        } catch (SQLException e) {
            log.error("Connection test failed: {}", e.getMessage());
            return false;
        }
    }

    private String buildJdbcUrl(Connection connection) {
        String type = connection.getType().toLowerCase();
        String host = connection.getHost();
        int port = connection.getPort();
        String database = connection.getDatabase();

        return switch (type) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                    host, port, database);
            case "doris" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
                    host, port, database);
            case "postgresql" -> String.format("jdbc:postgresql://%s:%d/%s", host, port, database);
            case "sqlserver" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, database);
            case "oracle" -> String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, database);
            default -> throw new IllegalArgumentException("Unsupported database type: " + type);
        };
    }

    private String getDriverClass(String type) {
        return switch (type.toLowerCase()) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "doris" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            case "sqlserver" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case "oracle" -> "oracle.jdbc.OracleDriver";
            default -> throw new IllegalArgumentException("Unsupported database type: " + type);
        };
    }
}
