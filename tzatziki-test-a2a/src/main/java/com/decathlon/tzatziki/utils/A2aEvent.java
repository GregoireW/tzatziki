package com.decathlon.tzatziki.utils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Represents an event received from an A2A server during streaming.
 */
@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class A2aEvent {

    public enum EventType {
        TASK,
        MESSAGE,
        TASK_STATUS_UPDATE,
        TASK_ARTIFACT_UPDATE
    }

    private EventType type;
    private Instant timestamp;
    private Object payload;

    public static A2aEvent fromTaskStatusUpdate(Map<String, Object> data) {
        return A2aEvent.builder()
                .type(EventType.TASK_STATUS_UPDATE)
                .timestamp(Instant.now())
                .payload(data)
                .build();
    }

    public static A2aEvent fromTaskArtifactUpdate(Map<String, Object> data) {
        return A2aEvent.builder()
                .type(EventType.TASK_ARTIFACT_UPDATE)
                .timestamp(Instant.now())
                .payload(data)
                .build();
    }

    public static A2aEvent fromTask(Map<String, Object> data) {
        return A2aEvent.builder()
                .type(EventType.TASK)
                .timestamp(Instant.now())
                .payload(data)
                .build();
    }

    public static A2aEvent fromMessage(Map<String, Object> data) {
        return A2aEvent.builder()
                .type(EventType.MESSAGE)
                .timestamp(Instant.now())
                .payload(data)
                .build();
    }
}
