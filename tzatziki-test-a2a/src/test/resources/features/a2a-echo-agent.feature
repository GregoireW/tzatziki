Feature: A2A Echo Agent Testing

  # ==================== AGENT CARD DISCOVERY ====================

  Scenario: Validate agent card structure
    Then the A2A agent card contains:
    """
    name: "Echo Agent"
    description: "A simple echo agent for testing the A2A protocol"
    version: "1.0.0"
    capabilities:
      streaming: true
      pushNotifications: false
    defaultInputModes:
      - "text/plain"
    defaultOutputModes:
      - "text/plain"
    """

  Scenario: Validate agent card skills
    Then the A2A agent card skills contains:
    """
    - id: "echo"
      name: "Echo"
      description: "Echoes back the user's message"
      tags:
        - "echo"
        - "test"
    - id: "slow-task"
      name: "Slow Task"
      description: "Simulates a long-running task that can be cancelled"
      tags:
        - "async"
        - "test"
    """

  Scenario: Agent card has required supported interfaces
    Then the A2A agent card contains:
    """
    supportedInterfaces:
      - protocolBinding: "HTTP+JSON"
        protocolVersion: "0.3"
    """

  Scenario: Fetch agent card from a custom path
    Given the A2A agent card path is "/custom/agents/echo-agent.json"
    Then the A2A agent card contains:
    """
    name: "Echo Agent"
    version: "1.0.0"
    """

  # ==================== SEND MESSAGE ====================

  Scenario: Send a simple text message and receive a completed task
    When we send an A2A message "Hello, World!"
    Then we receive from A2A:
    """
    status:
      state: "completed"
    artifacts:
      - name: "response"
        parts:
          - text: "Echo: Hello, World!"
    """

  Scenario: Send a message and check task status
    When we send an A2A message "Test message"
    Then the A2A task status is "completed"

  Scenario: Send a message with docstring
    When we send an A2A message:
    """
    What is the weather in Paris?
    """
    Then we receive from A2A:
    """
    status:
      state: "completed"
    artifacts:
      - parts:
          - text: "Echo: What is the weather in Paris?"
    """

  Scenario: Receive a direct message response (no task)
    When we send an A2A message "direct:Hello!"
    Then we receive from A2A:
    """
    role: "agent"
    parts:
      - text: "Echo: Hello!"
    """

  # ==================== TASK MANAGEMENT ====================

  Scenario: Get task after sending a message
    When we send an A2A message "Remember me"
    Then the A2A task status is "completed"
    When we get the A2A task
    Then we receive from A2A:
    """
    status:
      state: "completed"
    """

  Scenario: Cancel a task
    When we send an A2A message "Cancel me"
    Then the A2A task status is "completed"
    When we cancel the A2A task
    Then the A2A task status is "canceled"

  # ==================== ERROR HANDLING ====================

  Scenario: Get a non-existent task returns error
    When we get the A2A task "non-existent-task-id"
    Then the A2A response contains an error

  Scenario: Cancel a non-existent task returns error
    When we cancel the A2A task "non-existent-task-id"
    Then the A2A response contains an error

  Scenario: Send a message with full request structure
    When we send an A2A message with configuration:
    """
    message:
      messageId: "test-msg-001"
      role: "user"
      parts:
        - text: "Structured request"
    configuration:
      acceptedOutputModes:
        - "text/plain"
    """
    Then the A2A task status is "completed"

  # ==================== STREAMING ====================

  Scenario: Stream a message and receive events
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
          name: "response"
          parts:
            - text: "Echo: Hello streaming!"
        lastChunk: true
    - type: "TASK_STATUS_UPDATE"
      payload:
        status:
          state: "completed"
    """
