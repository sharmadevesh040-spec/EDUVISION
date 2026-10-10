package com.eduvision.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Opens plain-JDBC connections with {@link DriverManager} against <em>the same datasource the
 * Spring application uses</em>.
 *
 * <p>The connection settings are read from the Spring {@link Environment}:
 * {@code spring.datasource.url}, {@code spring.datasource.username} and
 * {@code spring.datasource.password}. Because those keys resolve to the active profile, the exact
 * same code talks to H2 by default and to PostgreSQL under the {@code postgres} profile - which is
 * what proves real JDBC connectivity to the SQL backend.
 *
 * <p>A second constructor accepts an explicit URL/credentials so tests can target an in-memory
 * database without a Spring context.
 */
@Component
public class ConnectionFactory {

    static {
        // Register the drivers explicitly for the classic DriverManager path.
        loadDriver("org.h2.Driver");
        loadDriver("org.postgresql.Driver");
    }

    private final String url;
    private final String username;
    private final String password;

    /** Spring constructor - reads the live datasource settings from the environment. */
    @Autowired
    public ConnectionFactory(Environment environment) {
        this(
                environment.getProperty("spring.datasource.url"),
                environment.getProperty("spring.datasource.username", ""),
                environment.getProperty("spring.datasource.password", ""));
    }

    /** Explicit constructor used by unit tests (and any non-Spring caller). */
    public ConnectionFactory(String url, String username, String password) {
        if (url == null || url.isBlank()) {
            throw new JdbcDaoException("spring.datasource.url is not configured");
        }
        this.url = url;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
    }

    /** Opens a brand new JDBC connection. Callers must close it (the DAO layer uses try-with-resources). */
    public Connection open() {
        try {
            return DriverManager.getConnection(url, username, password);
        } catch (SQLException e) {
            throw new JdbcDaoException("Unable to open a JDBC connection to " + url, e);
        }
    }

    public String url() {
        return url;
    }

    public String username() {
        return username;
    }

    private static void loadDriver(String className) {
        try {
            Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            // Driver not present on the (test) classpath - DriverManager still auto-discovers any
            // driver registered through the service loader, so this is safe to ignore.
        }
    }
}
