package com.eduvision.web;

import com.eduvision.domain.ClassSchedule;
import com.eduvision.domain.ClassSchedule.DayOfWeek;
import com.eduvision.domain.EduClass;
import com.eduvision.domain.Role;
import com.eduvision.domain.User;
import com.eduvision.repository.ClassScheduleRepository;
import com.eduvision.repository.EduClassRepository;
import com.eduvision.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

/**
 * REST endpoints for managing the weekly timetable of a class.
 *
 * <pre>
 * GET    /api/classes/{classId}/schedule            → list all slots for a class
 * POST   /api/classes/{classId}/schedule            → add a slot        (TEACHER only)
 * PUT    /api/classes/{classId}/schedule/{id}       → update a slot     (TEACHER only)
 * DELETE /api/classes/{classId}/schedule/{id}       → remove a slot     (TEACHER only)
 * GET    /api/schedule/day/{day}                    → all slots on a day (TEACHER only)
 * </pre>
 *
 * Students can read their own class timetable via the first GET endpoint.
 * Teachers can only modify schedules for classes they own.
 */
@RestController
public class ClassScheduleController {

    private final ClassScheduleRepository schedules;
    private final EduClassRepository classes;
    private final CurrentUser currentUser;

    public ClassScheduleController(ClassScheduleRepository schedules,
                                   EduClassRepository classes,
                                   CurrentUser currentUser) {
        this.schedules = schedules;
        this.classes = classes;
        this.currentUser = currentUser;
    }

    // ─────────────────────────────────────────────────── request / response DTOs

    /**
     * Request body for creating or updating a schedule slot.
     *
     * @param dayOfWeek  e.g. "MONDAY"
     * @param startTime  ISO-8601 local time string, e.g. "09:00"
     * @param endTime    ISO-8601 local time string, e.g. "10:00"
     * @param room       optional room label
     * @param notes      optional free-text note
     */
    public record ScheduleRequest(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull String startTime,
            @NotNull String endTime,
            String room,
            String notes
    ) {}

    /** Response DTO — never exposes internal JPA entities directly. */
    public record ScheduleDto(
            Long id,
            Long classId,
            String className,
            String grade,
            String section,
            DayOfWeek dayOfWeek,
            String startTime,
            String endTime,
            String room,
            String notes
    ) {}

    // ──────────────────────────────────────────────────────────── GET (list by class)

    /**
     * Returns the full weekly timetable for a class.
     * Accessible to the owning teacher AND to any enrolled student.
     */
    @GetMapping("/api/classes/{classId}/schedule")
    public List<ScheduleDto> listForClass(@PathVariable Long classId) {
        EduClass c = requireClass(classId);
        checkReadAccess(c);
        return schedules
                .findByEduClassIdOrderByDayOfWeekAscStartTimeAsc(classId)
                .stream()
                // dayOfWeek is stored as a STRING (@Enumerated(EnumType.STRING)), so the SQL
                // ORDER BY sorts the day alphabetically (FRIDAY before MONDAY). Re-sort
                // chronologically here so the timetable reads Monday -> Sunday.
                .sorted(java.util.Comparator.comparingInt((ClassSchedule s) -> s.dayOfWeek.ordinal())
                        .thenComparing(s -> s.startTime))
                .map(this::toDto)
                .toList();
    }

    // ──────────────────────────────────────────────────────────── POST (create slot)

