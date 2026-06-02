package com.datamine.analysis.core.service;

import com.datamine.analysis.common.entity.SqlHistory;
import com.datamine.analysis.common.repository.SqlHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlHistoryService {

    private final SqlHistoryRepository sqlHistoryRepository;

    public SqlHistory save(SqlHistory history) {
        return sqlHistoryRepository.save(history);
    }

    public SqlHistory recordGenerated(Long userId, Long connectionId, String sessionId, String sql, String naturalLanguage) {
        SqlHistory history = new SqlHistory();
        history.setUserId(userId);
        history.setConnectionId(connectionId);
        history.setSessionId(sessionId);
        history.setSql(sql);
        history.setNaturalLanguage(naturalLanguage);
        history.setStatus("generated");
        return sqlHistoryRepository.save(history);
    }

    public List<SqlHistory> getRecentByConnectionId(Long userId, Long connectionId) {
        return sqlHistoryRepository.findTop20ByUserIdAndConnectionIdOrderByCreatedAtDesc(userId, connectionId);
    }

    public List<SqlHistory> getBySessionId(Long userId, String sessionId) {
        return sqlHistoryRepository.findByUserIdAndSessionIdOrderByCreatedAtDesc(userId, sessionId);
    }

    @Transactional
    public void deleteByConnectionId(Long userId, Long connectionId, Long historyId) {
        if (connectionId == null || historyId == null) {
            throw new IllegalArgumentException("connectionId and historyId are required");
        }
        if (!sqlHistoryRepository.existsByIdAndConnectionIdAndUserId(historyId, connectionId, userId)) {
            throw new IllegalArgumentException("SQL 历史不存在: " + historyId);
        }
        sqlHistoryRepository.deleteByIdAndConnectionIdAndUserId(historyId, connectionId, userId);
    }
}
