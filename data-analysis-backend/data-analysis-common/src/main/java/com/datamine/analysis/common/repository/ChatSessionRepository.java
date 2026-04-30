package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {
    List<ChatSession> findByConnectionIdOrderByUpdatedAtDesc(Long connectionId);
    Optional<ChatSession> findTopByConnectionIdOrderByUpdatedAtDesc(Long connectionId);
}
