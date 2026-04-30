package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.WorkflowRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowRunRepository extends JpaRepository<WorkflowRunEntity, String> {

    List<WorkflowRunEntity> findTop120BySceneOrderByStartedAtDesc(String scene);

    List<WorkflowRunEntity> findTop120BySceneAndConnectionIdOrderByStartedAtDesc(String scene, Long connectionId);

    java.util.Optional<WorkflowRunEntity> findByIdAndConnectionId(String id, Long connectionId);
}
