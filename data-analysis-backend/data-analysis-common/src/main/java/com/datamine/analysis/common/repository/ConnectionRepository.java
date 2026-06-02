package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.Connection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ConnectionRepository extends JpaRepository<Connection, Long> {
    List<Connection> findByStatus(String status);

    List<Connection> findAllByOrderByIdAsc();

    List<Connection> findByIdInOrderByIdAsc(List<Long> ids);

    @Modifying
    @Query("UPDATE Connection c SET c.status = :status")
    int updateAllStatus(String status);
}
