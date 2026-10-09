package com.eduvision.domain;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A recurring weekly time-slot for an {@link EduClass}.
 *
 * <p>One class can have many schedule entries (e.g. Monday 09:00–10:00 and
 * Wednesday 11:00–12:00).  The combination of (edu_class_id, day_of_week,
 * start_time) is unique to prevent accidental duplicates.
 *
 * <p>Times are stored as {@link LocalTime} (wall-clock, no timezone) because
 * a school schedule is always expressed in the school's local time.  Hibernate
 * maps this to SQL {@code TIME} on both H2 and PostgreSQL.
 */
@Entity
@Table(
    name = "class_schedules",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_schedule_class_day_start",
        columnNames = {"edu_class_id", "day_of_week", "start_time"}
    )
)
public class ClassSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** The class this slot belongs to. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "edu_class_id", nullable = false)
    public EduClass eduClass;

    /**
     * Day of the week this slot recurs on.
     * Stored as a string (e.g. {@code "MONDAY"}) so the value is readable
     * directly in the database.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    public DayOfWeek dayOfWeek;

    /** Wall-clock start time in the school's local timezone. */
    @Column(name = "start_time", nullable = false)
    public LocalTime startTime;

    /** Wall-clock end time.  Must be strictly after {@code startTime}. */
    @Column(name = "end_time", nullable = false)
    public LocalTime endTime;

    /**
     * Physical or virtual location (e.g. "Room 204", "Lab B", "Google Meet").
     * Optional — null means no room has been assigned yet.
     */
    @Column(name = "room", length = 120)
    public String room;

    /**
     * Free-form note for the teacher (e.g. "Lab session", "Bring textbook").
     * Optional.
     */
    @Column(name = "notes", length = 500)
    public String notes;

    public ClassSchedule() {
    }

    public ClassSchedule(EduClass eduClass, DayOfWeek dayOfWeek,
                         LocalTime startTime, LocalTime endTime,
                         String room, String notes) {
        this.eduClass = eduClass;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
        this.notes = notes;
    }

    // ---------------------------------------------------------------- getters / setters

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public EduClass getEduClass() { return eduClass; }
    public void setEduClass(EduClass eduClass) { this.eduClass = eduClass; }

    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    // ---------------------------------------------------------------- enum

    /**
     * Days of the week a schedule slot can fall on.
     * Only MONDAY–FRIDAY are included because this is a school timetable;
     * add SATURDAY/SUNDAY if weekend sessions are ever needed.
     */
    public enum DayOfWeek {
        MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
    }
}
