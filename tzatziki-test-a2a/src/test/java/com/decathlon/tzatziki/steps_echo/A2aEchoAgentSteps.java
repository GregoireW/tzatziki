package com.decathlon.tzatziki.steps_echo;

import com.decathlon.tzatziki.a2a.server.A2aTestServer;
import com.decathlon.tzatziki.config.A2aClientConfiguration;
import io.cucumber.java.Before;
import io.cucumber.spring.CucumberContextConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = A2aTestServer.class)
@Slf4j
public class A2aEchoAgentSteps {

    @LocalServerPort
    private Integer serverPort;

    @Before(order = -1)
    public void before() {
        A2aClientConfiguration.setBaseUrl("http://localhost:" + serverPort);
    }
}
