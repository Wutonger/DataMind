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

    public SqlHistory recordSuccess(Long connectionId, String sessionId, String sql,
                                    String naturalLanguage, String resultPreview,
                                    int rowCount, long executionTimeMs) {
        SqlHistory history = new SqlHistory();
        history.setConnectionId(connectionId);
        history.setSessionId(sessionId);
        history.setSql(sql);
        history.setNaturalLanguage(naturalLanguage);
        history.setResultPreview(resultPreview);
        history.setRowCount(rowCount);
        history.setExecutionTimeMs(executionTimeMs);
        history.setStatus("success");
        return sqlHistoryRepository.save(history);
    }

    public SqlHistory recordGenerated(Long connectionId, String sessionId, String sql, String naturalLanguage) {
        SqlHistory history = new SqlHistory();
        history.setConnectionId(connectionId);
        history.setSessionId(sessionId);
        history.setSql(sql);
        history.setNaturalLanguage(naturalLanguage);
        history.setStatus("generated");
        return sqlHistoryRepository.save(history);
    }

    public SqlHistory recordFailure(Long connectionId, String sessionId, String sql,
                                    String naturalLanguage, long executionTimeMs,
                                    String errorMessage) {
        SqlHistory history = new SqlHistory();
        history.setConnectionId(connectionId);
        history.setSessionId(sessionId);
        history.setSql(sql);
        history.setNaturalLanguage(naturalLanguage);
        history.setExecutionTimeMs(executionTimeMs);
        history.setStatus("error");
        history.setErrorMessage(errorMessage);
        return sqlHistoryRepository.save(history);
    }

    public List<SqlHistory> getByConnectionId(Long connectionId) {
        return sqlHistoryRepository.findByConnectionIdOrderByCreatedAtDesc(connectionId);
    }

    public List<SqlHistory> getRecentByConnectionId(Long connectionId) {
        return sqlHistoryRepository.findTop20ByConnectionIdOrderByCreatedAtDesc(connectionId);
    }

    public List<SqlHistory> getBySessionId(String sessionId) {
        return sqlHistoryRepository.findBySessionIdOrderByCreatedAtDesc(sessionId);
    }

    @Transactional
    public void deleteByConnectionId(Long connectionId, Long historyId) {
        if (connectionId == null || historyId == null) {
            throw new IllegalArgumentException("connectionId and historyId are required");
        }
        if (!sqlHistoryRepository.existsByIdAndConnectionId(historyId, connectionId)) {
            throw new IllegalArgumentException("SQL history not found: " + historyId);
        }
        sqlHistoryRepository.deleteByIdAndConnectionId(historyId, connectionId);
    }
}
