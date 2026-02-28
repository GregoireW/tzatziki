package com.decathlon.tzatziki.utils;

import java.util.List;
import java.util.Map;

/**
 * Represents a tool call made by an LLM agent during conversation processing.
 */
public record AgentToolCall(String name, Map<String, Object> arguments) {

    /**
     * Represents the full result of an agent conversation processing,
     * including any tool calls the agent decided to make.
     */
    public record AgentResult(List<AgentToolCall> toolCalls, String content) {
        public boolean hasToolCall(String toolName) {
            return toolCalls.stream().anyMatch(tc -> tc.name().equals(toolName));
        }
    }
}
