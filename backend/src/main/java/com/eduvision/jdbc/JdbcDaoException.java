package com.eduvision.jdbc;

/**
 * Unchecked, domain-specific exception for every JDBC DAO operation.
 *
 * <p>Checked {@link java.sql.SQLException}s thrown by the JDBC API are wrapped in this type at the
 * DAO boundary (exception handling), so callers deal with one meaningful runtime exception instead
 * of the raw, low-level SQL error.
 */
public class JdbcDaoException extends RuntimeException {

    public JdbcDaoException(String message) {
        super(message);
    }

    public JdbcDaoException(String message, Throwable cause) {
        super(message, cause);
    }
}
