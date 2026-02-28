package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Lightweight client for OpenAI-compatible chat completions API with tool calling support.
 * <p>
 * Configurable via:
 * <ul>
 *   <li>Environment variable or system property {@code LLM_BASE_URL} (default: {@code https://api.openai.com/v1})</li>
 *   <li>Environment variable or system property {@code LLM_API_KEY}</li>
 * </ul>
 */
@Slf4j
public class LlmClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

    @Setter
    private static String baseUrl;
    @Setter
    private static String apiKey;

    private final HttpClient httpClient;

    public LlmClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    /**
     * Sends a chat completions request with tool definitions and returns the agent result.
     *
     * @param model        the LLM model name (e.g., "gpt-4o-mini")
     * @param messages     the conversation messages (role + content)
     * @param tools        the tool definitions (name + description + optional parameters)
     * @return the agent result containing any tool calls
     */
    @SuppressWarnings("unchecked")
    public AgentToolCall.AgentResult chatWithTools(String model, List<Map> messages,
                                                   List<Map> tools) {
        try {
            Map<String, Object> requestBody = buildRequestBody(model, messages, tools);
            String json = OBJECT_MAPPER.writeValueAsString(requestBody);

            log.debug("LLM request: {}", json);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resolveBaseUrl() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + resolveApiKey())
                    .timeout(DEFAULT_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            log.debug("LLM response ({}): {}", response.statusCode(), response.body());

            if (response.statusCode() != 200) {
                throw new RuntimeException("LLM API returned status " + response.statusCode() + ": " + response.body());
            }

            return parseResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM API call interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call LLM API", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(String model, List<Map> messages,
                                                  List<Map> tools) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);

        List<Map<String, String>> formattedMessages = messages.stream()
                .map(m -> Map.of("role", String.valueOf(m.get("role")), "content", String.valueOf(m.get("content"))))
                .toList();
        body.put("messages", formattedMessages);

        if (tools != null && !tools.isEmpty()) {
            List<Map<String, Object>> formattedTools = tools.stream()
                    .map(this::formatTool)
                    .toList();
            body.put("tools", formattedTools);
        }

        return body;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> formatTool(Map tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.get("name"));
        function.put("description", tool.get("description"));

        Object parameters = tool.get("parameters");
        if (parameters instanceof Map) {
            function.put("parameters", parameters);
        } else {
            function.put("parameters", Map.of("type", "object", "properties", Map.of()));
        }

        return Map.of("type", "function", "function", function);
    }

    @SuppressWarnings("unchecked")
    private AgentToolCall.AgentResult parseResponse(String responseBody) throws Exception {
        Map<String, Object> response = OBJECT_MAPPER.readValue(responseBody, new TypeReference<>() {});
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

        if (choices == null || choices.isEmpty()) {
            return new AgentToolCall.AgentResult(List.of(), null);
        }

        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        String content = (String) message.get("content");
        List<AgentToolCall> toolCalls = new ArrayList<>();

        List<Map<String, Object>> rawToolCalls = (List<Map<String, Object>>) message.get("tool_calls");
        if (rawToolCalls != null) {
            for (Map<String, Object> tc : rawToolCalls) {
                Map<String, Object> function = (Map<String, Object>) tc.get("function");
                String name = (String) function.get("name");
                String argsJson = function.get("arguments") instanceof String s ? s : OBJECT_MAPPER.writeValueAsString(function.get("arguments"));
                Map<String, Object> arguments = argsJson != null && !argsJson.isBlank()
                        ? OBJECT_MAPPER.readValue(argsJson, new TypeReference<>() {})
                        : Map.of();
                toolCalls.add(new AgentToolCall(name, arguments));
            }
        }

        return new AgentToolCall.AgentResult(toolCalls, content);
    }

    private String resolveBaseUrl() {
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl;
        }
        String env = System.getenv("LLM_BASE_URL");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty("llm.base-url");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return "https://api.openai.com/v1";
    }

    private String resolveApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        String env = System.getenv("LLM_API_KEY");
        if (env != null && !env.isBlank()) {
            return env;
        }
        String prop = System.getProperty("llm.api-key");
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        return "";
    }
}
