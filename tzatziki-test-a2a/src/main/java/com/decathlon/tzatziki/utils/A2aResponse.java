package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * A2A Response - represents a response from an A2A server.
 * Wraps either a Task or a Message, following the A2A protocol specification.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2aResponse {

    private String taskId;
    private String contextId;
    private Map<String, Object> status;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<Map<String, Object>> artifacts;
    private List<Map<String, Object>> history;
    private Map<String, Object> metadata;

    private Map<String, Object> message;

    private boolean isError;
    private String error;
    private int httpStatus;

    /**
     * Returns the task state from the status field.
     */
    public String getTaskState() {
        if (status != null && status.containsKey("state")) {
            return String.valueOf(status.get("state"));
        }
        return null;
    }

    /**
     * Returns true if the response contains a task (vs a direct message).
     */
    public boolean isTask() {
        return taskId != null;
    }

    /**
     * Returns the response as a Map for comparison purposes.
     */
    public Map<String, Object> asMap() {
        return Mapper.read(Mapper.toJson(this));
    }

    /**
     * Creates an A2aResponse from a parsed JSON Map representing a Task.
     */
    @SuppressWarnings("unchecked")
    public static A2aResponse fromTask(Map<String, Object> taskMap) {
        A2aResponseBuilder builder = A2aResponse.builder();
        if (taskMap.containsKey("id")) builder.taskId(String.valueOf(taskMap.get("id")));
        if (taskMap.containsKey("contextId")) builder.contextId(String.valueOf(taskMap.get("contextId")));
        if (taskMap.get("status") instanceof Map) builder.status((Map<String, Object>) taskMap.get("status"));
        if (taskMap.get("artifacts") instanceof List) builder.artifacts((List<Map<String, Object>>) taskMap.get("artifacts"));
        if (taskMap.get("history") instanceof List) builder.history((List<Map<String, Object>>) taskMap.get("history"));
        if (taskMap.get("metadata") instanceof Map) builder.metadata((Map<String, Object>) taskMap.get("metadata"));
        return builder.build();
    }

    /**
     * Creates an A2aResponse from a parsed JSON Map representing a Message.
     */
    public static A2aResponse fromMessage(Map<String, Object> messageMap) {
        return A2aResponse.builder()
                .message(messageMap)
                .build();
    }

    /**
     * Creates an error A2aResponse.
     */
    public static A2aResponse fromError(int httpStatus, String error) {
        return A2aResponse.builder()
                .httpStatus(httpStatus)
                .isError(true)
                .error(error)
                .build();
    }
}
