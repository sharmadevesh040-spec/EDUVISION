package com.eduvision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.ArContent;
import com.eduvision.domain.QuizResult;
import com.eduvision.domain.User;

public interface QuizResultRepository extends JpaRepository<QuizResult, Long> {

    List<QuizResult> findByStudentId(Long studentId);

    List<QuizResult> findByStudentOrderBySubmittedAtDesc(User student);

    List<QuizResult> findByContentId(Long contentId);

    List<QuizResult> findByContentOrderBySubmittedAtDesc(ArContent content);

    List<QuizResult> findByAssignmentId(Long assignmentId);
}