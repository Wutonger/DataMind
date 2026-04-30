package com.datamine.analysis.mcp.server.config;

import com.datamine.analysis.mcp.server.tools.DatabaseTools;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

    @Bean
    public MethodToolCallbackProvider databaseToolProvider(DatabaseTools databaseTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(databaseTools)
                .build();
    }
}
