package com.datamine.analysis.common.repository;

import com.datamine.analysis.common.entity.TableMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TableMetadataRepository extends JpaRepository<TableMetadata, Long> {
    List<TableMetadata> findByConnectionId(Long connectionId);
    Optional<TableMetadata> findByConnectionIdAndTableName(Long connectionId, String tableName);
}
