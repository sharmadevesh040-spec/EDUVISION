package com.eduvision.repository;

import com.eduvision.domain.LiveParticipant;
import com.eduvision.domain.LiveSession;
import com.eduvision.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveParticipantRepository extends JpaRepository<LiveParticipant, Long> {

    List<LiveParticipant> findBySession(LiveSession session);

    Optional<LiveParticipant> findBySessionAndStudent(LiveSession session, User student);
}