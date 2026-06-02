package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.ConnectionUserAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConnectionUserAccessRepository extends JpaRepository<ConnectionUserAccess, Long> {

    List<ConnectionUserAccess> findByUserIdOrderByConnectionIdAsc(Long userId);

    List<ConnectionUserAccess> findByConnectionIdOrderByUserIdAsc(Long connectionId);

    boolean existsByConnectionIdAndUserId(Long connectionId, Long userId);

    void deleteByConnectionIdAndUserId(Long connectionId, Long userId);

    void deleteByConnectionId(Long connectionId);
}
