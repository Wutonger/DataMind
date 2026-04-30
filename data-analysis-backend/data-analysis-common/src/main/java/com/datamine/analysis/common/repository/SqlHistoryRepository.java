package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.SqlHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SqlHistoryRepository extends JpaRepository<SqlHistory, Long> {
    List<SqlHistory> findByConnectionIdOrderByCreatedAtDesc(Long connectionId);
    List<SqlHistory> findBySessionIdOrderByCreatedAtDesc(String sessionId);
    List<SqlHistory> findTop20ByConnectionIdOrderByCreatedAtDesc(Long connectionId);
}
