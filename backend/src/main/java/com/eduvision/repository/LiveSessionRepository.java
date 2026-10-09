package com.eduvision.repository;

import com.eduvision.domain.LiveSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveSessionRepository extends JpaRepository<LiveSession, Long> {

    Optional<LiveSession> findByJoinCode(String joinCode);

    List<LiveSession> findByActiveTrue();
}