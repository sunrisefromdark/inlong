/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.inlong.manager.service.resource.sink.mysql;

import org.apache.inlong.manager.pojo.sink.mysql.MySQLColumnInfo;
import org.apache.inlong.manager.pojo.sink.mysql.MySQLTableInfo;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utils for MySQL JDBC.
 */
public class MySQLJdbcUtils {

    private static final String MYSQL_JDBC_PREFIX = "jdbc:mysql";
    private static final String MYSQL_DRIVER_CLASS = "com.mysql.cj.jdbc.Driver";
    private static final Logger LOGGER = LoggerFactory.getLogger(MySQLJdbcUtils.class);

    /**
     * Get MySQL connection from the url and user.
     *
     * @param url jdbc url, such as jdbc:mysql://host:port/database
     * @param user Username for JDBC URL
     * @param password User password
     * @return {@link Connection}
     * @throws Exception on get connection error
     */
    public static Connection getConnection(String url, String user, String password) throws Exception {
        String host = parseAndValidateURL(url);
        validateHostnameAndPort(host);
        Connection conn = establishDatabaseConnection(url, user, password);
        validateConnection(conn, url);

        return conn;
    }

    /**
     * Parses and validates the JDBC URL, and returns the host part.
     *
     * @param url The JDBC URL
     * @return The host part of the URL
     * @throws Exception If the URL is invalid
     */
    private static String parseAndValidateURL(String url) throws Exception {
        if (!url.startsWith(MYSQL_JDBC_PREFIX)) {
            throw new Exception("MySQL JDBC URL is invalid, it should start with " + MYSQL_JDBC_PREFIX);
        }

        String hostPortPart = url.substring(MYSQL_JDBC_PREFIX.length() + 3);
        String[] hostPortParts = hostPortPart.split("/");
        if (hostPortParts.length < 1) {
            throw new Exception("Invalid MySQL JDBC URL format");
        }

        return hostPortParts[0];
    }

    /**
     * Validates the hostname and port in the JDBC URL.
     *
     * @param host The hostname and port in host:port format
     * @throws Exception If the hostname or port is invalid
     */
    private static void validateHostnameAndPort(String host) throws Exception {
        if (host == null || host.isEmpty()) {
            throw new Exception("Host is empty in MySQL JDBC URL");
        }
        String[] hostPortSplit = host.split(":");
        if (hostPortSplit.length != 2) {
            throw new Exception("Invalid host:port format in MySQL JDBC URL");
        }
        String port = hostPortSplit[1];
        try {
            int portNumber = Integer.parseInt(port);
            if (portNumber < 1 || portNumber > 65535) {
                throw new Exception("Invalid port number in MySQL JDBC URL");
            }
        } catch (NumberFormatException e) {
            throw new Exception("Invalid port number format in MySQL JDBC URL");
        }
    }

    /**
     * Establishes a database connection using the provided URL, username, and password.
     *
     * @param url      The JDBC URL
     * @param user     The username
     * @param password The user's password
     * @return A {@link Connection} object representing the database connection
     * @throws Exception If an error occurs while obtaining the connection
     */
    private static Connection establishDatabaseConnection(String url, String user, String password) throws Exception {
        Connection conn;
        try {
            Class.forName(MYSQL_DRIVER_CLASS);
            conn = DriverManager.getConnection(url, user, password);
        } catch (Exception e) {
            String errorMsg = "Failed to get MySQL connection, please check MySQL JDBC URL, username, or password!";
            LOGGER.error(errorMsg, e);
            throw new Exception(errorMsg + " Other error message: " + e.getMessage());
        }
        return conn;
    }

    /**
     * Validates if the database connection was successfully obtained.
     *
     * @param conn The database connection
     * @param url  The JDBC URL
     * @throws Exception If the connection is null
     */
    private static void validateConnection(Connection conn, String url) throws Exception {
        if (conn == null) {
            throw new Exception("Failed to get MySQL connection, please contact the administrator.");
        }
        LOGGER.info("Successfully obtained MySQL connection for URL: {}", url);
    }