    @PostMapping("/api/classes/{classId}/schedule")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ScheduleDto> create(@PathVariable Long classId,
                                              @Valid @RequestBody ScheduleRequest req) {
        EduClass c = requireClass(classId);
        checkTeacherOwns(c);

        LocalTime start = parseTime(req.startTime(), "startTime");
        LocalTime end   = parseTime(req.endTime(),   "endTime");
        validateTimeRange(start, end);

        if (schedules.existsByEduClassAndDayOfWeekAndStartTime(c, req.dayOfWeek(), start)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A slot already exists for " + req.dayOfWeek() + " at " + req.startTime());
        }

        ClassSchedule slot = schedules.save(
                new ClassSchedule(c, req.dayOfWeek(), start, end,
                        trimOrNull(req.room()), trimOrNull(req.notes())));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(slot));
    }

    // ──────────────────────────────────────────────────────────── PUT (update slot)

    @PutMapping("/api/classes/{classId}/schedule/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ScheduleDto update(@PathVariable Long classId,
                              @PathVariable Long id,
                              @Valid @RequestBody ScheduleRequest req) {
        EduClass c = requireClass(classId);
        checkTeacherOwns(c);
        ClassSchedule slot = requireSlot(id, classId);

        LocalTime start = parseTime(req.startTime(), "startTime");
        LocalTime end   = parseTime(req.endTime(),   "endTime");
        validateTimeRange(start, end);

        // Only block if a *different* slot conflicts with the new day+time
        boolean conflict = schedules
                .existsByEduClassAndDayOfWeekAndStartTime(c, req.dayOfWeek(), start);
        if (conflict && !slot.dayOfWeek.equals(req.dayOfWeek())
                     && !slot.startTime.equals(start)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Another slot already occupies " + req.dayOfWeek() + " at " + req.startTime());
        }

        slot.dayOfWeek = req.dayOfWeek();
        slot.startTime = start;
        slot.endTime   = end;
        slot.room      = trimOrNull(req.room());
        slot.notes     = trimOrNull(req.notes());
        return toDto(schedules.save(slot));
    }

    // ──────────────────────────────────────────────────────────── DELETE

    @DeleteMapping("/api/classes/{classId}/schedule/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<Void> delete(@PathVariable Long classId,
                                       @PathVariable Long id) {
        EduClass c = requireClass(classId);
        checkTeacherOwns(c);
        ClassSchedule slot = requireSlot(id, classId);
        schedules.delete(slot);
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────── GET (by day)

    /**
     * Returns all slots across every class scheduled on a given day.
     * Useful for a school-wide daily view (TEACHER only).
     *
     * @param day  e.g. "MONDAY"
     */
    @GetMapping("/api/schedule/day/{day}")
    @PreAuthorize("hasRole('TEACHER')")
    public List<ScheduleDto> byDay(@PathVariable String day) {
        DayOfWeek dow;
        try {
            dow = DayOfWeek.valueOf(day.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown day '" + day + "'. Use MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY or SUNDAY.");
        }
        return schedules.findByDayOfWeek(dow).stream().map(this::toDto).toList();
    }

    // ──────────────────────────────────────────────────────────── helpers

    private EduClass requireClass(Long classId) {
        return classes.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Class not found"));
    }

    private ClassSchedule requireSlot(Long slotId, Long classId) {
        ClassSchedule slot = schedules.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule slot not found"));
        if (!slot.eduClass.id.equals(classId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule slot not found in this class");
        }
        return slot;
    }

    /**
     * Teachers can read only their own class timetable;
     * students can read their enrolled class's timetable.
     */
    private void checkReadAccess(EduClass c) {
        User me = currentUser.get();
        if (me.role == Role.TEACHER && !c.teacher.id.equals(me.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not teach this class");
        }
        // STUDENT and DEVELOPER roles have unrestricted read access to any class schedule.
    }

    /** Throws 403 if the current teacher does not own this class. */
    private void checkTeacherOwns(EduClass c) {
        User me = currentUser.get();
        if (!c.teacher.id.equals(me.id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not teach this class");
        }
    }

    private LocalTime parseTime(String value, String fieldName) {
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    fieldName + " must be a valid time in HH:mm or HH:mm:ss format, got: " + value);
        }
    }

    private void validateTimeRange(LocalTime start, LocalTime end) {
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "endTime must be after startTime");
        }
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private ScheduleDto toDto(ClassSchedule s) {
        return new ScheduleDto(
                s.id,
                s.eduClass.id,
                s.eduClass.name,
                s.eduClass.grade,
                s.eduClass.section,
                s.dayOfWeek,
                s.startTime.toString(),
                s.endTime.toString(),
                s.room,
                s.notes
        );
    }
}
