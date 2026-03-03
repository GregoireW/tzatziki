package com.decathlon.tzatziki.steps;

import com.decathlon.tzatziki.config.A2aClientConfiguration;
import com.decathlon.tzatziki.utils.*;
import io.cucumber.java.AfterAll;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Type;
import java.util.*;

import static com.decathlon.tzatziki.utils.Comparison.COMPARING_WITH;
import static com.decathlon.tzatziki.utils.Guard.GUARD;
import static com.decathlon.tzatziki.utils.Mapper.readAsAListOf;
import static com.decathlon.tzatziki.utils.Patterns.*;

@Slf4j
@SuppressWarnings("java:S100")
public class A2aSteps {

    private static final String A2A_RESPONSE_KEY = "_a2aResponse";
    private static final String A2A_AGENT_CARD_KEY = "_a2aAgentCard";

    @Getter
    private static final List<A2aEvent> a2aEvents = Collections.synchronizedList(new ArrayList<>());

    private static A2aClientConfiguration a2aClientConfiguration;

    private final ObjectSteps objects;

    public A2aSteps(ObjectSteps objects) {
        this.objects = objects;
        if (a2aClientConfiguration == null) {
            a2aClientConfiguration = new A2aClientConfiguration();
        }
        A2aClientConfiguration.setAgentCardPath("/.well-known/agent.json");
        a2aEvents.clear();
    }

    @AfterAll
    public static void afterAll() {
        a2aClientConfiguration = null;
    }

    // ==================== AGENT CARD ====================

    @When(THAT + GUARD + "the A2A agent card path is " + QUOTED_CONTENT + "$")
    public void the_agent_card_path_is(Guard guard, String path) {
        guard.in(objects, () -> A2aClientConfiguration.setAgentCardPath(objects.resolve(path)));
    }

