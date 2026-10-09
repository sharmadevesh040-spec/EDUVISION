package com.eduvision.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eduvision.domain.ArContent;
import com.eduvision.domain.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    List<Feedback> findByContentId(Long contentId);

    List<Feedback> findByContentOrderByIdDesc(ArContent content);

    List<Feedback> findByDeveloperId(Long developerId);

    long countByContentId(Long contentId);

    @Query("select avg(f.rating) from Feedback f where f.content = :content")
    Double averageRating(@Param("content") ArContent content);
}