package com.datamine.analysis.core.service;

import com.datamine.analysis.core.chat.ChatModelFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SqlFormatService {

    private final ChatModelFactory chatModelFactory;

    private static final String FORMAT_PROMPT = """
            请将以下 SQL 语句格式化为标准、易读的格式。规则：
            1. 关键字大写
            2. 适当缩进和换行
            3. 每个子句单独一行
            4. 只返回格式化后的 SQL，不要解释
            原始 SQL:
            %s
            """;

    public String format(String sql) {
        try {
            ChatModel chatModel = chatModelFactory.getChatModel();
            String formatted = chatModel.call(new Prompt(List.of(
                    new UserMessage(FORMAT_PROMPT.formatted(sql))
            ))).getResult().getOutput().getText();

            if (formatted != null) {
                formatted = formatted.trim();
                if (formatted.startsWith("```sql")) {
                    formatted = formatted.substring(6);
                } else if (formatted.startsWith("```")) {
                    formatted = formatted.substring(3);
                }
                if (formatted.endsWith("```")) {
                    formatted = formatted.substring(0, formatted.length() - 3);
                }
                return formatted.trim();
            }
            return sql;
        } catch (Exception e) {
            log.warn("SQL formatting failed, returning original", e);
            return sql;
        }
    }
}
