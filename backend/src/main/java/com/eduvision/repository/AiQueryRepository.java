package com.eduvision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.AiQuery;

public interface AiQueryRepository extends JpaRepository<AiQuery, Long> {

    List<AiQuery> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<AiQuery> findByContentId(Long contentId);
}