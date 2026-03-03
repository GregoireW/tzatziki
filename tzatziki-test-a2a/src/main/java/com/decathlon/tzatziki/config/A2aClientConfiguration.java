package com.decathlon.tzatziki.config;

import com.decathlon.tzatziki.steps.A2aSteps;
import com.decathlon.tzatziki.utils.A2aEvent;
import com.decathlon.tzatziki.utils.A2aResponse;
import com.decathlon.tzatziki.utils.Mapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration for the A2A client used in step definitions.
 * Manages the HTTP client and provides methods for A2A protocol operations.
 */
@Slf4j
public class A2aClientConfiguration {

    @Setter
    private static String baseUrl;

    @Setter
    private static String agentCardPath = "/.well-known/agent.json";

    @Setter
    private static Duration timeout = Duration.ofSeconds(30);

    @Getter
    private final HttpClient httpClient;

    public A2aClientConfiguration() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Fetches the agent card from the well-known endpoint.
     */
    public Map<String, Object> getAgentCard() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + agentCardPath))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(timeout)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("Failed to fetch agent card: HTTP " + response.statusCode());
            }
            return Mapper.read(response.body());
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch agent card from " + baseUrl, e);
        }
    }

    /**
     * Sends a message to the A2A server using the HTTP+JSON/REST binding.
     */
    @SuppressWarnings("unchecked")
    public A2aResponse sendMessage(Map<String, Object> sendMessageRequest) {
        try {
            String body = Mapper.toJson(sendMessageRequest);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/message:send"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(timeout)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return A2aResponse.fromError(response.statusCode(), response.body());
            }

            Map<String, Object> responseBody = Mapper.read(response.body());
            if (responseBody.containsKey("id") && responseBody.containsKey("status")) {
                return A2aResponse.fromTask(responseBody);
            } else if (responseBody.containsKey("messageId") || responseBody.containsKey("parts")) {
                return A2aResponse.fromMessage(responseBody);
            }
            return A2aResponse.fromTask(responseBody);
        } catch (Exception e) {
            return A2aResponse.fromError(0, e.getMessage());
        }
    }

    /**
     * Sends a streaming message and collects events.
     */
    @SuppressWarnings("unchecked")
    public void sendStreamingMessage(Map<String, Object> sendMessageRequest, List<A2aEvent> events) {
        try {
            String body = Mapper.toJson(sendMessageRequest);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/message:stream"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(timeout)
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body()))) {
                StringBuilder eventData = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data:")) {
                        eventData.append(line.substring(5).trim());
                    } else if (line.isEmpty() && !eventData.isEmpty()) {
                        Map<String, Object> data = Mapper.read(eventData.toString());
                        parseStreamEvent(data, events);
                        eventData.setLength(0);
                    }
                }
                // Handle last event if no trailing newline
                if (!eventData.isEmpty()) {
                    Map<String, Object> data = Mapper.read(eventData.toString());
                    parseStreamEvent(data, events);
                }
            }
        } catch (Exception e) {
            log.error("Error during streaming: {}", e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseStreamEvent(Map<String, Object> data, List<A2aEvent> events) {
        if (data.containsKey("status") && data.containsKey("taskId") && !data.containsKey("artifacts")) {
            if (data.containsKey("artifact")) {
                events.add(A2aEvent.fromTaskArtifactUpdate(data));
            } else {
                events.add(A2aEvent.fromTaskStatusUpdate(data));
            }
        } else if (data.containsKey("id") && data.containsKey("status")) {
            events.add(A2aEvent.fromTask(data));
        } else if (data.containsKey("messageId") || data.containsKey("parts")) {
            events.add(A2aEvent.fromMessage(data));
        } else if (data.containsKey("artifact")) {
            events.add(A2aEvent.fromTaskArtifactUpdate(data));
        } else {
            events.add(A2aEvent.fromTaskStatusUpdate(data));
        }
    }

    /**
     * Gets a task by ID.
     */
    public A2aResponse getTask(String taskId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tasks/" + taskId))
                    .header("Accept", "application/json")
                    .GET()
                    .timeout(timeout)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return A2aResponse.fromError(response.statusCode(), response.body());
            }
            return A2aResponse.fromTask(Mapper.read(response.body()));
        } catch (Exception e) {
            return A2aResponse.fromError(0, e.getMessage());
        }
    }

    /**
     * Cancels a task.
     */
    public A2aResponse cancelTask(String taskId) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/tasks/" + taskId + ":cancel"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .timeout(timeout)
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                return A2aResponse.fromError(response.statusCode(), response.body());
            }
            return A2aResponse.fromTask(Mapper.read(response.body()));
        } catch (Exception e) {
            return A2aResponse.fromError(0, e.getMessage());
        }
    }

    /**
     * Builds a SendMessageRequest body from simple text content.
     */
    public static Map<String, Object> buildTextMessageRequest(String text) {
        return Map.of(
                "message", Map.of(
                        "messageId", UUID.randomUUID().toString(),
                        "role", "user",
                        "parts", List.of(Map.of("text", text))
                )
        );
    }

    /**
     * Builds a SendMessageRequest body from text and a context/task ID.
     */
    public static Map<String, Object> buildTextMessageRequest(String text, String contextId, String taskId) {
        return Map.of(
                "message", Map.of(
                        "messageId", UUID.randomUUID().toString(),
                        "role", "user",
                        "contextId", contextId != null ? contextId : "",
                        "taskId", taskId != null ? taskId : "",
                        "parts", List.of(Map.of("text", text))
                )
        );
    }
}
