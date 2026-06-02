package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.WorkflowRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, String> {

    List<WorkflowRunEntity> findTop120BySceneOrderByStartedAtDesc(String scene);

    List<WorkflowRunEntity> findTop120BySceneAndConnectionIdOrderByStartedAtDesc(String scene, Long connectionId);

    List<WorkflowRunEntity> findTop120BySceneAndUserIdOrderByStartedAtDesc(String scene, Long userId);

    List<WorkflowRunEntity> findTop120BySceneAndUserIdAndConnectionIdOrderByStartedAtDesc(String scene, Long userId, Long connectionId);

    Optional<WorkflowRunEntity> findByIdAndConnectionId(String id, Long connectionId);

    Optional<WorkflowRunEntity> findByIdAndUserId(String id, Long userId);

    Optional<WorkflowRunEntity> findByIdAndUserIdAndConnectionId(String id, Long userId, Long connectionId);
}
