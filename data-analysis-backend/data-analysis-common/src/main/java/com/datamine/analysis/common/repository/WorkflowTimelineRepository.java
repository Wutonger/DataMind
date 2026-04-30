package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.WorkflowTimelineEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowTimelineRepository extends JpaRepository<WorkflowTimelineEntity, Long> {

    List<WorkflowTimelineEntity> findByRunIdOrderByEventOrderAsc(String runId);
}
