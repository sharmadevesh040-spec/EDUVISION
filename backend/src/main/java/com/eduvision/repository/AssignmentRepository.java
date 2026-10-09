package com.eduvision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.Assignment;
import com.eduvision.domain.EduClass;
import com.eduvision.domain.User;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {

    List<Assignment> findByEduClassId(Long eduClassId);

    List<Assignment> findByEduClass(EduClass eduClass);

    List<Assignment> findByEduClassOrderByIdAsc(EduClass eduClass);

    List<Assignment> findByContentId(Long contentId);

    List<Assignment> findByTeacherId(Long teacherId);

    List<Assignment> findByTeacherOrderByIdAsc(User teacher);

    List<Assignment> findByEduClassIdOrderByCreatedAtDesc(Long eduClassId);
}