package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {
    List<Report> findByConnectionId(Long connectionId);
}
