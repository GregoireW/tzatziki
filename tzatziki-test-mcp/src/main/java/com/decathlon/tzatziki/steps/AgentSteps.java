package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Mapper.readAsAListOf;
import static com.decathlon.tzatziki.utils.Patterns.*;

/**
 * Cucumber step definitions for testing MCP tool selection by LLM agents.
 * <p>
 * These steps allow you to define a set of tools, run a conversation through an LLM,
 * and assert which tools the LLM decided to call.
 * <p>
 * Configuration:
 * <ul>
 *   <li>{@code LLM_BASE_URL} env var or {@code llm.base-url} system property</li>
 *   <li>{@code LLM_API_KEY} env var or {@code llm.api-key} system property</li>
 * </ul>
 */
@Slf4j
@SuppressWarnings("java:S100")
public class AgentSteps {

    private static final String AGENT_RESULT_KEY = "_agentResult";
    private static final String AGENT_TOOLS_KEY = "_agentTools";

    private final ObjectSteps objects;
    private final LlmClient llmClient;

    public AgentSteps(ObjectSteps objects) {
        this.objects = objects;
        this.llmClient = new LlmClient();
    }

    @Given(THAT + GUARD + "the following agent tools:$")
    public void the_following_agent_tools(Guard guard, String content) {
        guard.in(objects, () -> {
            List<Map> tools = readAsAListOf(objects.resolve(content), Map.class);
            objects.add(AGENT_TOOLS_KEY, tools);
        });
    }

    @When(THAT + GUARD + "the agent processes the conversation with " + QUOTED_CONTENT + ":$")
    public void the_agent_processes_the_conversation(Guard guard, String model, String content) {
        guard.in(objects, () -> {
            String resolvedContent = objects.resolve(content);
            List<Map> messages = readAsAListOf(resolvedContent, Map.class);

            @SuppressWarnings("unchecked")
            List<Map> tools = objects.get(AGENT_TOOLS_KEY);
            if (tools == null) {
                tools = List.of();
            }

            AgentToolCall.AgentResult result = llmClient.chatWithTools(model, messages, tools);
            objects.add(AGENT_RESULT_KEY, result);

            log.info("Agent processed conversation with model '{}': {} tool call(s)", model, result.toolCalls().size());
            result.toolCalls().forEach(tc -> log.info("  Tool called: {} with args: {}", tc.name(), tc.arguments()));
        });
    }

    @Then(THAT + GUARD + "the tool " + QUOTED_CONTENT + " should have been called$")
    public void the_tool_should_have_been_called(Guard guard, String toolName) {
        guard.in(objects, () -> {
            AgentToolCall.AgentResult result = objects.get(AGENT_RESULT_KEY);
            if (result == null) {
                throw new AssertionError("No agent result available. Did you run the agent first?");
            }
            if (!result.hasToolCall(toolName)) {
                throw new AssertionError("Expected tool '" + toolName + "' to have been called, but it was not. " +
                        "Tool calls made: " + result.toolCalls().stream().map(AgentToolCall::name).toList());
            }
        });
    }

    @Then(THAT + GUARD + "the tool " + QUOTED_CONTENT + " should not have been called$")
    public void the_tool_should_not_have_been_called(Guard guard, String toolName) {
        guard.in(objects, () -> {
            AgentToolCall.AgentResult result = objects.get(AGENT_RESULT_KEY);
            if (result == null) {
                throw new AssertionError("No agent result available. Did you run the agent first?");
            }
            if (result.hasToolCall(toolName)) {
                throw new AssertionError("Expected tool '" + toolName + "' to NOT have been called, but it was. " +
                        "Tool calls made: " + result.toolCalls().stream().map(AgentToolCall::name).toList());
            }
        });
    }

    @Then(THAT + GUARD + "the agent should have called (\\d+) tools?$")
    public void the_agent_should_have_called_n_tools(Guard guard, int expectedCount) {
        guard.in(objects, () -> {
            AgentToolCall.AgentResult result = objects.get(AGENT_RESULT_KEY);
            if (result == null) {
                throw new AssertionError("No agent result available. Did you run the agent first?");
            }
            int actualCount = result.toolCalls().size();
            if (actualCount != expectedCount) {
                throw new AssertionError("Expected " + expectedCount + " tool call(s) but got " + actualCount +
                        ". Tool calls made: " + result.toolCalls().stream().map(AgentToolCall::name).toList());
            }
        });
    }

    @Then(THAT + GUARD + "the agent should not have called any tool$")
    public void the_agent_should_not_have_called_any_tool(Guard guard) {
        guard.in(objects, () -> {
            AgentToolCall.AgentResult result = objects.get(AGENT_RESULT_KEY);
            if (result == null) {
                throw new AssertionError("No agent result available. Did you run the agent first?");
            }
            if (!result.toolCalls().isEmpty()) {
                throw new AssertionError("Expected no tool calls but got " + result.toolCalls().size() +
                        ". Tool calls made: " + result.toolCalls().stream().map(AgentToolCall::name).toList());
            }
        });
    }
}
