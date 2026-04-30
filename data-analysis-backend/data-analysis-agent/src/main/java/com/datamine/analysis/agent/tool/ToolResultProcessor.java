package com.datamine.analysis.agent.tool;

import com.datamine.analysis.common.dto.knowledge.KnowledgeCitationDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolResultProcessor {

    private final ObjectMapper objectMapper;

    /**
     * 将工具原始结果压缩成适合前端步骤区域展示的一句话摘要。
     */
    public String summarize(String result) {
        if (!StringUtils.hasText(result)) {
            return "执行完成";
        }

        try {
            JsonNode root = objectMapper.readTree(result);
            if (root.has("message") && root.get("message").isTextual()) {
                return root.get("message").asText();
            }
            if (root.has("rowCount")) {
                return "查询完成，返回 " + root.get("rowCount").asInt() + " 行";
            }
            if (root.has("affectedRows")) {
                return "执行完成，影响 " + root.get("affectedRows").asInt() + " 行";
            }

            JsonNode dataNode = root.path("data");
            if (dataNode.has("reportName")) {
                return "已保存报表：" + dataNode.get("reportName").asText();
            }
        } catch (Exception ignored) {
            // Fall through to plain-text summary.
        }

        return result.length() > 240 ? result.substring(0, 240) + "..." : result;
    }

    /**
     * 从知识库工具结果里提取 citations，并与当前会话已收集的引用做去重合并。
     */
    public void mergeKnowledgeCitations(String toolName,
                                        String toolResult,
                                        List<KnowledgeCitationDTO> collectedCitations) {
        if (!"knowledge_search".equalsIgnoreCase(toolName) || !StringUtils.hasText(toolResult)) {
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(toolResult);
            JsonNode citationsNode = root.path("data").path("citations");
            if (!citationsNode.isArray() || citationsNode.isEmpty()) {
                return;
            }

            List<KnowledgeCitationDTO> citations = objectMapper.convertValue(
                    citationsNode,
                    new TypeReference<>() {
                    });
            synchronized (collectedCitations) {
                for (KnowledgeCitationDTO citation : citations) {
                    boolean exists = collectedCitations.stream().anyMatch(existing ->
                            Objects.equals(existing.documentId(), citation.documentId())
                                    && Objects.equals(existing.chunkIndex(), citation.chunkIndex()));
                    if (!exists) {
                        collectedCitations.add(citation);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to collect knowledge citations from tool result", e);
        }
    }
}
