package io.kestra.plugin.openstack.compute;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.openstack.client.model.EventType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class OpenStackTriggerTest {

  @Inject
  private RunContextFactory runContextFactory;

  private static WireMockServer wireMockServer;
  private static String authUrl;
  private static int port;

  @BeforeAll
  static void startWireMock() {
    wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMockServer.start();
    port = wireMockServer.port();
    authUrl = "http://localhost:" + port + "/v3";
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMockServer != null) {
      wireMockServer.stop();
    }
  }

  @BeforeEach
  void setupStubs() {
    wireMockServer.resetAll();

    var tokenBody = String.format("""
        {
          "token": {
            "expires_at": "2099-01-01T00:00:00.000000Z",
            "issued_at": "2026-11-16T12:00:00.000000Z",
            "catalog": [
              {
                "type": "compute",
                "name": "nova",
                "endpoints": [
                  {
                    "interface": "public",
                    "region": "RegionOne",
                    "url": "http://localhost:%d/compute/v2.1"
                  }
                ]
              }
            ]
          }
        }
        """, port);

    wireMockServer.stubFor(post(urlEqualTo("/v3/auth/tokens"))
        .willReturn(aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withHeader("X-Subject-Token", "mock-token-12345")
            .withBody(tokenBody)));
  }

  @Test
  void testTriggerFiresOnMatchingServerEvent() throws Exception {
    wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/detail"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "servers": [
                    {
                      "id": "srv-prod-01",
                      "name": "prod-app-01",
                      "status": "SHUTOFF",
                      "updated": "2026-11-17T10:00:00Z"
                    }
                  ]
                }
                """)));

    wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/srv-prod-01/os-instance-actions"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "instanceActions": [
                    {
                      "action": "stop",
                      "instance_uuid": "srv-prod-01",
                      "request_id": "req-stop-9988",
                      "start_time": "2026-11-17T10:00:00.000000"
                    }
                  ]
                }
                """)));

    var trigger = Trigger.builder()
        .id("watch_openstack_" + IdUtils.create())
        .type(Trigger.class.getName())
        .authUrl(Property.ofValue(authUrl))
        .applicationCredentialId(Property.ofValue("app-id"))
        .applicationCredentialSecret(Property.ofValue("app-secret"))
        .status(Property.ofValue("SHUTOFF"))
        .serverNameRegex(Property.ofValue("prod-.*"))
        .eventType(Property.ofValue(EventType.STOP))
        .interval(Duration.ofMinutes(1))
        .build();

    var context = TestsUtils.mockTrigger(runContextFactory, trigger);
    var executionOptional = trigger.evaluate(context.getKey(), context.getValue());

    assertTrue(executionOptional.isPresent(), "Trigger should evaluate and return an execution.");
    var execution = executionOptional.get();
    assertThat(execution.getTrigger().getVariables().get("size"), is(1));

    @SuppressWarnings("unchecked")
    var events = (List<Map<String, Object>>) execution.getTrigger().getVariables().get("events");
    assertThat(events, hasSize(1));
    assertThat(events.getFirst().get("serverId"), is("srv-prod-01"));
    assertThat(events.getFirst().get("eventType"), is("stop"));
    assertThat(events.getFirst().get("requestId"), is("req-stop-9988"));

    var secondPoll = trigger.evaluate(context.getKey(), context.getValue());
    assertTrue(secondPoll.isEmpty(), "Second consecutive poll without new events must return empty.");
  }

  @Test
  void testTriggerEmptyWhenNoServersMatch() throws Exception {
    wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/detail"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"servers\": []}")));

    var trigger = Trigger.builder()
        .id("watch_openstack_" + IdUtils.create())
        .type(Trigger.class.getName())
        .authUrl(Property.ofValue(authUrl))
        .applicationCredentialId(Property.ofValue("app-id"))
        .applicationCredentialSecret(Property.ofValue("app-secret"))
        .status(Property.ofValue("SHUTOFF"))
        .build();

    var context = TestsUtils.mockTrigger(runContextFactory, trigger);
    var executionOptional = trigger.evaluate(context.getKey(), context.getValue());
    assertTrue(executionOptional.isEmpty(), "Trigger should return empty when no servers match.");
  }

  @Test
  void testTriggerRethrowsMicroversion406Error() {
    wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/detail"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "servers": [
                    {
                      "id": "srv-prod-02",
                      "name": "prod-app-02",
                      "status": "ACTIVE",
                      "updated": "2026-11-17T10:00:00Z"
                    }
                  ]
                }
                """)));

    wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/srv-prod-02/os-instance-actions"))
        .willReturn(aResponse()
            .withStatus(406)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"computeFault\": {\"message\": \"Version 2.58 is not supported\", \"code\": 406}}")));

    var trigger = Trigger.builder()
        .id("watch_openstack_" + IdUtils.create())
        .type(Trigger.class.getName())
        .authUrl(Property.ofValue(authUrl))
        .applicationCredentialId(Property.ofValue("app-id"))
        .applicationCredentialSecret(Property.ofValue("app-secret"))
        .build();

    var context = TestsUtils.mockTrigger(runContextFactory, trigger);
    var ex = assertThrows(IllegalStateException.class, () -> trigger.evaluate(context.getKey(), context.getValue()));
    assertThat(ex.getMessage(), containsString("microversion"));
  }

  @Test
  void testTriggerNextEvaluationDate() {
    var trigger = Trigger.builder()
        .id("trigger_sched_" + IdUtils.create())
        .authUrl(Property.ofValue(authUrl))
        .interval(Duration.ofMinutes(2))
        .build();

    var next = trigger.nextEvaluationDate();
    assertNotNull(next);
    assertTrue(next.isAfter(ZonedDateTime.now().minusSeconds(1)));
  }

    @Test
    void testTriggerChronologicalOrderWithMaxEventsAndPruning() throws Exception {
        wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/detail"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "servers": [
                        {
                          "id": "srv-prod-B",
                          "name": "prod-app-B",
                          "status": "ACTIVE",
                          "updated": "2026-11-17T10:30:00Z"
                        },
                        {
                          "id": "srv-prod-A",
                          "name": "prod-app-A",
                          "status": "ACTIVE",
                          "updated": "2026-11-17T10:10:00Z"
                        }
                      ]
                    }
                    """)));

        wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/srv-prod-B/os-instance-actions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "instanceActions": [
                        {
                          "action": "start",
                          "instance_uuid": "srv-prod-B",
                          "request_id": "req-B",
                          "start_time": "2026-11-17T10:30:00.000000"
                        }
                      ]
                    }
                    """)));

        wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/srv-prod-A/os-instance-actions"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "instanceActions": [
                        {
                          "action": "start",
                          "instance_uuid": "srv-prod-A",
                          "request_id": "req-A",
                          "start_time": "2026-11-17T10:10:00.000000"
                        }
                      ]
                    }
                    """)));

        var trigger = Trigger.builder()
            .id("watch_openstack_chrono_" + IdUtils.create())
            .type(Trigger.class.getName())
            .authUrl(Property.ofValue(authUrl))
            .applicationCredentialId(Property.ofValue("app-id"))
            .applicationCredentialSecret(Property.ofValue("app-secret"))
            .eventType(Property.ofValue(EventType.START))
            .maxEvents(Property.ofValue(1))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

    var poll1Opt = trigger.evaluate(context.getKey(), context.getValue());
    assertTrue(poll1Opt.isPresent());
    @SuppressWarnings("unchecked")
    var events1 = (List<Map<String, Object>>) poll1Opt.get().getTrigger().getVariables().get("events");
    assertThat(events1, hasSize(1));
    assertThat(events1.getFirst().get("requestId"), is("req-A"));
    assertThat(events1.getFirst().get("serverId"), is("srv-prod-A"));

    var poll2Opt = trigger.evaluate(context.getKey(), context.getValue());
    assertTrue(poll2Opt.isPresent());
    @SuppressWarnings("unchecked")
    var events2 = (List<Map<String, Object>>) poll2Opt.get().getTrigger().getVariables().get("events");
    assertThat(events2, hasSize(1));
    assertThat(events2.getFirst().get("requestId"), is("req-B"));
    assertThat(events2.getFirst().get("serverId"), is("srv-prod-B"));

    var poll3Opt = trigger.evaluate(context.getKey(), context.getValue());
    assertTrue(poll3Opt.isEmpty());
  }
}
