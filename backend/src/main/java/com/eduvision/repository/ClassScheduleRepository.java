package com.eduvision.repository;

import com.eduvision.domain.ClassSchedule;
import com.eduvision.domain.ClassSchedule.DayOfWeek;
import com.eduvision.domain.EduClass;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link ClassSchedule}.
 *
 * <ul>
 *   <li>{@link #findByEduClassOrderByDayOfWeekAscStartTimeAsc} — full timetable for one class,
 *       sorted into weekly reading order.</li>
 *   <li>{@link #findByDayOfWeek} — all slots across every class on a given day
 *       (useful for a school-wide daily view).</li>
 *   <li>{@link #existsByEduClassAndDayOfWeekAndStartTime} — duplicate guard used
 *       by the controller before persisting a new slot.</li>
 * </ul>
 */
public interface ClassScheduleRepository extends JpaRepository<ClassSchedule, Long> {

    /** Returns all schedule slots for a class sorted Monday→Friday then by start time. */
    List<ClassSchedule> findByEduClassOrderByDayOfWeekAscStartTimeAsc(EduClass eduClass);

    /** Returns all schedule slots for a class identified by its ID. */
    List<ClassSchedule> findByEduClassIdOrderByDayOfWeekAscStartTimeAsc(Long classId);

    /** Returns every slot scheduled on a particular day (all classes). */
    List<ClassSchedule> findByDayOfWeek(DayOfWeek dayOfWeek);

    /** True if the exact (class, day, startTime) combination already exists. */
    boolean existsByEduClassAndDayOfWeekAndStartTime(
            EduClass eduClass, DayOfWeek dayOfWeek, java.time.LocalTime startTime);

    /** How many slots a class currently has (used by the ClassController DTO). */
    int countByEduClassId(Long classId);
}
