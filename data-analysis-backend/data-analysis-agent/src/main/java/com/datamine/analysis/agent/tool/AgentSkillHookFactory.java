package com.datamine.analysis.agent.tool;

import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 统一管理 Skills 目录与 SkillsAgentHook 的装配。
 * 这里按能力域分组 Tool，只有模型 read_skill 后才会暴露对应能力。
 */
@Component
public class AgentSkillHookFactory {

    private static final String KNOWLEDGE_GROUNDING = "knowledge-grounding";
    private static final String ARTIFACT_GENERATION = "artifact-generation";

    private static final Set<String> GROUPED_TOOL_NAMES = Set.of(
            "knowledge_search",
            "save_chart_report",
            "save_markdown_report"
    );

    private final SkillRegistry projectSkillRegistry = ClasspathSkillRegistry.builder()
            .classpathPath("skills")
            .autoLoad(true)
            .build();

    public SkillsAgentHook buildHook(List<ToolCallback> toolCallbacks) {
        return SkillsAgentHook.builder()
                .skillRegistry(projectSkillRegistry)
                .groupedTools(buildGroupedTools(toolCallbacks))
                .autoReload(false)
                .build();
    }

    /**
     * 由 skill 目录管理的 Tool 默认不直接暴露，
     * 需要模型先 read_skill，再由 SkillsAgentHook 动态注入。
     */
    public List<ToolCallback> resolveBaseTools(List<ToolCallback> toolCallbacks) {
        return toolCallbacks.stream()
                .filter(callback -> !isGroupedTool(callback.getToolDefinition().name()))
                .toList();
    }

    private Map<String, List<ToolCallback>> buildGroupedTools(List<ToolCallback> toolCallbacks) {
        Map<String, List<ToolCallback>> groupedTools = new LinkedHashMap<>();
        putIfPresent(groupedTools, KNOWLEDGE_GROUNDING, toolCallbacks, "knowledge_search");
        putIfPresent(groupedTools, ARTIFACT_GENERATION, toolCallbacks, "save_chart_report", "save_markdown_report");
        return groupedTools;
    }

    private void putIfPresent(Map<String, List<ToolCallback>> groupedTools,
                              String skillName,
                              List<ToolCallback> toolCallbacks,
                              String... toolNames) {
        List<ToolCallback> matched = toolCallbacks.stream()
                .filter(callback -> matchesAny(callback.getToolDefinition().name(), toolNames))
                .toList();
        if (!matched.isEmpty()) {
            groupedTools.put(skillName, matched);
        }
    }

    private boolean isGroupedTool(String toolName) {
        return GROUPED_TOOL_NAMES.contains(normalize(toolName));
    }

    private boolean matchesAny(String toolName, String... candidates) {
        String normalized = normalize(toolName);
        for (String candidate : candidates) {
            if (normalized.equals(normalize(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
