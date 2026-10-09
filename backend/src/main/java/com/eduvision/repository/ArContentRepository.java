package com.eduvision.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.ArContent;
import com.eduvision.domain.ContentStatus;
import com.eduvision.domain.Subject;
import com.eduvision.domain.User;

public interface ArContentRepository extends JpaRepository<ArContent, Long> {

    Optional<ArContent> findByMarkerId(String markerId);

    boolean existsByMarkerId(String markerId);

    List<ArContent> findByStatus(ContentStatus status);

    List<ArContent> findByStatusOrderByIdAsc(ContentStatus status);

    List<ArContent> findBySubject(Subject subject);

    List<ArContent> findByCreatedById(Long userId);

    List<ArContent> findByCreatedBy(User createdBy);

    List<ArContent> findByCreatedByOrderByIdAsc(User createdBy);

    List<ArContent> findByCreatedByIdOrderByIdDesc(Long userId);

    List<ArContent> findAllByOrderByCreatedAtDesc();
}