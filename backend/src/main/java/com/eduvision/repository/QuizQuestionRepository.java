package com.eduvision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eduvision.domain.ArContent;
import com.eduvision.domain.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByContentIdOrderByOrderIndexAsc(Long contentId);

    List<QuizQuestion> findByContentOrderByOrderIndexAsc(ArContent content);

    long countByContentId(Long contentId);

    long countByContent(ArContent content);

    List<QuizQuestion> findByTopic(String topic);
}