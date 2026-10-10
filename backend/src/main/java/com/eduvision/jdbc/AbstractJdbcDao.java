package com.eduvision.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Abstract base class for JDBC DAOs - inheritance + polymorphism + the template-method pattern.
 *
 * <p>The whole JDBC lifecycle (open a connection, prepare a statement, run it, map the
 * {@link ResultSet}, close everything with try-with-resources) lives here <em>once</em>. Concrete
 * DAOs only declare what is genuinely table-specific through the abstract hooks below, and the
 * inherited {@code findAll/findById/save/count} methods call them polymorphically.
 *
 * <p>Every {@link SQLException} is wrapped in a {@link JdbcDaoException}.
 *
 * @param <T> the row type
 */
public abstract class AbstractJdbcDao<T> implements GenericDao<T> {

    /** The shared factory that opens connections against the application datasource. */
    protected final ConnectionFactory connections;

    protected AbstractJdbcDao(ConnectionFactory connections) {
        this.connections = connections;
    }

    // ────────────────────────────────────────────── abstract hooks (polymorphic dispatch)

    /** The physical table this DAO operates on. */
    protected abstract String tableName();

    /** Maps the current {@link ResultSet} row to a {@code T}. */
    protected abstract T mapRow(ResultSet rs) throws SQLException;

    /** The insertable (non-identity) column names, in bind order. */
    protected abstract String[] insertColumns();

    /** Binds the insert values for {@code entity}, starting at parameter index 1. */
    protected abstract void bindInsert(PreparedStatement ps, T entity) throws SQLException;

    /** Returns a copy of {@code entity} carrying the database-generated id. */
    protected abstract T withId(T entity, long id);

    // ────────────────────────────────────────────────────────────── template helpers

    /** Opens a fresh connection (template method). */
    protected Connection open() {
        return connections.open();
    }

    /** Best-effort close used by non try-with-resources callers; real errors are reported by the caller. */
    protected void close(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // closing is best-effort
            }
        }
    }

    // ──────────────────────────────────────────────────────────────── GenericDao

    @Override
    public List<T> findAll() {
        String sql = "SELECT * FROM " + tableName() + " ORDER BY id";
        List<T> rows = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new JdbcDaoException("findAll() failed on table " + tableName(), e);
        }
        return rows;
    }

    @Override
    public Optional<T> findById(Long id) {
        String sql = "SELECT * FROM " + tableName() + " WHERE id = ?";
        try (Connection connection = open();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new JdbcDaoException("findById(" + id + ") failed on table " + tableName(), e);
        }
    }

    @Override
    public T save(T entity) {
        String[] columns = insertColumns();
        StringBuilder sql = new StringBuilder("INSERT INTO ")
                .append(tableName())
                .append(" (")
                .append(String.join(", ", columns))
                .append(") VALUES (");
        for (int i = 0; i < columns.length; i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(')');

        try (Connection connection = open();
             PreparedStatement ps = connection.prepareStatement(sql.toString(),
                     Statement.RETURN_GENERATED_KEYS)) {
            bindInsert(ps, entity);
            ps.executeUpdate();
            long id = -1L;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    id = keys.getLong(1);
                }
            }
            return withId(entity, id);
        } catch (SQLException e) {
            throw new JdbcDaoException("save() failed on table " + tableName(), e);
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM " + tableName();
        try (Connection connection = open();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new JdbcDaoException("count() failed on table " + tableName(), e);
        }
    }
}
