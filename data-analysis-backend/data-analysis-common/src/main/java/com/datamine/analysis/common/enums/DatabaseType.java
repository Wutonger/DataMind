package com.datamine.analysis.common.enums;

import lombok.Getter;

@Getter
public enum DatabaseType {

    MYSQL("mysql", "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"),
    DORIS("doris", "com.mysql.cj.jdbc.Driver",
            "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true"),
    POSTGRESQL("postgresql", "org.postgresql.Driver",
            "jdbc:postgresql://%s:%d/%s"),
    SQLSERVER("sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
            "jdbc:sqlserver://%s:%d;databaseName=%s"),
    ORACLE("oracle", "oracle.jdbc.OracleDriver",
            "jdbc:oracle:thin:@%s:%d:%s");

    private final String value;
    private final String driverClass;
    private final String urlTemplate;

    DatabaseType(String value, String driverClass, String urlTemplate) {
        this.value = value;
        this.driverClass = driverClass;
        this.urlTemplate = urlTemplate;
    }

    public static DatabaseType fromValue(String value) {
        for (DatabaseType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported database type: " + value);
    }

    public boolean usesMysqlProtocol() {
        return this == MYSQL || this == DORIS;
    }

    public String buildJdbcUrl(String host, int port, String database) {
        return String.format(urlTemplate, host, port, database);
    }
}
