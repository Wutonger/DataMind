package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.Report;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long> {

    List<Report> findByUserIdAndConnectionIdOrderByUpdatedAtDesc(Long userId, Long connectionId);

    Optional<Report> findByIdAndUserId(Long id, Long userId);
}
