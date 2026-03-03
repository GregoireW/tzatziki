Tzatziki A2A Test Library
======

## Description

This module provides steps to interact with and test [A2A (Agent-to-Agent)](https://a2aproject.github.io/A2A/) protocol servers.
It allows you to validate agent cards, send messages, manage tasks, handle streaming events, and assert responses.

It is agnostic of the language used to implement the A2A server (Java, Python, TypeScript, Go, etc.), as long as it speaks the A2A protocol using the HTTP+JSON/REST binding.

You can find concrete setup and example tests in the [test folder](src/test) of this module.

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-test-a2a</artifactId>
    <version>x.x.x</version>
    <scope>test</scope>
</dependency>
```

### Configuration

Before running your tests, you need to configure the base URL of the A2A server.

```java
import com.decathlon.tzatziki.config.A2aClientConfiguration;
import io.cucumber.java.Before;

public class MySteps {

    @Before(order = -1)
    public void setup() {
        A2aClientConfiguration.setBaseUrl("http://localhost:8080");
    }
}
```

Example using Spring Boot Test:

```java
import com.decathlon.tzatziki.config.A2aClientConfiguration;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = MyA2aServerApplication.class)
public class MyA2aServerSteps {

    @LocalServerPort
    private Integer serverPort;

    @Before(order = -1)
    public void setup() {
        A2aClientConfiguration.setBaseUrl("http://localhost:" + serverPort);
    }
}
```

## Usage

### Agent Card Discovery

Validate the agent card returned by the server at `GET /.well-known/agent.json`:

```gherkin
# Validate required agent card fields
Then the A2A agent card contains:
  """
  name: "My Agent"
  description: "An agent that does things"
  version: "1.0.0"
  capabilities:
    streaming: true
  """

# Validate skills separately
Then the A2A agent card skills contains:
  """
  - id: "my-skill"
    name: "My Skill"
    description: "Does something useful"
    tags:
      - "useful"
  """
```

### Sending Messages

Send messages to the A2A server via `POST /message:send`:

```gherkin
# Simple inline message
When we send an A2A message "Hello, World!"

# Message with docstring
When we send an A2A message:
  """
  What is the weather in Paris?
  """

# Full request with configuration
When we send an A2A message with configuration:
  """
  message:
    messageId: "msg-001"
    role: "user"
    parts:
      - text: "Hello"
  configuration:
    acceptedOutputModes:
      - "text/plain"
    blocking: true
  """

# Send a follow-up message to an existing task
When we send an A2A message "Follow up" to task "task-123"
```

### Asserting Responses

Assert on the response received from the server. The response can be either a **Task** or a **Message**:

```gherkin
# Assert on a task response
Then we receive from A2A:
  """
  status:
    state: "completed"
  artifacts:
    - parts:
      - text: "The answer is 42"
  """

# Assert on a direct message response
Then we receive from A2A:
  """
  role: "agent"
  parts:
    - text: "Hello back!"
  """

# Assert on the full A2aResponse object
Then we receive from A2A a A2aResponse:
  """
  taskId: "?notNull"
  isError: false
  """

# Quick status check
Then the A2A task status is "completed"
```

### Task Management

Manage tasks via `GET /tasks/{id}` and `POST /tasks/{id}:cancel`:

```gherkin
# Get the last task (uses the task ID from the previous response)
When we get the A2A task
Then the A2A task status is "completed"

# Get a specific task by ID
When we get the A2A task "task-123"

# Cancel the last task
When we cancel the A2A task
Then the A2A task status is "canceled"

# Cancel a specific task
When we cancel the A2A task "task-123"
```

### Streaming

Send streaming messages via `POST /message:stream` (SSE):

```gherkin
When we stream an A2A message:
  """
  Hello streaming!
  """
Then the A2A events list contains:
  """
  - type: "TASK"
    payload:
      status:
        state: "submitted"
  - type: "TASK_STATUS_UPDATE"
    payload:
      status:
        state: "working"
  - type: "TASK_ARTIFACT_UPDATE"
    payload:
      artifact:
        parts:
          - text: "Echo: Hello streaming!"
  - type: "TASK_STATUS_UPDATE"
    payload:
      status:
        state: "completed"
  """
```

### Error Handling

Assert that a response contains an error:

```gherkin
When we get the A2A task "non-existent-id"
Then the A2A response contains an error
```

## A2A Protocol Coverage

This module tests the following aspects of the [A2A protocol specification](https://a2aproject.github.io/A2A/latest/specification/):

| Feature | Endpoint | Description |
|---------|----------|-------------|
| **Agent Card** | `GET /.well-known/agent.json` | Validate agent identity, capabilities, skills, interfaces |
| **Send Message** | `POST /message:send` | Send messages and receive Task or Message responses |
| **Stream Message** | `POST /message:stream` | Real-time streaming with SSE events |
| **Get Task** | `GET /tasks/{id}` | Retrieve task state, artifacts, and history |
| **Cancel Task** | `POST /tasks/{id}:cancel` | Cancel running tasks |
| **Task Lifecycle** | - | Verify state transitions: submitted → working → completed/failed/canceled |
| **Error Handling** | - | Task not found, invalid requests |
| **Multi-turn** | - | Follow-up messages to existing tasks |

## Event Types

The following event types are captured during streaming:

* `TASK`: Initial task creation
* `TASK_STATUS_UPDATE`: Task state changes (submitted, working, completed, failed, canceled)
* `TASK_ARTIFACT_UPDATE`: New artifacts or artifact chunks
* `MESSAGE`: Direct message responses
