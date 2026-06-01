package com.datamine.analysis.agent.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ToolProgressResolver {

    private static final String KIND_TOOL = "tool";
    private static final String KIND_SKILL = "skill";

    /**
     * 为当前可见工具构建统一的展示文案与分类。
     */
    public Map<String, ToolProgressDescriptor> buildDescriptors(List<ToolCallback> toolCallbacks) {
        Map<String, ToolProgressDescriptor> descriptors = new LinkedHashMap<>();
        for (ToolCallback callback : toolCallbacks) {
            ToolProgressDescriptor descriptor = resolve(callback.getToolDefinition());
            descriptors.put(callback.getToolDefinition().name(), descriptor);
        }
        return descriptors;
    }

    /**
     * 当只有工具名时，提供兜底展示信息。
     */
    public ToolProgressDescriptor resolveByName(String toolName) {
        return new ToolProgressDescriptor(resolveDisplayName(toolName, ""), "", resolveKind(toolName));
    }

    private ToolProgressDescriptor resolve(ToolDefinition toolDefinition) {
        String toolName = toolDefinition.name();
        String description = normalizeDescription(toolDefinition.description());
        String displayName = resolveDisplayName(toolName, description);
        return new ToolProgressDescriptor(displayName, description, resolveKind(toolName));
    }

    private String normalizeDescription(String description) {
        if (!StringUtils.hasText(description)) {
            return "";
        }

        String normalized = description.trim();
        while (normalized.endsWith("。") || normalized.endsWith("；") || normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1).trim();
        }
        return normalized;
    }

    private String resolveDisplayName(String toolName, String description) {
        String normalizedName = ToolNameNormalizer.canonicalize(toolName);
        return switch (normalizedName) {
            case "db_list_tables" -> "读取数据表列表";
            case "db_get_columns" -> "读取字段信息";
            case "db_get_schema" -> "读取表结构";
            case "db_execute" -> "执行数据查询";
            case "read_skill" -> "调用技能";
            case "save_chart_report" -> "保存图表报表";
            case "knowledge_search" -> "检索知识库";
            case "save_markdown_report" -> "保存文档报告";
            default -> {
                if (StringUtils.hasText(description)) {
                    yield description;
                }
                if (normalizedName.startsWith("db_") || normalizedName.startsWith("db")) {
                    yield "执行数据库操作";
                }
                yield StringUtils.hasText(toolName) ? toolName.replace('_', ' ') : "处理步骤";
            }
        };
    }

    private String resolveKind(String toolName) {
        return "read_skill".equals(ToolNameNormalizer.canonicalize(toolName)) ? KIND_SKILL : KIND_TOOL;
    }
}
