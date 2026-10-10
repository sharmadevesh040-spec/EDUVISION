package com.eduvision.jdbc;

import java.util.List;
import java.util.Optional;

/**
 * Generic data-access contract (generics + interface).
 *
 * <p>Every JDBC DAO implements this interface, so the persistence operations are declared once and
 * reused for any entity type {@code T}.
 *
 * @param <T> the row type this DAO maps to
 */
public interface GenericDao<T> {

    /** @return every row, in primary-key order. */
    List<T> findAll();

    /** @return the row with the given id, or {@link Optional#empty()} when it does not exist. */
    Optional<T> findById(Long id);

    /** @return the persisted entity (with its database-generated id populated). */
    T save(T entity);

    /** @return the number of rows in the table. */
    long count();
}