    /**
     * Execute SQL command on MySQL.
     *
     * @param conn JDBC {@link Connection}
     * @param sql SQL to be executed
     * @throws Exception on execute SQL error
     */
    public static void executeSql(final Connection conn, final String sql) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            LOGGER.info("execute sql [{}] success", sql);
        }
    }

    /**
     * Execute batch query SQL on MySQL.
     *
     * @param conn JDBC {@link Connection}
     * @param sqls SQL to be executed
     * @throws Exception on get execute SQL batch error
     */
    public static void executeSqlBatch(final Connection conn, final List<String> sqls) throws Exception {
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            for (String entry : sqls) {
                stmt.execute(entry);
            }
            conn.commit();
            LOGGER.info("execute sql [{}] success", sqls);
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Create MySQL database
     *
     * @param conn JDBC {@link Connection}
     * @param dbName database name
     * @throws Exception on create database error
     */
    public static void createDb(final Connection conn, final String dbName) throws Exception {
        if (!checkDbExist(conn, dbName)) {
            final String createDbSql = MySQLSqlBuilder.buildCreateDbSql(dbName);
            executeSql(conn, createDbSql);
            LOGGER.info("execute sql [{}] success", createDbSql);
        } else {
            LOGGER.info("The database [{}] are exists", dbName);
        }
    }

    /**
     * Check database from the MySQL information_schema.
     *
     * @param conn JDBC {@link Connection}
     * @param dbName database name
     * @return true if table exist, otherwise false
     * @throws Exception on check database exist error
     */
    public static boolean checkDbExist(final Connection conn, final String dbName) throws Exception {
        final String checkDbSql = MySQLSqlBuilder.getCheckDatabase(dbName);
        try (Statement stmt = conn.createStatement();
                ResultSet resultSet = stmt.executeQuery(checkDbSql)) {
            if (Objects.nonNull(resultSet)) {
                if (resultSet.next()) {
                    LOGGER.info("check db exist for db={}, result=true", dbName);
                    return true;
                }
            }
        }
        LOGGER.info("check db exist for db={}, result=false", dbName);
        return false;
    }

    /**
     * Create MySQL table by MySQLTableInfo
     *
     * @param conn JDBC {@link Connection}
     * @param tableInfo table info  {@link MySQLTableInfo}
     * @throws Exception on create table error
     */
    public static void createTable(final Connection conn, final MySQLTableInfo tableInfo) throws Exception {
        if (checkTablesExist(conn, tableInfo.getDbName(), tableInfo.getTableName())) {
            LOGGER.info("The table [{}] are exists", tableInfo.getTableName());
        } else {
            final String createTableSql = MySQLSqlBuilder.buildCreateTableSql(tableInfo);
            executeSql(conn, createTableSql);
            LOGGER.info("execute sql [{}] success", createTableSql);
        }
    }

    /**
     * Check tables from the MySQL information_schema.
     *
     * @param conn JDBC {@link Connection}
     * @param dbName database name
     * @param tableName table name
     * @return true if table exist, otherwise false
     * @throws Exception on check table exist error
     */
    public static boolean checkTablesExist(final Connection conn, final String dbName, final String tableName)
            throws Exception {
        boolean result = false;
        final String checkTableSql = MySQLSqlBuilder.getCheckTable(dbName, tableName);
        try (Statement stmt = conn.createStatement();
                ResultSet resultSet = stmt.executeQuery(checkTableSql)) {
            if (Objects.nonNull(resultSet)) {
                if (resultSet.next()) {
                    result = true;
                }
            }
        }
        LOGGER.info("check table exist for db={} table={}, result={}", dbName, tableName, result);
        return result;
    }

    /**
     * Check whether the column exists in the MySQL table.
     *
     * @param conn JDBC Connection  {@link Connection}
     * @param dbName database name
     * @param tableName table name
     * @param column table column name
     * @return true if column exist in the table, otherwise false
     * @throws Exception on check column exist error
     */
    public static boolean checkColumnExist(final Connection conn, final String dbName, final String tableName,
            final String column) throws Exception {
        boolean result = false;
        final String checkTableSql = MySQLSqlBuilder.getCheckColumn(dbName, tableName, column);
        try (Statement stmt = conn.createStatement();
                ResultSet resultSet = stmt.executeQuery(checkTableSql)) {
            if (Objects.nonNull(resultSet)) {
                if (resultSet.next()) {
                    result = true;
                }
            }
        }
        LOGGER.info("check column exist for db={} table={}, result={} column={}", dbName, tableName, result, column);
        return result;
    }

    /**
     * Query all MySQL table columns by the given tableName.
     *
     * @param conn JDBC {@link Connection}
     * @param dbName database name
     * @param tableName table name
     * @return {@link List}
     * @throws Exception on get columns error
     */
    public static List<MySQLColumnInfo> getColumns(final Connection conn, final String dbName, final String tableName)
            throws Exception {
        final String querySql = MySQLSqlBuilder.buildDescTableSql(dbName, tableName);
        final List<MySQLColumnInfo> columnList = new ArrayList<>();

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(querySql)) {
            if (Objects.nonNull(rs)) {
                while (rs.next()) {
                    MySQLColumnInfo columnInfo = new MySQLColumnInfo(rs.getString(1),
                            rs.getString(2), rs.getString(3));
                    columnList.add(columnInfo);
                }
            }
        }
        return columnList;
    }

    /**
     * Add columns for MySQL table.
     *
     * @param conn JDBC Connection  {@link Connection}
     * @param dbName database name
     * @param tableName table name
     * @param columns columns to be added
     * @throws Exception on add columns error
     */
    public static void addColumns(final Connection conn, final String dbName, final String tableName,
            final List<MySQLColumnInfo> columns) throws Exception {
        final List<MySQLColumnInfo> columnInfos = Lists.newArrayList();

        for (MySQLColumnInfo columnInfo : columns) {
            if (!checkColumnExist(conn, dbName, tableName, columnInfo.getName())) {
                columnInfos.add(columnInfo);
            }
        }
        final List<String> addColumnSql = MySQLSqlBuilder.buildAddColumnsSql(dbName, tableName, columnInfos);
        executeSqlBatch(conn, addColumnSql);
    }

}