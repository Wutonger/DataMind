package com.datamine.analysis.core.chat;

import com.datamine.analysis.agent.prompt.PromptConstant;
import com.datamine.analysis.agent.tool.AgentToolsetFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatContextPromptResolver {

    private final AgentToolsetFactory agentToolsetFactory;

    public ChatPromptSnapshot resolve(Long userId, Long connectionId, String userInput) {
        AgentToolsetFactory.AgentToolset toolset = agentToolsetFactory.createChatToolset(userId, connectionId, userInput);
        String prompt = appendCapabilityHint(PromptConstant.CHAT_AGENT_PROMPT, connectionId, toolset.isEmpty());
        int visibleToolCount = toolset.baseCallbacks().size() + (toolset.skillHook() == null ? 0 : 1);
        return new ChatPromptSnapshot(prompt, visibleToolCount);
    }

    private String appendCapabilityHint(String prompt, Long connectionId, boolean noToolsAvailable) {
        if (connectionId == null) {
            return prompt + "\n" + PromptConstant.NO_CONNECTION_CAPABILITY_HINT;
        }
        if (noToolsAvailable) {
            return prompt + "\n" + PromptConstant.NO_TOOLS_CAPABILITY_HINT;
        }
        return prompt + "\n" + PromptConstant.TOOLS_READY_CAPABILITY_HINT;
    }

    public record ChatPromptSnapshot(String prompt, int toolCount) {
    }
}
