package com.doof.passwordmanager.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionManager {

    private static final String URL = "jdbc:mysql://localhost:3306/Password_Manager?sslMode=VERIFY_IDENTITY";
    private static final String USER = "appuser";
    private static final String PASSWORD = "appuser";

    private ConnectionManager() {}

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver not found. Add mysql-connector-j to the project.", e);
        }
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
