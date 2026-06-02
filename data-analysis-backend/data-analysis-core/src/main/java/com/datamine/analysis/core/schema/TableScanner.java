package com.datamine.analysis.core.schema;

import com.datamine.analysis.agent.orchestrator.AssistantAgentOrchestrator;
import com.datamine.analysis.common.entity.Connection;
import com.datamine.analysis.common.entity.TableMetadata;
import com.datamine.analysis.common.repository.TableMetadataRepository;
import com.datamine.analysis.agent.workflow.WorkflowRunTracker;
import com.datamine.analysis.core.chat.ChatModelFactory;
import com.datamine.analysis.core.service.ConnectionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TableScanner {

    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final int SCAN_PHASE_MAX_PERCENT = 90;

    private final SchemaReader schemaReader;
    private final TableMetadataRepository tableMetadataRepository;
    private final ConnectionService connectionService;
    private final ChatModelFactory chatModelFactory;
    private final ObjectMapper objectMapper;
    private final WorkflowRunTracker workflowRunTracker;
    private final AssistantAgentOrchestrator assistantAgentOrchestrator;

    @FunctionalInterface
    public interface TableScanProgressListener {
        void onProgress(TableScanProgressEvent event);
    }

    public record TableScanProgressEvent(String type, Map<String, Object> data) {
    }

    public List<TableMetadata> scanTables(Long connectionId) {
        return scanTables(null, connectionId, null);
    }

    public List<TableMetadata> scanTables(Long connectionId, TableScanProgressListener progressListener) {
        return scanTables(null, connectionId, progressListener);
    }

    public List<TableMetadata> scanTables(Long userId, Long connectionId, TableScanProgressListener progressListener) {
        String workflowRunId = workflowRunTracker.startRun("analysis", userId, connectionId, "全库表结构分析");
        String routeStepId = workflowRunId + "-route";
        String scanStepId = workflowRunId + "-scan";
        String relationStepId = workflowRunId + "-relation";
        String persistStepId = workflowRunId + "-persist";

        try {
            Connection connection = connectionService.getConnectionById(connectionId).orElseThrow();
            List<Map<String, Object>> tables = schemaReader.readTables(connection);
            List<Map<String, Object>> foreignKeys = schemaReader.readForeignKeys(connection);
            List<Map<String, Object>> allColumnRows = schemaReader.readAllColumns(connection);
            Map<String, List<Map<String, Object>>> groupedColumns = groupColumnsByTable(allColumnRows);
            Map<String, TableMetadata> existingMetadataMap = loadExistingMetadataMap(connectionId);

            int totalTables = tables.size();
            int totalBatches = totalTables == 0 ? 0 : (totalTables + DEFAULT_BATCH_SIZE - 1) / DEFAULT_BATCH_SIZE;

            Map<String, List<Map<String, Object>>> allColumns = new LinkedHashMap<>();
            Map<String, String> descriptions = new HashMap<>();

        workflowRunTracker.startStep(
                workflowRunId,
                routeStepId,
                AssistantAgentOrchestrator.ASSISTANT_AGENT,
                AssistantAgentOrchestrator.ASSISTANT_OWNER,
                "锁定表分析流程",
                "Workflow Start",
                "准备执行全库表结构分析",
                List.of()
        );
        workflowRunTracker.addTimeline(workflowRunId, routeStepId, AssistantAgentOrchestrator.ASSISTANT_OWNER, "初始化表结构分析工作流");
        workflowRunTracker.completeStep(
                workflowRunId,
                routeStepId,
                "已锁定当前分析任务",
                List.of("routeMode=" + AssistantAgentOrchestrator.ROUTE_MODE)
        );

        workflowRunTracker.startStep(
                workflowRunId,
                scanStepId,
                AssistantAgentOrchestrator.ASSISTANT_AGENT,
                AssistantAgentOrchestrator.ASSISTANT_OWNER,
                "采集表结构元数据",
                "Schema Collect",
                "开始读取数据表、字段和外键信息",
                List.of("schema_reader")
        );
        workflowRunTracker.addTimeline(workflowRunId, scanStepId, AssistantAgentOrchestrator.ASSISTANT_OWNER, "开始采集全库表结构元数据");

        Map<String, Object> scanStartedPayload = createProgressPayload(
                totalTables,
                0,
                DEFAULT_BATCH_SIZE,
                0,
                totalBatches,
                0,
                0,
                totalTables == 0 ? 100 : 0,
                "scan",
                totalTables == 0
                        ? "未发现可分析的数据表"
                        : "已发现 " + totalTables + " 张表，准备开始扫描"
        );
        scanStartedPayload.put("runId", workflowRunId);
        emitProgress(progressListener, "SCAN_STARTED", scanStartedPayload);

        for (int i = 0; i < totalTables; i += DEFAULT_BATCH_SIZE) {
            int end = Math.min(i + DEFAULT_BATCH_SIZE, totalTables);
            int batchIndex = (i / DEFAULT_BATCH_SIZE) + 1;
            List<Map<String, Object>> batchTables = tables.subList(i, end);

            Map<String, Object> batchStartedPayload = createProgressPayload(
                    totalTables,
                    i,
                    DEFAULT_BATCH_SIZE,
                    batchIndex,
                    totalBatches,
                    i + 1,
                    end,
                    calculateScanPercent(i, totalTables),
                    "scan",
                    "正在扫描第 " + batchIndex + "/" + totalBatches + " 批表结构"
            );
            batchStartedPayload.put("runId", workflowRunId);
            emitProgress(progressListener, "BATCH_STARTED", batchStartedPayload);
            workflowRunTracker.addTimeline(
                    workflowRunId,
                    scanStepId,
                    AssistantAgentOrchestrator.ASSISTANT_OWNER,
                    "开始扫描第 " + batchIndex + "/" + totalBatches + " 批表结构"
            );

            Map<String, List<Map<String, Object>>> batchColumns = new LinkedHashMap<>();
            for (Map<String, Object> table : batchTables) {
                String tableName = stringValue(table.get("TABLE_NAME"));
                List<Map<String, Object>> columns = groupedColumns.getOrDefault(tableName, List.of());
                batchColumns.put(tableName, columns);
                allColumns.put(tableName, columns);
            }

            descriptions.putAll(resolveTableDescriptions(chatModelFactory.getChatModel(), batchTables, batchColumns));

            Map<String, Object> batchCompletedPayload = createProgressPayload(
                    totalTables,
                    end,
                    DEFAULT_BATCH_SIZE,
                    batchIndex,
                    totalBatches,
                    i + 1,
                    end,
                    calculateScanPercent(end, totalTables),
                    "scan",
                    "已完成第 " + batchIndex + "/" + totalBatches + " 批表结构扫描"
            );
            batchCompletedPayload.put("runId", workflowRunId);
            emitProgress(progressListener, "BATCH_COMPLETED", batchCompletedPayload);
            workflowRunTracker.addTimeline(
                    workflowRunId,
                    scanStepId,
                    AssistantAgentOrchestrator.ASSISTANT_OWNER,
                    "已完成第 " + batchIndex + "/" + totalBatches + " 批表结构扫描"
            );

            log.info("Processed batch: {}-{}/{} tables", i + 1, end, totalTables);
        }

        workflowRunTracker.completeStep(
                workflowRunId,
                scanStepId,
                "已完成 " + totalTables + " 张表的结构采集与描述生成",
                List.of("schema_reader", "table_description")
        );
        workflowRunTracker.addTimeline(workflowRunId, scanStepId, AssistantAgentOrchestrator.ASSISTANT_OWNER, "表结构元数据采集完成");

        workflowRunTracker.startStep(
                workflowRunId,
                relationStepId,
                AssistantAgentOrchestrator.ASSISTANT_AGENT,
                AssistantAgentOrchestrator.ASSISTANT_OWNER,
                "推断全库表关系",
                "Relation Inference",
                "开始综合主键、外键和候选字段分析关系",
                List.of("relation_inference")
        );
        workflowRunTracker.addTimeline(workflowRunId, relationStepId, AssistantAgentOrchestrator.ASSISTANT_OWNER, "开始分析全库表关系");

        Map<String, Object> relationStartedPayload = createProgressPayload(
                totalTables,
                totalTables,
                DEFAULT_BATCH_SIZE,
                totalBatches,
                totalBatches,
                totalTables == 0 ? 0 : 1,
                totalTables,
                totalTables == 0 ? 100 : 95,
                "relation",
                "正在分析全库表关系"
        );
        relationStartedPayload.put("runId", workflowRunId);
        emitProgress(progressListener, "RELATION_ANALYSIS_STARTED", relationStartedPayload);

        Map<String, List<Map<String, Object>>> relations = analyzeGlobalRelationsByLlm(
                chatModelFactory.getChatModel(),
                tables,
                allColumns,
                foreignKeys
        );

        workflowRunTracker.completeStep(
                workflowRunId,
                relationStepId,
                "已完成全库表关系推断",
                List.of("relation_inference")
        );
        workflowRunTracker.addTimeline(workflowRunId, relationStepId, AssistantAgentOrchestrator.ASSISTANT_OWNER, "全库表关系分析完成");

        Map<String, Object> relationCompletedPayload = createProgressPayload(
                totalTables,
                totalTables,
                DEFAULT_BATCH_SIZE,
                totalBatches,
                totalBatches,
                totalTables == 0 ? 0 : 1,
                totalTables,
                totalTables == 0 ? 100 : 99,
                "relation",
                "已完成表关系分析"
        );
        relationCompletedPayload.put("runId", workflowRunId);
        emitProgress(progressListener, "RELATION_ANALYSIS_COMPLETED", relationCompletedPayload);

        workflowRunTracker.startStep(
                workflowRunId,
                persistStepId,
                AssistantAgentOrchestrator.ASSISTANT_AGENT,
                AssistantAgentOrchestrator.ASSISTANT_OWNER,
                "落库并同步前端结果",
                "Persist Result",
                "开始回写表结构元数据",
                List.of("metadata_persist")
        );
        workflowRunTracker.addTimeline(workflowRunId, persistStepId, AssistantAgentOrchestrator.ASSISTANT_OWNER, "开始回写分析结果");

        for (Map<String, Object> table : tables) {
            String tableName = stringValue(table.get("TABLE_NAME"));
            String tableComment = stringValue(table.get("TABLE_COMMENT"));
            Long tableRows = resolveTableRowCount(table.get("TABLE_ROWS"));

            TableMetadata metadata = existingMetadataMap.getOrDefault(tableName, new TableMetadata());

            metadata.setConnectionId(connectionId);
            metadata.setTableName(tableName);
            metadata.setSchema(connection.getDatabase());
            metadata.setRowCount(tableRows);
            metadata.setFields(serializeFields(allColumns.get(tableName)));
            metadata.setAiDescription(descriptions.getOrDefault(tableName, tableComment));
            metadata.setRelations(serializeRelations(relations.getOrDefault(tableName, List.of())));
            metadata.setAnalyzedAt(LocalDateTime.now());

            tableMetadataRepository.save(metadata);
            log.info("Scanned table: {} ({} rows)", tableName, tableRows);
        }

        workflowRunTracker.completeStep(
                workflowRunId,
                persistStepId,
                "表结构分析结果已完成落库",
                List.of("metadata_persist")
        );
        workflowRunTracker.addTimeline(workflowRunId, persistStepId, AssistantAgentOrchestrator.ASSISTANT_OWNER, "分析结果已同步完成");
            workflowRunTracker.completeRun(workflowRunId, AssistantAgentOrchestrator.ROUTE_MODE);

            return tableMetadataRepository.findByConnectionId(connectionId);
        } catch (RuntimeException error) {
            workflowRunTracker.failRun(workflowRunId, AssistantAgentOrchestrator.ROUTE_MODE, error.getMessage());
            throw error;
        }
    }

    public List<TableMetadata> getCachedMetadata(Long connectionId) {
        return tableMetadataRepository.findByConnectionId(connectionId);
    }

    /**
     * 优先复用表注释，只有在表注释缺失时才调用 LLM 生成简短描述，
     * 并且只提供主键、关联字段和少量业务提示字段，避免把整表字段全部发送给模型。
     */
    private Map<String, String> resolveTableDescriptions(ChatModel chatModel,
                                                         List<Map<String, Object>> tables,
                                                         Map<String, List<Map<String, Object>>> allColumns) {
        Map<String, String> resolvedDescriptions = new HashMap<>();
        List<Map<String, Object>> tablesNeedingDescription = new ArrayList<>();

        for (Map<String, Object> table : tables) {
            String tableName = stringValue(table.get("TABLE_NAME"));
            String tableComment = stringValue(table.get("TABLE_COMMENT"));
            List<Map<String, Object>> columns = allColumns.getOrDefault(tableName, List.of());

            if (StringUtils.hasText(tableComment)) {
                resolvedDescriptions.put(tableName, tableComment);
                continue;
            }

            tablesNeedingDescription.add(buildDescriptionPayload(tableName, columns));
        }

        if (tablesNeedingDescription.isEmpty()) {
            return resolvedDescriptions;
        }

        try {
            Map<String, String> generatedDescriptions =
                    assistantAgentOrchestrator.generateTableDescriptions(chatModel, tablesNeedingDescription);
            for (Map<String, Object> tableInfo : tablesNeedingDescription) {
                String tableName = stringValue(tableInfo.get("tableName"));
                String generated = generatedDescriptions.get(tableName);
                resolvedDescriptions.put(
                        tableName,
                        StringUtils.hasText(generated) ? generated.trim() : buildDescriptionFallback(tableName)
                );
            }
            return resolvedDescriptions;
        } catch (Exception e) {
            log.warn("Table description generation failed, falling back to table names", e);
            for (Map<String, Object> tableInfo : tablesNeedingDescription) {
                String tableName = stringValue(tableInfo.get("tableName"));
                resolvedDescriptions.put(tableName, buildDescriptionFallback(tableName));
            }
            return resolvedDescriptions;
        }
    }

    private Map<String, Object> buildDescriptionPayload(String tableName, List<Map<String, Object>> columns) {
        Map<String, Object> tableInfo = new LinkedHashMap<>();
        tableInfo.put("tableName", tableName);

        List<Map<String, Object>> keyColumns = buildKeyColumns(columns);
        if (!keyColumns.isEmpty()) {
            tableInfo.put("keyColumns", keyColumns);
        }

        List<Map<String, Object>> relationCandidates = buildRelationCandidates(columns);
        if (!relationCandidates.isEmpty()) {
            tableInfo.put("relationCandidates", relationCandidates);
        }

        List<Map<String, Object>> hintColumns = buildDescriptionHintColumns(columns);
        if (!hintColumns.isEmpty()) {
            tableInfo.put("hintColumns", hintColumns);
        }

        return tableInfo;
    }

    private Map<String, List<Map<String, Object>>> analyzeGlobalRelationsByLlm(
            ChatModel chatModel,
            List<Map<String, Object>> tables,
            Map<String, List<Map<String, Object>>> allColumns,
            List<Map<String, Object>> foreignKeys) {
        if (tables.isEmpty()) {
            return Map.of();
        }

        Map<String, List<Map<String, Object>>> foreignKeyMap = buildForeignKeyMap(foreignKeys);
        List<Map<String, Object>> tablePayload = new ArrayList<>();

        for (Map<String, Object> table : tables) {
            String tableName = stringValue(table.get("TABLE_NAME"));
            List<Map<String, Object>> columns = allColumns.getOrDefault(tableName, List.of());

            Map<String, Object> tableInfo = new LinkedHashMap<>();
            tableInfo.put("tableName", tableName);

            String tableComment = stringValue(table.get("TABLE_COMMENT"));
            if (!tableComment.isEmpty()) {
                tableInfo.put("comment", tableComment);
            }

            List<Map<String, Object>> keyColumns = buildKeyColumns(columns);
            if (!keyColumns.isEmpty()) {
                tableInfo.put("keyColumns", keyColumns);
            }

            List<Map<String, Object>> relationCandidates = buildRelationCandidates(columns);
            if (!relationCandidates.isEmpty()) {
                tableInfo.put("relationCandidates", relationCandidates);
            }

            List<Map<String, Object>> physicalForeignKeys = foreignKeyMap.getOrDefault(tableName, List.of());
            if (!physicalForeignKeys.isEmpty()) {
                tableInfo.put("physicalForeignKeys", physicalForeignKeys);
            }

            tablePayload.add(tableInfo);
        }

        try {
            Map<String, List<Map<String, Object>>> parsed = assistantAgentOrchestrator.analyzeGlobalRelations(
                    chatModel,
                    Map.of("tables", tablePayload)
            );
            return normalizeRelationResult(parsed, tables, allColumns, foreignKeys);
        } catch (Exception e) {
            log.warn("Global relation analysis failed, falling back to physical foreign keys only", e);
            return normalizeRelationResult(Map.of(), tables, allColumns, foreignKeys);
        }
    }

    private List<Map<String, Object>> buildKeyColumns(List<Map<String, Object>> columns) {
        List<Map<String, Object>> keyColumns = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String key = stringValue(column.get("COLUMN_KEY"));
            if (!"PRI".equalsIgnoreCase(key) && !"UNI".equalsIgnoreCase(key)) {
                continue;
            }

            Map<String, Object> keyInfo = new LinkedHashMap<>();
            keyInfo.put("name", stringValue(column.get("COLUMN_NAME")));
            keyInfo.put("type", stringValue(column.get("COLUMN_TYPE")));
            keyInfo.put("role", "PRI".equalsIgnoreCase(key) ? "pk" : "unique");
            keyColumns.add(keyInfo);
        }
        return keyColumns;
    }

    private List<Map<String, Object>> buildRelationCandidates(List<Map<String, Object>> columns) {
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String columnName = stringValue(column.get("COLUMN_NAME"));
            if (!isRelationCandidate(columnName)) {
                continue;
            }

            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("name", columnName);
            candidate.put("type", stringValue(column.get("COLUMN_TYPE")));

            String columnComment = stringValue(column.get("COLUMN_COMMENT"));
            if (!columnComment.isEmpty()) {
                candidate.put("comment", columnComment);
            }
            candidates.add(candidate);
        }
        return candidates;
    }

    /**
     * 为无注释表挑选少量业务提示字段，尽量减少 token，同时保留能帮助判断业务语义的信息。
     */
    private List<Map<String, Object>> buildDescriptionHintColumns(List<Map<String, Object>> columns) {
        List<Map<String, Object>> hintColumns = new ArrayList<>();
        Set<String> addedColumns = new LinkedHashSet<>();

        for (Map<String, Object> column : columns) {
            if (hintColumns.size() >= 6) {
                break;
            }

            String columnName = stringValue(column.get("COLUMN_NAME"));
            String normalizedColumnName = columnName.toLowerCase();
            String columnComment = stringValue(column.get("COLUMN_COMMENT"));

            if (!StringUtils.hasText(columnName)
                    || isAuditLikeColumn(normalizedColumnName)
                    || !StringUtils.hasText(columnComment)) {
                continue;
            }

            if (addedColumns.add(normalizedColumnName)) {
                hintColumns.add(buildHintColumn(columnName, stringValue(column.get("COLUMN_TYPE")), columnComment));
            }
        }

        for (Map<String, Object> column : columns) {
            if (hintColumns.size() >= 6) {
                break;
            }

            String columnName = stringValue(column.get("COLUMN_NAME"));
            String normalizedColumnName = columnName.toLowerCase();
            if (!StringUtils.hasText(columnName)
                    || isAuditLikeColumn(normalizedColumnName)
                    || !looksLikeBusinessHintColumn(normalizedColumnName)
                    || !addedColumns.add(normalizedColumnName)) {
                continue;
            }

            hintColumns.add(buildHintColumn(
                    columnName,
                    stringValue(column.get("COLUMN_TYPE")),
                    stringValue(column.get("COLUMN_COMMENT"))
            ));
        }

        for (Map<String, Object> column : columns) {
            if (hintColumns.size() >= 6) {
                break;
            }

            String columnName = stringValue(column.get("COLUMN_NAME"));
            String normalizedColumnName = columnName.toLowerCase();
            if (!StringUtils.hasText(columnName)
                    || isAuditLikeColumn(normalizedColumnName)
                    || isRelationCandidate(columnName)
                    || !addedColumns.add(normalizedColumnName)) {
                continue;
            }

            hintColumns.add(buildHintColumn(
                    columnName,
                    stringValue(column.get("COLUMN_TYPE")),
                    stringValue(column.get("COLUMN_COMMENT"))
            ));
        }

        return hintColumns;
    }

    private Map<String, Object> buildHintColumn(String name, String type, String comment) {
        Map<String, Object> hintColumn = new LinkedHashMap<>();
        hintColumn.put("name", name);
        hintColumn.put("type", type);
        if (StringUtils.hasText(comment)) {
            hintColumn.put("comment", comment);
        }
        return hintColumn;
    }

    private boolean isRelationCandidate(String columnName) {
        if (columnName == null || columnName.isBlank()) {
            return false;
        }

        String normalized = columnName.trim().toLowerCase();
        return normalized.endsWith("_id")
                || "parent_id".equals(normalized)
                || "created_by".equals(normalized)
                || "updated_by".equals(normalized)
                || "owner_id".equals(normalized)
                || "tenant_id".equals(normalized);
    }

    private boolean isAuditLikeColumn(String columnName) {
        return Set.of(
                "id", "deleted", "is_deleted", "version",
                "create_time", "created_at", "created_by",
                "update_time", "updated_at", "updated_by",
                "gmt_create", "gmt_modified"
        ).contains(columnName);
    }

    private boolean looksLikeBusinessHintColumn(String columnName) {
        return columnName.contains("name")
                || columnName.contains("title")
                || columnName.contains("code")
                || columnName.contains("type")
                || columnName.contains("status")
                || columnName.contains("category")
                || columnName.contains("amount")
                || columnName.contains("price")
                || columnName.contains("content")
                || columnName.contains("desc")
                || columnName.contains("remark");
    }

    private Map<String, List<Map<String, Object>>> buildForeignKeyMap(List<Map<String, Object>> foreignKeys) {
        Map<String, List<Map<String, Object>>> foreignKeyMap = new LinkedHashMap<>();
        for (Map<String, Object> foreignKey : foreignKeys) {
            String sourceTable = stringValue(foreignKey.get("TABLE_NAME"));

            Map<String, Object> relation = new LinkedHashMap<>();
            relation.put("column", stringValue(foreignKey.get("COLUMN_NAME")));
            relation.put("targetTable", stringValue(foreignKey.get("REFERENCED_TABLE_NAME")));
            relation.put("targetColumn", stringValue(foreignKey.get("REFERENCED_COLUMN_NAME")));
            relation.put("type", "fk");

            foreignKeyMap.computeIfAbsent(sourceTable, key -> new ArrayList<>()).add(relation);
        }
        return foreignKeyMap;
    }

    private Map<String, List<Map<String, Object>>> normalizeRelationResult(
            Map<String, List<Map<String, Object>>> parsed,
            List<Map<String, Object>> tables,
            Map<String, List<Map<String, Object>>> allColumns,
            List<Map<String, Object>> foreignKeys) {
        Map<String, List<Map<String, Object>>> normalized = new LinkedHashMap<>();
        Map<String, Set<String>> relationKeys = new HashMap<>();
        Set<String> tableNames = new LinkedHashSet<>();
        Map<String, Set<String>> columnNames = new HashMap<>();

        for (Map<String, Object> table : tables) {
            String tableName = stringValue(table.get("TABLE_NAME"));
            tableNames.add(tableName);
            normalized.put(tableName, new ArrayList<>());
            relationKeys.put(tableName, new LinkedHashSet<>());

            Set<String> tableColumnNames = new LinkedHashSet<>();
            for (Map<String, Object> column : allColumns.getOrDefault(tableName, List.of())) {
                tableColumnNames.add(stringValue(column.get("COLUMN_NAME")).toLowerCase());
            }
            columnNames.put(tableName, tableColumnNames);
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : parsed.entrySet()) {
            String sourceTable = entry.getKey();
            if (!tableNames.contains(sourceTable)) {
                continue;
            }

            for (Map<String, Object> relation : entry.getValue()) {
                String sourceColumn = stringValue(relation.get("column"));
                String targetTable = stringValue(relation.get("targetTable"));
                String targetColumn = stringValue(relation.get("targetColumn"));
                String type = stringValue(relation.get("type"));

                if (sourceColumn.isBlank()
                        || targetTable.isBlank()
                        || targetColumn.isBlank()
                        || !tableNames.contains(targetTable)
                        || !columnNames.getOrDefault(sourceTable, Set.of()).contains(sourceColumn.toLowerCase())
                        || !columnNames.getOrDefault(targetTable, Set.of()).contains(targetColumn.toLowerCase())) {
                    continue;
                }

                addRelation(normalized, relationKeys, sourceTable, sourceColumn, targetTable, targetColumn, normalizeRelationType(type));
            }
        }

        for (Map<String, Object> foreignKey : foreignKeys) {
            addRelation(
                    normalized,
                    relationKeys,
                    stringValue(foreignKey.get("TABLE_NAME")),
                    stringValue(foreignKey.get("COLUMN_NAME")),
                    stringValue(foreignKey.get("REFERENCED_TABLE_NAME")),
                    stringValue(foreignKey.get("REFERENCED_COLUMN_NAME")),
                    "fk"
            );
        }

        return normalized;
    }

    private void addRelation(Map<String, List<Map<String, Object>>> relations,
                             Map<String, Set<String>> relationKeys,
                             String sourceTable,
                             String sourceColumn,
                             String targetTable,
                             String targetColumn,
                             String type) {
        if (!relations.containsKey(sourceTable)) {
            return;
        }

        String relationKey = (sourceColumn + "|" + targetTable + "|" + targetColumn).toLowerCase();
        if (!relationKeys.computeIfAbsent(sourceTable, key -> new LinkedHashSet<>()).add(relationKey)) {
            return;
        }

        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("column", sourceColumn);
        relation.put("targetTable", targetTable);
        relation.put("targetColumn", targetColumn);
        relation.put("type", type);
        relations.get(sourceTable).add(relation);
    }

    private String normalizeRelationType(String type) {
        return "fk".equalsIgnoreCase(type) ? "fk" : "logical";
    }

    private int calculateScanPercent(int processedTables, int totalTables) {
        if (totalTables <= 0) {
            return 100;
        }
        return Math.min(SCAN_PHASE_MAX_PERCENT, (int) Math.round((double) processedTables * SCAN_PHASE_MAX_PERCENT / totalTables));
    }

    /**
     * 表分析阶段优先采用库侧提供的近似行数，避免逐表 COUNT(*) 导致大库扫描明显变慢。
     */
    private Long resolveTableRowCount(Object metadataRowCount) {
        Long estimatedRowCount = toLong(metadataRowCount);
        return estimatedRowCount != null ? Math.max(estimatedRowCount, 0L) : 0L;
    }

    private String buildDescriptionFallback(String tableName) {
        return tableName + "相关数据";
    }

    private String serializeFields(List<Map<String, Object>> columns) {
        try {
            List<Map<String, Object>> fields = columns.stream().map(col -> {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("name", stringValue(col.get("COLUMN_NAME")));
                field.put("type", stringValue(col.get("COLUMN_TYPE")));
                field.put("nullable", "YES".equals(stringValue(col.get("IS_NULLABLE"))));
                field.put("key", stringValue(col.get("COLUMN_KEY")));
                field.put("default", col.get("COLUMN_DEFAULT"));
                field.put("extra", stringValue(col.get("EXTRA")));
                field.put("comment", stringValue(col.get("COLUMN_COMMENT")));
                return field;
            }).toList();
            return objectMapper.writeValueAsString(fields);
        } catch (Exception e) {
            log.error("Failed to serialize fields", e);
            return "[]";
        }
    }

    private String serializeRelations(List<Map<String, Object>> relations) {
        try {
            return objectMapper.writeValueAsString(relations);
        } catch (Exception e) {
            log.error("Failed to serialize relations", e);
            return "[]";
        }
    }

    private void emitProgress(TableScanProgressListener progressListener,
                              String type,
                              Map<String, Object> data) {
        if (progressListener != null) {
            progressListener.onProgress(new TableScanProgressEvent(type, data));
        }
    }

    private Map<String, Object> createProgressPayload(int totalTables,
                                                      int processedTables,
                                                      int batchSize,
                                                      int batchIndex,
                                                      int totalBatches,
                                                      int batchStart,
                                                      int batchEnd,
                                                      int percent,
                                                      String stage,
                                                      String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("totalTables", totalTables);
        payload.put("processedTables", processedTables);
        payload.put("batchSize", batchSize);
        payload.put("batchIndex", batchIndex);
        payload.put("totalBatches", totalBatches);
        payload.put("batchStart", batchStart);
        payload.put("batchEnd", batchEnd);
        payload.put("percent", percent);
        payload.put("stage", stage);
        payload.put("message", message);
        return payload;
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Map<String, List<Map<String, Object>>> groupColumnsByTable(List<Map<String, Object>> columnRows) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> columnRow : columnRows) {
            String tableName = stringValue(columnRow.get("TABLE_NAME"));
            if (!StringUtils.hasText(tableName)) {
                continue;
            }
            grouped.computeIfAbsent(tableName, key -> new ArrayList<>()).add(columnRow);
        }
        return grouped;
    }

    private Map<String, TableMetadata> loadExistingMetadataMap(Long connectionId) {
        Map<String, TableMetadata> metadataMap = new HashMap<>();
        for (TableMetadata metadata : tableMetadataRepository.findByConnectionId(connectionId)) {
            metadataMap.put(metadata.getTableName(), metadata);
        }
        return metadataMap;
    }
}
