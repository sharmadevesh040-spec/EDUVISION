package com.eduvision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.EduClass;
import com.eduvision.domain.User;

public interface EduClassRepository extends JpaRepository<EduClass, Long> {

    List<EduClass> findByTeacherId(Long teacherId);

    List<EduClass> findByTeacher(User teacher);

    List<EduClass> findByTeacherOrderByIdAsc(User teacher);

    Optional<EduClass> findByName(String name);

    boolean existsByName(String name);
}