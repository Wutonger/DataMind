package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.WorkflowStepEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WorkflowStepRepository extends JpaRepository<WorkflowStepEntity, String> {

    List<WorkflowStepEntity> findByRunIdOrderByStepOrderAsc(String runId);
}
