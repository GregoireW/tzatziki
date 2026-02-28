Feature: MCP Agent Tool Calling Testing
  Test that LLM agents correctly select (or don't select) MCP tools based on conversation context.
  These tests use a mocked LLM endpoint to verify the step definitions work correctly.

  # ==================== TOOL NOT CALLED ====================

  Scenario: Tool should not be called when the prompt is ambiguous
    Given the following agent tools:
      """
      - name: orderList
        description: Orders a list of items in a store
      """
    And that posting "/chat/completions" will return:
      """json
      {
        "choices": [{
          "message": {
            "role": "assistant",
            "content": "Here is your sorted list: 1, 3, 5, 9"
          }
        }]
      }
      """
    When the agent processes the conversation with "gpt-4o-mini":
      """
      - role: user
        content: order me the list 5,9,1,3
      """
    Then the tool "orderList" should not have been called
    And the agent should not have called any tool

  # ==================== TOOL CALLED ====================

  Scenario: Correct tool should be selected from multi-turn conversation
    Given the following agent tools:
      """
      - name: orderValidatedList
        description: Returns the list of validated orders
      - name: orderDeliveredList
        description: Returns the list of delivered orders
      """
    And that posting "/chat/completions" will return:
      """json
      {
        "choices": [{
          "message": {
            "role": "assistant",
            "content": null,
            "tool_calls": [{
              "id": "call_123",
              "type": "function",
              "function": {
                "name": "orderDeliveredList",
                "arguments": "{}"
              }
            }]
          }
        }]
      }
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
    And the agent should have called 1 tool

  # ==================== MULTIPLE TOOL CALLS ====================

  Scenario: Agent can call multiple tools
    Given the following agent tools:
      """
      - name: getWeather
        description: Gets the current weather for a location
      - name: getCalendar
        description: Gets the calendar events for today
      """
    And that posting "/chat/completions" will return:
      """json
      {
        "choices": [{
          "message": {
            "role": "assistant",
            "content": null,
            "tool_calls": [
              {
                "id": "call_1",
                "type": "function",
                "function": {
                  "name": "getWeather",
                  "arguments": "{\"location\": \"Paris\"}"
                }
              },
              {
                "id": "call_2",
                "type": "function",
                "function": {
                  "name": "getCalendar",
                  "arguments": "{}"
                }
              }
            ]
          }
        }]
      }
      """
    When the agent processes the conversation with "gpt-4o-mini":
      """
      - role: user
        content: What is the weather like today in Paris and what do I have on my calendar?
      """
    Then the tool "getWeather" should have been called
    And the tool "getCalendar" should have been called
    And the agent should have called 2 tools