    @Then(THAT + GUARD + "the A2A agent card contains" + COMPARING_WITH + ":$")
    public void the_agent_card_contains(Guard guard, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            Map<String, Object> agentCard = a2aClientConfiguration.getAgentCard();
            objects.add(A2A_AGENT_CARD_KEY, agentCard);
            Map<String, Object> expected = Mapper.read(objects.resolve(content));
            comparison.compare(agentCard, expected);
        });
    }

    @Then(THAT + GUARD + "the A2A agent card skills contains" + COMPARING_WITH + ":$")
    public void the_agent_card_skills_contains(Guard guard, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            Map<String, Object> agentCard = a2aClientConfiguration.getAgentCard();
            objects.add(A2A_AGENT_CARD_KEY, agentCard);
            List<Map> expected = readAsAListOf(objects.resolve(content), Map.class);
            comparison.compare(agentCard.get("skills"), expected);
        });
    }

    // ==================== SEND MESSAGE ====================

    @When(THAT + GUARD + "we send an A2A message " + QUOTED_CONTENT + "$")
    public void send_a2a_message_inline(Guard guard, String text) {
        guard.in(objects, () -> sendMessage(objects.resolve(text), null));
    }

    @When(THAT + GUARD + "we send an A2A message:$")
    public void send_a2a_message(Guard guard, String content) {
        guard.in(objects, () -> {
            String resolved = objects.resolve(content);
            // If the content looks like a structured request (has 'message' key), use it directly
            try {
                Map<String, Object> parsed = Mapper.read(resolved);
                if (parsed.containsKey("message")) {
                    A2aResponse response = a2aClientConfiguration.sendMessage(parsed);
                    objects.add(A2A_RESPONSE_KEY, response);
                    return;
                }
            } catch (Exception ignored) {
                // Not a map, treat as plain text
            }
            sendMessage(resolved.trim(), null);
        });
    }

    @When(THAT + GUARD + "we send an A2A message " + QUOTED_CONTENT + " to task " + QUOTED_CONTENT + "$")
    public void send_a2a_message_to_task(Guard guard, String text, String taskId) {
        guard.in(objects, () -> {
            A2aResponse previous = objects.get(A2A_RESPONSE_KEY);
            String contextId = previous != null ? previous.getContextId() : null;
            sendMessage(objects.resolve(text), Map.of(
                    "contextId", contextId != null ? contextId : "",
                    "taskId", objects.resolve(taskId)
            ));
        });
    }

    @When(THAT + GUARD + "we send an A2A message with configuration:$")
    public void send_a2a_message_with_config(Guard guard, String content) {
        guard.in(objects, () -> {
            Map<String, Object> request = Mapper.read(objects.resolve(content));
            A2aResponse response = a2aClientConfiguration.sendMessage(request);
            objects.add(A2A_RESPONSE_KEY, response);
        });
    }

    private void sendMessage(String text, Map<String, Object> extra) {
        Map<String, Object> request;
        if (extra != null && extra.containsKey("taskId")) {
            request = A2aClientConfiguration.buildTextMessageRequest(
                    text,
                    (String) extra.get("contextId"),
                    (String) extra.get("taskId"));
        } else {
            request = A2aClientConfiguration.buildTextMessageRequest(text);
        }
        A2aResponse response = a2aClientConfiguration.sendMessage(request);
        objects.add(A2A_RESPONSE_KEY, response);
    }

    // ==================== STREAMING ====================

    @When(THAT + GUARD + "we stream an A2A message:$")
    public void stream_a2a_message(Guard guard, String content) {
        guard.in(objects, () -> {
            String resolved = objects.resolve(content);
            Map<String, Object> request;
            try {
                Map<String, Object> parsed = Mapper.read(resolved);
                request = parsed.containsKey("message") ? parsed :
                        A2aClientConfiguration.buildTextMessageRequest(resolved.trim());
            } catch (Exception e) {
                request = A2aClientConfiguration.buildTextMessageRequest(resolved.trim());
            }
            a2aClientConfiguration.sendStreamingMessage(request, a2aEvents);
        });
    }

    @When(THAT + GUARD + "we stream an A2A message " + QUOTED_CONTENT + "$")
    public void stream_a2a_message_inline(Guard guard, String text) {
        guard.in(objects, () -> {
            Map<String, Object> request = A2aClientConfiguration.buildTextMessageRequest(objects.resolve(text));
            a2aClientConfiguration.sendStreamingMessage(request, a2aEvents);
        });
    }

    // ==================== RESPONSE ASSERTIONS ====================

    @Then(THAT + GUARD + A_USER + "receive(?:s|d)? from A2A" + COMPARING_WITH + "(?: " + A + TYPE + ")?:$")
    public void we_receive_from_a2a(Guard guard, Comparison comparison, Type type, String content) {
        guard.in(objects, () -> {
            A2aResponse response = objects.get(A2A_RESPONSE_KEY);
            String payload = objects.resolve(content);
            Map<String, Object> expected = Mapper.read(payload);

            if (A2aResponse.class.equals(type)) {
                comparison.compare(response, expected);
            } else if (response.isTask()) {
                Map<String, Object> actual = new LinkedHashMap<>();
                if (response.getTaskId() != null) actual.put("id", response.getTaskId());
                if (response.getContextId() != null) actual.put("contextId", response.getContextId());
                if (response.getStatus() != null) actual.put("status", response.getStatus());
                if (response.getArtifacts() != null) actual.put("artifacts", response.getArtifacts());
                if (response.getHistory() != null) actual.put("history", response.getHistory());
                if (response.getMetadata() != null) actual.put("metadata", response.getMetadata());
                comparison.compare(actual, expected);
            } else if (response.getMessage() != null) {
                comparison.compare(response.getMessage(), expected);
            }
        });
    }

    @Then(THAT + GUARD + "the A2A task status is " + QUOTED_CONTENT + "$")
    public void the_task_status_is(Guard guard, String expectedState) {
        guard.in(objects, () -> {
            A2aResponse response = objects.get(A2A_RESPONSE_KEY);
            String actualState = response.getTaskState();
            if (!objects.resolve(expectedState).equalsIgnoreCase(actualState)) {
                throw new AssertionError("Expected task state '" + expectedState + "' but got '" + actualState + "'");
            }
        });
    }

    @Then(THAT + GUARD + "the A2A response contains an error$")
    public void the_response_contains_an_error(Guard guard) {
        guard.in(objects, () -> {
            A2aResponse response = objects.get(A2A_RESPONSE_KEY);
            if (response == null || !response.isError()) {
                throw new AssertionError("Expected an error but got a successful response");
            }
        });
    }

    // ==================== TASK MANAGEMENT ====================

    @When(THAT + GUARD + "we get the A2A task$")
    public void get_a2a_task(Guard guard) {
        guard.in(objects, () -> {
            A2aResponse previous = objects.get(A2A_RESPONSE_KEY);
            if (previous == null || previous.getTaskId() == null) {
                throw new AssertionError("No task ID available. Send a message first.");
            }
            A2aResponse response = a2aClientConfiguration.getTask(previous.getTaskId());
            objects.add(A2A_RESPONSE_KEY, response);
        });
    }

    @When(THAT + GUARD + "we get the A2A task " + QUOTED_CONTENT + "$")
    public void get_a2a_task_by_id(Guard guard, String taskId) {
        guard.in(objects, () -> {
            A2aResponse response = a2aClientConfiguration.getTask(objects.resolve(taskId));
            objects.add(A2A_RESPONSE_KEY, response);
        });
    }

    @When(THAT + GUARD + "we cancel the A2A task$")
    public void cancel_a2a_task(Guard guard) {
        guard.in(objects, () -> {
            A2aResponse previous = objects.get(A2A_RESPONSE_KEY);
            if (previous == null || previous.getTaskId() == null) {
                throw new AssertionError("No task ID available. Send a message first.");
            }
            A2aResponse response = a2aClientConfiguration.cancelTask(previous.getTaskId());
            objects.add(A2A_RESPONSE_KEY, response);
        });
    }

    @When(THAT + GUARD + "we cancel the A2A task " + QUOTED_CONTENT + "$")
    public void cancel_a2a_task_by_id(Guard guard, String taskId) {
        guard.in(objects, () -> {
            A2aResponse response = a2aClientConfiguration.cancelTask(objects.resolve(taskId));
            objects.add(A2A_RESPONSE_KEY, response);
        });
    }

    // ==================== EVENTS ====================

    @Then(THAT + GUARD + "the A2A events (?:list )?contains" + COMPARING_WITH + ":$")
    public void the_a2a_events_contains(Guard guard, Comparison comparison, Object content) {
        guard.in(objects, () -> {
            List<Map> expected = readAsAListOf(objects.resolve(content), Map.class);
            comparison.compare(a2aEvents, expected);
        });
    }
}
