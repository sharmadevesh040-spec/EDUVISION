package com.eduvision.jdbc;

/**
 * Immutable POJO representing one attendance row stored by {@link AttendanceJdbcDao}.
 *
 * <p>{@code id} is {@code null} until the row has been inserted (the database assigns it), after
 * which {@link #withId(long)} returns an updated copy.
 */
public final class AttendanceRecord {

    private final Long id;
    private final String studentName;
    private final boolean present;

    public AttendanceRecord(Long id, String studentName, boolean present) {
        this.id = id;
        this.studentName = studentName;
        this.present = present;
    }

    public Long getId() {
        return id;
    }

    public String getStudentName() {
        return studentName;
    }

    public boolean isPresent() {
        return present;
    }

    /** @return a copy of this record carrying the given (database-generated) id. */
    public AttendanceRecord withId(long newId) {
        return new AttendanceRecord(newId, studentName, present);
    }

    @Override
    public String toString() {
        return "AttendanceRecord{id=" + id + ", studentName='" + studentName + "', present=" + present + '}';
    }
}
