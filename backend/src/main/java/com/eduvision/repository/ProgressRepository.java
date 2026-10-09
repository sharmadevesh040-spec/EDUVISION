package com.eduvision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.ArContent;
import com.eduvision.domain.Assignment;
import com.eduvision.domain.Progress;
import com.eduvision.domain.User;

public interface ProgressRepository extends JpaRepository<Progress, Long> {

    List<Progress> findByStudentId(Long studentId);

    List<Progress> findByStudentOrderByLastAccessedDesc(User student);

    List<Progress> findByContentId(Long contentId);

    List<Progress> findByAssignmentId(Long assignmentId);

    Optional<Progress> findByStudentIdAndContentId(Long studentId, Long contentId);

    Optional<Progress> findByStudentAndContent(User student, ArContent content);

    Optional<Progress> findByStudentIdAndContentIdAndAssignmentId(Long studentId, Long contentId, Long assignmentId);

    Optional<Progress> findByStudentAndContentAndAssignment(User student, ArContent content, Assignment assignment);

    long countByStudentId(Long studentId);
}