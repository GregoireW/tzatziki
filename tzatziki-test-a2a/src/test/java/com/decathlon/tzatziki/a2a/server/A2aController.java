package com.decathlon.tzatziki.a2a.server;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Minimal A2A server implementing the HTTP+JSON/REST binding for testing.
 * Implements: Agent Card, Send Message, Get Task, Cancel Task, and Streaming.
 */
@RestController
public class A2aController {

    private final Map<String, Map<String, Object>> tasks = new ConcurrentHashMap<>();

    @GetMapping("/.well-known/agent.json")
    public Map<String, Object> getAgentCard() {
        return buildAgentCard();
    }

    @GetMapping("/custom/agents/echo-agent.json")
    public Map<String, Object> getCustomAgentCard() {
        return buildAgentCard();
    }

    private Map<String, Object> buildAgentCard() {
        return Map.ofEntries(
                Map.entry("name", "Echo Agent"),
                Map.entry("description", "A simple echo agent for testing the A2A protocol"),
                Map.entry("version", "1.0.0"),
                Map.entry("supportedInterfaces", List.of(
                        Map.of(
                                "url", "http://localhost:0",
                                "protocolBinding", "HTTP+JSON",
                                "protocolVersion", "0.3"
                        )
                )),
                Map.entry("capabilities", Map.of(
                        "streaming", true,
                        "pushNotifications", false
                )),
                Map.entry("defaultInputModes", List.of("text/plain")),
                Map.entry("defaultOutputModes", List.of("text/plain")),
                Map.entry("skills", List.of(
                        Map.of(
                                "id", "echo",
                                "name", "Echo",
                                "description", "Echoes back the user's message",
                                "tags", List.of("echo", "test")
                        ),
                        Map.of(
                                "id", "slow-task",
                                "name", "Slow Task",
                                "description", "Simulates a long-running task that can be cancelled",
                                "tags", List.of("async", "test")
                        )
                ))
        );
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/message:send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, Object> request) {
        Map<String, Object> message = (Map<String, Object>) request.get("message");
        if (message == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing 'message' field"));
        }

        List<Map<String, Object>> parts = (List<Map<String, Object>>) message.get("parts");
        String userText = extractText(parts);
        String contextId = (String) message.getOrDefault("contextId", UUID.randomUUID().toString());

        // Check for "direct:" prefix to return a Message instead of a Task
        if (userText != null && userText.startsWith("direct:")) {
            String echoText = "Echo: " + userText.substring(7);
            return ResponseEntity.ok(Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "role", "agent",
                    "parts", List.of(Map.of("text", echoText))
            ));
        }

        String taskId = (String) message.getOrDefault("taskId", UUID.randomUUID().toString());

        String echoText = "Echo: " + (userText != null ? userText : "");
        Map<String, Object> task = buildTask(taskId, contextId, "completed",
                echoText, "Echo: " + userText);

        tasks.put(taskId, task);
        return ResponseEntity.ok(task);
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/message:stream")
    public void streamMessage(@RequestBody Map<String, Object> request, HttpServletResponse response) throws IOException {
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> message = (Map<String, Object>) request.get("message");
        List<Map<String, Object>> parts = message != null ?
                (List<Map<String, Object>>) message.get("parts") : List.of();
        String userText = extractText(parts);
        String taskId = UUID.randomUUID().toString();
        String contextId = UUID.randomUUID().toString();

        PrintWriter writer = response.getWriter();

        // Event 1: Task with submitted status
        sendSseEvent(writer, buildSseData(Map.of(
                "id", taskId,
                "contextId", contextId,
                "status", Map.of("state", "submitted")
        )));

        // Event 2: Status update to working
        sendSseEvent(writer, buildSseData(Map.of(
                "taskId", taskId,
                "contextId", contextId,
                "status", Map.of("state", "working")
        )));

        // Event 3: Artifact update
        sendSseEvent(writer, buildSseData(Map.of(
                "taskId", taskId,
                "contextId", contextId,
                "artifact", Map.of(
                        "artifactId", UUID.randomUUID().toString(),
                        "name", "response",
                        "parts", List.of(Map.of("text", "Echo: " + userText))
                ),
                "lastChunk", true
        )));

        // Event 4: Status update to completed
        sendSseEvent(writer, buildSseData(Map.of(
                "taskId", taskId,
                "contextId", contextId,
                "status", Map.of(
                        "state", "completed",
                        "message", Map.of(
                                "messageId", UUID.randomUUID().toString(),
                                "role", "agent",
                                "parts", List.of(Map.of("text", "Echo: " + userText))
                        )
                )
        )));

        writer.flush();
        writer.close();
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable String taskId) {
        Map<String, Object> task = tasks.get(taskId);
        if (task == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found: " + taskId));
        }
        return ResponseEntity.ok(task);
    }

    @PostMapping("/tasks/{taskId}:cancel")
    public ResponseEntity<Map<String, Object>> cancelTask(@PathVariable String taskId) {
        Map<String, Object> task = tasks.get(taskId);
        if (task == null) {
            return ResponseEntity.status(404).body(Map.of("error", "Task not found: " + taskId));
        }

        // Update task status to canceled
        Map<String, Object> updatedTask = new LinkedHashMap<>(task);
        updatedTask.put("status", Map.of("state", "canceled"));
        tasks.put(taskId, updatedTask);
        return ResponseEntity.ok(updatedTask);
    }

    private String extractText(List<Map<String, Object>> parts) {
        if (parts == null) return null;
        return parts.stream()
                .filter(p -> p.containsKey("text"))
                .map(p -> (String) p.get("text"))
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> buildTask(String taskId, String contextId, String state,
                                          String statusMessage, String artifactText) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", taskId);
        task.put("contextId", contextId);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("state", state);
        if (statusMessage != null) {
            status.put("message", Map.of(
                    "messageId", UUID.randomUUID().toString(),
                    "role", "agent",
                    "parts", List.of(Map.of("text", statusMessage))
            ));
        }
        task.put("status", status);

        if (artifactText != null) {
            task.put("artifacts", List.of(Map.of(
                    "artifactId", UUID.randomUUID().toString(),
                    "name", "response",
                    "parts", List.of(Map.of("text", artifactText))
            )));
        }
        return task;
    }

    private String buildSseData(Map<String, Object> data) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void sendSseEvent(PrintWriter writer, String data) {
        writer.write("data:" + data + "\n\n");
        writer.flush();
    }
}
