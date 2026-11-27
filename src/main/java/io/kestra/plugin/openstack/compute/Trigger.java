package io.kestra.plugin.openstack.compute;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.storages.kv.KVMetadata;
import io.kestra.core.storages.kv.KVValueAndMetadata;
import io.kestra.plugin.openstack.client.SafeRegex;
import io.kestra.plugin.openstack.client.model.EndpointType;
import io.kestra.plugin.openstack.client.model.EventType;
import io.kestra.plugin.openstack.client.model.InstanceActionResponses;
import io.kestra.plugin.openstack.client.model.Server;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(title = "React to OpenStack server state changes", description = """
        Periodically polls OpenStack Nova compute servers for state changes using KV Store watermarking and event filtering, dispatching execution with compact event payloads.
        """)
@Plugin(examples = {
        @Example(title = "Trigger a flow whenever an active OpenStack worker shuts down or stops", full = true, code = """
                id: handle_server_stop_events
                namespace: company.openstack

                triggers:
                  - id: watch_server_stops
                    type: io.kestra.plugin.openstack.compute.Trigger
                    authUrl: "https://keystone.example.com:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CRED_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CRED_SECRET') }}"
                    eventType: STOP
                    serverNameRegex: "^kestra-worker-.*"
                    interval: PT1M

                tasks:
                  - id: log_event
                    type: io.kestra.plugin.core.log.Log
                    message: "Detected {{ trigger.size }} OpenStack server event(s): {{ trigger.events }}"
                """)
})
public class Trigger extends AbstractTrigger
        implements PollingTriggerInterface, TriggerOutput<Trigger.Output>, OpenStackConnectionInterface {

    @NotNull
    @Schema(title = "Keystone Auth URL", description = """
            Identity v3 auth URL, e.g. `https://keystone.example.com:5000/v3`.
            """)
    @PluginProperty(group = "connection")
    private Property<String> authUrl;

    @Schema(title = "Application Credential ID", description = """
            The OpenStack Keystone Application Credential ID for authentication.
            """)
    @PluginProperty(group = "connection")
    private Property<String> applicationCredentialId;

    @Schema(title = "Application Credential Secret", description = """
            The OpenStack Keystone Application Credential Secret for authentication.
            """)
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Property<String> applicationCredentialSecret;

    @Schema(title = "OpenStack username", description = """
            The username for OpenStack password-based authentication.
            """)
    @PluginProperty(group = "connection")
    private Property<String> username;

    @Schema(title = "OpenStack password", description = """
            The password for OpenStack password-based authentication.
            """)
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Property<String> password;

    @Schema(title = "Target project name", description = """
            The OpenStack project/tenant name to scope authentication to when using password credentials.
            """)
    @PluginProperty(group = "connection")
    private Property<String> projectName;

    @Schema(title = "User domain name", description = """
            The domain name for the user when using password credentials. Defaults to `Default`.
            """)
    @PluginProperty(group = "connection")
    @Builder.Default
    private Property<String> userDomainName = Property.ofValue("Default");

    @Schema(title = "Project domain name", description = """
            The domain name for the project when using password credentials. Defaults to `Default`.
            """)
    @PluginProperty(group = "connection")
    @Builder.Default
    private Property<String> projectDomainName = Property.ofValue("Default");

    @Schema(title = "Region name", description = """
            The OpenStack region to target in the service catalog (e.g. `RegionOne`).
            """)
    @PluginProperty(group = "connection")
    private Property<String> region;

    @Schema(title = "Endpoint interface type", description = """
            The endpoint interface type to resolve from the service catalog: `PUBLIC`, `INTERNAL`, or `ADMIN`. Defaults to `PUBLIC`.
            """)
    @PluginProperty(group = "connection")
    @Builder.Default
    private Property<EndpointType> endpointType = Property.ofValue(EndpointType.PUBLIC);

    @Schema(title = "HTTP client options", description = """
            Advanced HTTP client configuration such as timeouts and proxy settings.
            """)
    @PluginProperty(group = "connection")
    private HttpConfiguration options;

    @Schema(title = "Interval between evaluations", description = """
            The duration between each poll evaluation. Defaults to 1 minute (`PT1M`).
            """)
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Duration interval = Duration.ofMinutes(1);

    @Schema(title = "Server status filter", description = """
            Optional server power state filter (`ACTIVE`, `SHUTOFF`, `PAUSED`, `ERROR`).
            """)
    @PluginProperty(group = "main")
    private Property<String> status;

    @Schema(title = "Server name regex filter", description = """
            Client-side regex filter applied against server names before triggering.
            """)
    @PluginProperty(group = "main")
    private Property<String> serverNameRegex;

    @Schema(title = "Event type filter", description = """
            Filter for specific server lifecycle events (`ALL`, `CREATE`, `START`, `STOP`, `REBOOT`, `PAUSE`, `SUSPEND`, `RESUME`, `DELETE`, `RESIZE`, `REBUILD`). Defaults to `ALL`.
            """)
    @PluginProperty(group = "main")
    @Builder.Default
    private Property<EventType> eventType = Property.ofValue(EventType.ALL);

    @Schema(title = "Max events per poll", description = """
            Maximum number of server events to emit in a single trigger execution. Must be between 1 and 500. Defaults to 50.
            """)
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<@Min(1) @Max(500) Integer> maxEvents = Property.ofValue(50);

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext context) throws Exception {
        var runContext = conditionContext.getRunContext();
        var logger = runContext.logger();

        var rStatus = runContext.render(this.status).as(String.class).orElse(null);
        var rNameRegex = runContext.render(this.serverNameRegex).as(String.class).orElse(null);
        var rEventType = runContext.render(this.eventType).as(EventType.class).orElse(EventType.ALL);
        var rMaxEvents = runContext.render(this.maxEvents).as(Integer.class).orElse(50);

        var flowId = conditionContext.getFlow().getId();
        var triggerId = this.getId();
        var kvKey = ("wm_" + flowId.length() + "_" + flowId + "_" + triggerId.length() + "_" + triggerId)
                .replaceAll("[^a-zA-Z0-9_.-]", "_");
        var kvStore = runContext.namespaceKv(conditionContext.getFlow().getNamespace());

        String rawWatermark = null;
        try {
            var valOpt = kvStore.getValue(kvKey);
            if (valOpt.isPresent() && valOpt.get().value() != null) {
                rawWatermark = valOpt.get().value().toString();
            }
        } catch (Exception e) {
            logger.debug("No previous watermark found in KV Store for key '{}': {}", kvKey, e.getMessage());
        }

        String watermarkTimestamp = null;
        var deliveredIds = new HashSet<String>();
        if (rawWatermark != null && !rawWatermark.isBlank()) {
            var parts = rawWatermark.split("\\|", 2);
            watermarkTimestamp = parts[0];
            if (parts.length > 1 && !parts[1].isBlank()) {
                deliveredIds.addAll(Arrays.asList(parts[1].split(",")));
            }
        } else if (context.getDate() != null) {
            watermarkTimestamp = context.getDate().format(DateTimeFormatter.ISO_INSTANT);
        }

        var queryParams = new HashMap<String, String>();
        if (watermarkTimestamp != null) {
            queryParams.put("changes-since", watermarkTimestamp);
        }
        if (rStatus != null) {
            queryParams.put("status", rStatus);
        }

        try (var client = OpenStackConnectionInterface.createClient(this, runContext)) {
            var servers = client.listServers(queryParams);

            if (rNameRegex != null && !rNameRegex.isBlank()) {
                var pattern = SafeRegex.compile(rNameRegex);
                var filtered = new ArrayList<Server>();
                for (var s : servers) {
                    if (s.getName() != null && SafeRegex.matches(pattern, s.getName())) {
                        filtered.add(s);
                    }
                }
                servers = filtered;
            }

            var candidates = new ArrayList<CandidateEvent>();

            for (var srv : servers) {
                var actions = Collections.<InstanceActionResponses.InstanceAction>emptyList();
                try {
                    actions = client.listInstanceActions(srv.getId(), watermarkTimestamp);
                } catch (IllegalStateException e) {
                    if (e.getMessage() != null && e.getMessage().contains("microversion")) {
                        throw e;
                    }
                    logger.warn("Failed to fetch instance actions for server '{}': {}", srv.getId(), e.getMessage());
                } catch (Exception e) {
                    logger.warn("Failed to fetch instance actions for server '{}': {}", srv.getId(), e.getMessage());
                }

                if (!actions.isEmpty()) {
                    for (var act : actions) {
                        var eventId = act.getRequestId() != null
                                ? act.getRequestId()
                                : (srv.getId() + "_" + act.getAction() + "_" + act.getStartTime());

                        var ts = act.getStartTime() != null ? act.getStartTime() : srv.getUpdated();
                        if (rawWatermark != null) {
                            var eventInstant = parseInstantSafely(ts);
                            var watermarkInstant = parseInstantSafely(watermarkTimestamp);
                            if (watermarkInstant != null && eventInstant != null
                                    && eventInstant.isBefore(watermarkInstant)) {
                                continue;
                            }
                        }

                        if (deliveredIds.contains(eventId)) {
                            continue;
                        }

                        if (matchesEvent(act.getAction(), rEventType)) {
                            candidates.add(new CandidateEvent(
                                    srv.getId(),
                                    srv.getName(),
                                    act.getAction(),
                                    act.getRequestId(),
                                    ts,
                                    eventId));
                        }
                    }
                } else {
                    var eventId = srv.getId() + "_" + (srv.getUpdated() != null ? srv.getUpdated() : srv.getStatus());
                    if (rawWatermark != null) {
                        var eventInstant = parseInstantSafely(srv.getUpdated());
                        var watermarkInstant = parseInstantSafely(watermarkTimestamp);
                        if (watermarkInstant != null && eventInstant != null
                                && eventInstant.isBefore(watermarkInstant)) {
                            continue;
                        }
                    }

                    if (!deliveredIds.contains(eventId)) {
                        var eventName = srv.getStatus() != null ? srv.getStatus() : "UPDATE";
                        if (rEventType == EventType.ALL || matchesStatusEvent(srv.getStatus(), rEventType)) {
                            candidates.add(new CandidateEvent(
                                    srv.getId(),
                                    srv.getName(),
                                    eventName,
                                    null,
                                    srv.getUpdated(),
                                    eventId));
                        }
                    }
                }
            }

            candidates.sort(Comparator
                    .comparing((CandidateEvent c) -> parseInstantSafely(c.timestamp()),
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CandidateEvent::eventId));

            var events = new ArrayList<ServerEvent>();
            var deliveredInThisPoll = new ArrayList<CandidateEvent>();

            for (var candidate : candidates) {
                if (events.size() >= rMaxEvents) {
                    break;
                }
                events.add(ServerEvent.builder()
                        .serverId(candidate.serverId())
                        .serverName(candidate.serverName())
                        .eventType(candidate.eventType())
                        .requestId(candidate.requestId())
                        .timestamp(candidate.timestamp())
                        .build());
                deliveredInThisPoll.add(candidate);
            }

            var latestTimestamp = watermarkTimestamp;
            var newDeliveredIds = new HashSet<String>();

            if (!deliveredInThisPoll.isEmpty()) {
                var lastDelivered = deliveredInThisPoll.getLast();
                if (lastDelivered.timestamp() != null && !lastDelivered.timestamp().isBlank()) {
                    latestTimestamp = lastDelivered.timestamp();
                }

                if (watermarkTimestamp != null && isSameTimestamp(latestTimestamp, watermarkTimestamp)) {
                    newDeliveredIds.addAll(deliveredIds);
                }

                for (var d : deliveredInThisPoll) {
                    if (isSameTimestamp(d.timestamp(), latestTimestamp)) {
                        newDeliveredIds.add(d.eventId());
                    }
                }
            } else {
                var maxServerUpdated = watermarkTimestamp;
                var maxInstant = parseInstantSafely(watermarkTimestamp);
                for (var srv : servers) {
                    var srvInstant = parseInstantSafely(srv.getUpdated());
                    if (srvInstant != null && (maxInstant == null || srvInstant.isAfter(maxInstant))) {
                        maxInstant = srvInstant;
                        maxServerUpdated = srv.getUpdated();
                    }
                }
                var watermarkInstant = parseInstantSafely(watermarkTimestamp);
                if (maxInstant != null && (watermarkInstant == null || maxInstant.isAfter(watermarkInstant))) {
                    latestTimestamp = maxServerUpdated;
                } else {
                    newDeliveredIds.addAll(deliveredIds);
                }
            }

            if (latestTimestamp != null) {
                var newWatermarkValue = latestTimestamp + "|" + String.join(",", newDeliveredIds);
                if (!newWatermarkValue.equals(rawWatermark)) {
                    try {
                        kvStore.put(kvKey,
                                new KVValueAndMetadata(new KVMetadata(null, (Duration) null), newWatermarkValue));
                        logger.debug("Updated OpenStack trigger watermark to '{}'.", newWatermarkValue);
                    } catch (Exception e) {
                        logger.warn("Failed to persist watermark '{}' in KV store: {}", newWatermarkValue,
                                e.getMessage());
                    }
                }
            }

            if (events.isEmpty()) {
                return Optional.empty();
            }

            logger.info("OpenStack trigger detected {} matching server event(s).", events.size());

            var output = Output.builder()
                    .events(events)
                    .size(events.size())
                    .build();

            var execution = TriggerService.generateExecution(this, conditionContext, context, output);
            return Optional.of(execution);
        }
    }

    private boolean matchesEvent(String action, EventType filter) {
        if (filter == EventType.ALL || action == null) {
            return true;
        }
        var act = action.toLowerCase(Locale.ROOT);
        return switch (filter) {
            case CREATE -> act.contains("create");
            case START -> act.contains("start");
            case STOP -> act.contains("stop");
            case REBOOT -> act.contains("reboot");
            case PAUSE -> act.contains("pause");
            case SUSPEND -> act.contains("suspend");
            case RESUME -> act.contains("resume");
            case DELETE -> act.contains("delete");
            case RESIZE -> act.contains("resize");
            case REBUILD -> act.contains("rebuild");
            default -> true;
        };
    }

    private boolean matchesStatusEvent(String status, EventType filter) {
        if (filter == EventType.ALL || status == null) {
            return true;
        }
        var stat = status.toUpperCase(Locale.ROOT);
        return switch (filter) {
            case START, RESUME -> "ACTIVE".equals(stat);
            case STOP -> "SHUTOFF".equals(stat);
            case PAUSE -> "PAUSED".equals(stat);
            case SUSPEND -> "SUSPENDED".equals(stat);
            case RESIZE -> stat.contains("RESIZE");
            case REBUILD -> stat.contains("REBUILD");
            default -> false;
        };
    }

    private static Instant parseInstantSafely(String ts) {
        if (ts == null || ts.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(ts);
        } catch (Exception ignored) {
            try {
                var normalized = ts.trim().replace(' ', 'T');
                if (!normalized.endsWith("Z") && !normalized.contains("+")) {
                    var tIdx = normalized.indexOf('T');
                    if (tIdx == -1 || !normalized.substring(tIdx).contains("-")) {
                        normalized = normalized + "Z";
                    }
                }
                return Instant.parse(normalized);
            } catch (Exception e) {
                return null;
            }
        }
    }

    private static boolean isSameTimestamp(String ts1, String ts2) {
        if (Objects.equals(ts1, ts2)) {
            return true;
        }
        var i1 = parseInstantSafely(ts1);
        var i2 = parseInstantSafely(ts2);
        return i1 != null && i1.equals(i2);
    }

    private record CandidateEvent(
            String serverId,
            String serverName,
            String eventType,
            String requestId,
            String timestamp,
            String eventId) {
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Server events", description = """
                List of detected lifecycle events for matching compute instances.
                """)
        private final List<ServerEvent> events;

        @Schema(title = "Events count", description = """
                The number of events detected in this evaluation.
                """)
        private final int size;
    }

    @Getter
    @Builder
    @ToString
    public static class ServerEvent {
        @Schema(title = "Server UUID", description = """
                The unique identifier of the server.
                """)
        private final String serverId;

        @Schema(title = "Server name", description = """
                The name of the server.
                """)
        private final String serverName;

        @Schema(title = "Event type", description = """
                The action or status event type (e.g. `create`, `stop`, `start`, `reboot`).
                """)
        private final String eventType;

        @Schema(title = "Request ID", description = """
                The Nova request UUID associated with this instance action, if available.
                """)
        private final String requestId;

        @Schema(title = "Event timestamp", description = """
                Timestamp when the event occurred.
                """)
        private final String timestamp;
    }
}
