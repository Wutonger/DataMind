package com.datamine.analysis.core.datasource;

import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.common.enums.DatabaseType;
import com.datamine.analysis.core.util.PasswordEncoder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicDataSource {

    private final PasswordEncoder passwordEncoder;
    private final Map<Long, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    public HikariDataSource getDataSource(Connection dbConnection) {
        return dataSourceCache.computeIfAbsent(dbConnection.getId(), id -> createDataSource(dbConnection));
    }

    public HikariDataSource getDataSource(Long connectionId) {
        return dataSourceCache.get(connectionId);
    }

    public void addDataSource(Long connectionId, HikariDataSource dataSource) {
        dataSourceCache.put(connectionId, dataSource);
    }

    public void removeDataSource(Long connectionId) {
        HikariDataSource ds = dataSourceCache.remove(connectionId);
        if (ds != null) {
            ds.close();
        }
    }

    public boolean testConnection(Connection dbConnection) {
        HikariDataSource ds = null;
        try {
            ds = createDataSource(dbConnection);
            try (java.sql.Connection conn = ds.getConnection()) {
                return conn.isValid(5);
            }
        } catch (SQLException e) {
            log.error("Connection test failed", e);
            return false;
        } finally {
            if (ds != null) {
                ds.close();
            }
        }
    }

    private HikariDataSource createDataSource(Connection conn) {
        DatabaseType dbType = DatabaseType.fromValue(conn.getType());
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbType.buildJdbcUrl(conn.getHost(), conn.getPort(), conn.getDatabase()));
        config.setDriverClassName(dbType.getDriverClass());
        config.setUsername(conn.getUsername());
        config.setPassword(passwordEncoder.decode(conn.getPassword()));
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        config.setPoolName("DataSource-" + conn.getId());

        return new HikariDataSource(config);
    }

    public void closeAll() {
        dataSourceCache.values().forEach(HikariDataSource::close);
        dataSourceCache.clear();
    }
}
