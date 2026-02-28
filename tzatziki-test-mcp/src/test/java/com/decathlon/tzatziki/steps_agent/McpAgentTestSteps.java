package com.decathlon.tzatziki.steps_agent;

import com.decathlon.tzatziki.utils.LlmClient;
import io.cucumber.java.Before;
import lombok.extern.slf4j.Slf4j;

import static com.decathlon.tzatziki.utils.HttpWiremockUtils.url;

@Slf4j
public class McpAgentTestSteps {

    @Before(order = -1)
    public void before() {
        LlmClient.setBaseUrl(url());
        LlmClient.setApiKey("test-api-key");
        log.info("LLM client configured to use mock server at {}", url());
    }
}
