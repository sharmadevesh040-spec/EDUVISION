package com.eduvision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.ArContent;
import com.eduvision.domain.ContentPart;

public interface ContentPartRepository extends JpaRepository<ContentPart, Long> {

    List<ContentPart> findByContentIdOrderByOrderIndexAsc(Long contentId);

    List<ContentPart> findByContentOrderByOrderIndexAsc(ArContent content);

    List<ContentPart> findByContentId(Long contentId);

    long countByContentId(Long contentId);

    long countByContent(ArContent content);

    void deleteByContentId(Long contentId);
}