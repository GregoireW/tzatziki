Tzatziki MCP Test Library
======

## Description

This module provides steps to interact with and test [MCP (Model Context Protocol)](https://modelcontextprotocol.io/) servers.
It allows you to list available tools, resources, and prompts, call them, assert the responses, and receive event notifications.

It is agnostic of the language used to implement the MCP server (Java, Python, TypeScript, etc.), as long as it speaks the MCP protocol.

You can find concrete setup and example tests in the [test folder](src/test) of this module.

## Get started with this module

You need to add this dependency to your project:

```xml
<dependency>
    <groupId>com.decathlon.tzatziki</groupId>
    <artifactId>tzatziki-test-mcp</artifactId>
    <version>x.x.x</version>
    <scope>test</scope>
</dependency>
```

### Configuration

Before running your tests, you need to configure the `McpClientTransport` that will be used to communicate with your MCP server.
Example using `HttpClientStreamableHttpTransport` (for HTTP servers):

```java
import com.decathlon.tzatziki.config.McpClientConfiguration;
import io.cucumber.java.Before;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;

public class MySteps {

    @Before(order = -1)
    public void setup() {
        McpClientConfiguration.setMcpClientTransport(
            HttpClientStreamableHttpTransport.builder("http://localhost:8080").build()
        );
    }
}
```

Example using `StdioClientTransport` (for local processes, e.g. Python, Node.js):

```java
import com.decathlon.tzatziki.config.McpClientConfiguration;
import io.cucumber.java.Before;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonMapper;

public class MySteps {

    @Before(order = -1)
    public void setup() {
        McpClientConfiguration.setMcpClientTransport(
                new StdioClientTransport(ServerParameters.builder("npx")
                        .args("-y", "@modelcontextprotocol/server-everything", "stdio").build(), 
                        McpJsonMapper.createDefault())
        );
    }
}
```

Example using Spring Boot Test (e.g. Spring AI MCP):

```java
import com.decathlon.tzatziki.config.McpClientConfiguration;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = MyMcpServerApplication.class)
public class MyMcpServerSteps {

    @LocalServerPort
    private Integer serverPort;

    @Before
    public void setup() {
        McpClientConfiguration.setMcpClientTransport(
            HttpClientStreamableHttpTransport.builder("http://localhost:" + serverPort).build()
        );
    }
}
```

### Advanced Configuration

You can configure advanced capabilities like Sampling, Elicitation, and Roots by setting the corresponding static fields in `McpClientConfiguration`.

#### Sampling (LLM Generation)

If the MCP server needs to request LLM sampling (generation) from the client (e.g. "human in the loop" or client-side LLM), you can provide a `samplingHandler`.

```java
McpClientConfiguration.setSamplingHandler(request ->
                    Mono.just(new McpSchema.CreateMessageResult(McpSchema.Role.ASSISTANT, request.messages().get(0).content(), "test-model",
                            McpSchema.CreateMessageResult.StopReason.END_TURN)));
```

#### Elicitation (User Input)

If the MCP server needs to ask the user for input or clarification, you can provide an `elicitationHandler`.

```java
McpClientConfiguration.setElicitationHandler(request -> Mono.just(McpSchema.ElicitResult.builder()
        .message(McpSchema.ElicitResult.Action.ACCEPT)
        .content(Map.of("message", "elicitation response"))
        .build()));
```

#### Roots

You can define the roots (directories or resources) that the client exposes to the server. This is often used to define the workspace boundaries for the server.

```java
McpClientConfiguration.setRoots(List.of(
    new McpSchema.Root("file:///path/to/project", "Project Root")
));
```

### Using Testcontainers (Optional)

If you're testing with an MCP server running in a Docker container (as shown in the test examples), you'll need to add the Testcontainers dependency:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>testcontainers</artifactId>
    <version>2.0.3</version>
    <scope>test</scope>
</dependency>
```

Example using GenericContainer:

```java
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

private static GenericContainer<?> mcpContainer;

@Before(order = -1)
public void startContainer() {
    if (mcpContainer == null || !mcpContainer.isRunning()) {
        mcpContainer = new GenericContainer<>("your-mcp-server-image")
                .withExposedPorts(8080)
                .waitingFor(Wait.forHttp("/").forStatusCode(200));
        mcpContainer.start();
        
        String host = "http://" + mcpContainer.getHost() + ":" + mcpContainer.getMappedPort(8080);
        McpClientConfiguration.setMcpClientTransport(
            HttpClientStreamableHttpTransport.builder(host).build()
        );
    }
}
```

## Usage

### Listing Capabilities

You can assert the list of available tools, resources, or prompts exposed by the MCP server.

```gherkin
# Check available tools with their schema
Then the tools contains:
  """
  - name: "getTemperature"
    description: "Get the temperature"
    inputSchema:
      type: "object"
      properties:
        latitude:
          type: "number"
        longitude:
          type: "number"
      required:
      - "latitude"
      - "longitude"
  """

# Check available resources
Then the resources contains:
  """
  - name: "config"
    uri: "file:///config.json"
  """

# Check available prompts with their arguments
Then the prompts contains:
  """
  - name: "summarize"
    description: "Summarize text"
    arguments:
    - name: "text"
      description: "The text to summarize"
      required: true
  """
```

### Calling Tools, Resources, and Prompts

You can call a tool, resource, or prompt and assert the response.
Arguments can be provided in JSON or YAML format.

#### Calling a Tool

```gherkin
When we call the tool "getTemperature":
  """
  latitude: 48.8566
  longitude: 2.3522
  """
Then we receive from mcp:
  """json
  {
    "current": {
      "temperature_2m": 20.5
    }
  }
  """
```

If the tool takes no arguments:

```gherkin
When we call the tool "printEnv"
Then we receive from mcp:
  """
  ENV_VAR=value
  """
```

You can also pass metadata (like `progressToken`) in the `request-meta` field:

```gherkin
When we call the tool "longRunningOperation":
  """
  request-meta:
    progressToken: my-progress-token
  duration: 10
  """
Then we receive from mcp:
  """
  Operation completed.
  """
```

#### Calling a Resource

```gherkin
When we call the resource "weather://data/cities"
Then we receive from mcp:
  """yml
  - name: "Paris"
    country: "FR"
  """
```

#### Calling a Prompt

```gherkin
When we call the prompt "summarize":
  """
  text: "History"
  """
Then we receive from mcp:
  """json
  {"role":"ASSISTANT","content":"Help me to summarize History..."}
  """
```

### Asserting Responses

You can assert the response content using `Then we receive from mcp:`.
If you want to assert the full `McpResponse` object (including error status, etc.), you can use `Then we receive from mcp a McpResponse:`.

```gherkin
Then we receive from mcp a McpResponse:
  """
  isError: false
  content:
    - type: "text"
      text: "The temperature is 20°C"
    - type: image
        annotations: {}
        payload: ?notNull   
  """
```

### Handling Errors

You can check if the response contains an error.

```gherkin
Then the response contains an error
```

### Subscriptions

You can subscribe and unsubscribe from resources.

```gherkin
When we subscribe to the resource "weather://alerts"
When we unsubscribe from the resource "weather://alerts"
```

### Events

You can assert that specific MCP events have occurred. The following events are captured:

*   `TOOLS_CHANGE`: When the list of available tools changes.
*   `RESOURCES_CHANGE`: When the list of available resources changes.
*   `RESOURCES_UPDATE`: When a subscribed resource is updated.
*   `PROMPTS_CHANGE`: When the list of available prompts changes.
*   `LOGGING`: When the server sends a log message.
*   `PROGRESS`: When the server sends a progress update.

```gherkin
Then the mcp events contains:
  """
  - type: "TOOLS_CHANGE"
    payload:
      - name: "new-tool"
  - type: "LOGGING"
    payload:
      level: "INFO"
      data: "Server started"
  """
```

## Agent Tool Calling Testing

This module also provides steps to test **MCP tool selection by LLM agents**. The idea is to verify that, given a set of tools and a conversation, an LLM correctly selects (or doesn't select) specific tools.

This is useful for validating tool naming, descriptions, and discoverability in real-world agent scenarios.

### Configuration

The agent steps use an OpenAI-compatible chat completions API. Configure the LLM endpoint via environment variables or system properties:

| Setting | Env Variable | System Property | Default |
|---------|-------------|-----------------|---------|
| Base URL | `LLM_BASE_URL` | `llm.base-url` | `https://api.openai.com/v1` |
| API Key | `LLM_API_KEY` | `llm.api-key` | *(empty)* |

You can also set them programmatically in a `@Before` step:

```java
import com.decathlon.tzatziki.utils.LlmClient;
import io.cucumber.java.Before;

public class MyAgentSteps {

    @Before(order = -1)
    public void setup() {
        LlmClient.setBaseUrl("https://api.openai.com/v1");
        LlmClient.setApiKey("sk-...");
    }
}
```

Any OpenAI-compatible endpoint works (OpenAI, Azure OpenAI, Anthropic, Ollama, vLLM, etc.).

### Defining Tools

Define the tools available to the agent. Each tool needs at least a `name` and `description`:

```gherkin
Given the following agent tools:
  """
  - name: orderValidatedList
    description: Returns the list of validated orders
  - name: orderDeliveredList
    description: Returns the list of delivered orders
  """
```

You can also include a `parameters` field with a JSON Schema if needed:

```gherkin
Given the following agent tools:
  """
  - name: getWeather
    description: Gets the current weather for a location
    parameters:
      type: object
      properties:
        location:
          type: string
          description: The city name
      required:
        - location
  """
```

### Running the Agent

Send a conversation to an LLM model. The conversation is a list of messages with `role` (system, user, assistant) and `content`:

```gherkin
When the agent processes the conversation with "gpt-4o-mini":
  """
  - role: system
    content: you are a specialist in IT support, help the users the best you can
  - role: user
    content: Give me my order list
  - role: assistant
    content: Which order you are looking for? the delivered or the one you just ordered?
  - role: user
    content: the delivered
  """
```

You can use Scenario Outlines to test the same conversation against multiple LLMs:

```gherkin
Scenario Outline: Correct tool selection with <model>
  Given the following agent tools:
    """
    - name: orderDeliveredList
      description: Returns the list of delivered orders
    """
  When the agent processes the conversation with "<model>":
    """
    - role: user
      content: Give me my delivered orders
    """
  Then the tool "orderDeliveredList" should have been called

  Examples:
    | model       |
    | gpt-4o-mini |
    | gpt-4o      |
```

### Asserting Tool Calls

Assert which tools the agent decided to call (or not call):

```gherkin
# Assert a specific tool was called
Then the tool "orderDeliveredList" should have been called

# Assert a specific tool was NOT called
And the tool "orderValidatedList" should not have been called

# Assert the exact number of tool calls
And the agent should have called 1 tool

# Assert no tools were called at all
Then the agent should not have called any tool
```

### Full Example

```gherkin
Feature: Order Management Tool Testing

  Scenario: Tool should not be called when intent is ambiguous
    Given the following agent tools:
      """
      - name: orderList
        description: Orders a list of items in a store
      """
    When the agent processes the conversation with "gpt-4o-mini":
      """
      - role: user
        content: order me the list 5,9,1,3
      """
    Then the tool "orderList" should not have been called

  Scenario: Correct tool selected from multi-turn conversation
    Given the following agent tools:
      """
      - name: orderValidatedList
        description: Returns the list of validated orders
      - name: orderDeliveredList
        description: Returns the list of delivered orders
      """
    When the agent processes the conversation with "gpt-4o-mini":
      """
      - role: system
        content: you are a specialist in IT support, help the users the best you can
      - role: user
        content: Give me my order list
      - role: assistant
        content: Which order you are looking for? the delivered or the one you just ordered?
      - role: user
        content: the delivered
      """
    Then the tool "orderDeliveredList" should have been called
    And the tool "orderValidatedList" should not have been called
```
