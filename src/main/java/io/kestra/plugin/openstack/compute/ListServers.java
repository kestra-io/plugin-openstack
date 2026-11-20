package io.kestra.plugin.openstack.compute;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.FileSerde;
import io.kestra.plugin.openstack.client.SafeRegex;
import io.kestra.plugin.openstack.client.model.Server;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List OpenStack compute servers",
    description = """
        Queries OpenStack Nova for detailed server instances, supporting marker pagination and FETCH / FETCH_ONE / STORE output modes.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List and fetch all active servers matching a regular expression",
            full = true,
            code = """
                id: list_active_openstack_servers
                namespace: company.openstack

                tasks:
                  - id: list_servers
                    type: io.kestra.plugin.openstack.compute.ListServers
                    authUrl: "https://keystone.example.com:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CRED_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CRED_SECRET') }}"
                    status: "ACTIVE"
                    nameRegex: "^prod-.*"
                    fetchType: FETCH
                """
        ),
        @Example(
            title = "Stream servers to Kestra internal storage in ION format",
            full = true,
            code = """
                id: store_large_openstack_inventory
                namespace: company.openstack

                tasks:
                  - id: store_servers
                    type: io.kestra.plugin.openstack.compute.ListServers
                    authUrl: "https://keystone.example.com:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CRED_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CRED_SECRET') }}"
                    fetchType: STORE
                    pageSize: 500
                """
        )
    }
)
public class ListServers extends AbstractComputeTask implements RunnableTask<ListServers.Output> {

    @Schema(
        title = "Output fetch type",
        description = """
            Determines how results are processed and returned:
            - `FETCH`: collects servers into a list in the task output.
            - `FETCH_ONE`: returns the single first matching server.
            - `STORE`: streams all matching servers directly to Kestra internal storage in ION format without buffering in heap.
            Defaults to `FETCH`.
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Schema(
        title = "Server status filter",
        description = """
            Filter servers by power/lifecycle status (e.g. `ACTIVE`, `SHUTOFF`, `PAUSED`, `ERROR`).
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> status;

    @Schema(
        title = "Exact server name filter",
        description = """
            Filter servers by exact name matching.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> name;

    @Schema(
        title = "Server name regex filter",
        description = """
            Client-side regex filter applied against server names before returning results.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> nameRegex;

    @Schema(
        title = "Changes since filter",
        description = """
            ISO 8601 timestamp string (e.g. `2026-11-16T00:00:00Z`). Only servers updated since this time will be returned.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> changesSince;

    @Schema(
        title = "Maximum number of servers to return",
        description = """
            Limits the maximum number of servers returned when `fetchType` is `FETCH` or `FETCH_ONE`.
            Must be between 1 and 1000. Defaults to 100. Does not apply to `STORE` mode.
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<@Min(1) @Max(1000) Integer> limit = Property.ofValue(100);

    @Schema(
        title = "Page size for pagination",
        description = """
            Number of servers to request per page from Nova. Must be between 1 and 1000. Defaults to 100.
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<@Min(1) @Max(1000) Integer> pageSize = Property.ofValue(100);

    @Schema(
        title = "Pagination marker",
        description = """
            The ID of the last server on the previous page for cursor pagination.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> marker;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);
        var rStatus = runContext.render(this.status).as(String.class).orElse(null);
        var rName = runContext.render(this.name).as(String.class).orElse(null);
        var rNameRegex = runContext.render(this.nameRegex).as(String.class).orElse(null);
        var rChangesSince = runContext.render(this.changesSince).as(String.class).orElse(null);
        var rLimit = runContext.render(this.limit).as(Integer.class).orElse(100);
        var rPageSize = runContext.render(this.pageSize).as(Integer.class).orElse(100);
        var rMarker = runContext.render(this.marker).as(String.class).orElse(null);

        var queryParams = new HashMap<String, String>();
        if (rStatus != null) {
            queryParams.put("status", rStatus);
        }
        if (rName != null) {
            queryParams.put("name", rName);
        }
        if (rChangesSince != null) {
            queryParams.put("changes-since", rChangesSince);
        }

        var pattern = (rNameRegex != null && !rNameRegex.isBlank()) ? SafeRegex.compile(rNameRegex) : null;
        var currentMarker = rMarker;
        try (var client = this.createClient(runContext)) {
            if (rFetchType == FetchType.STORE) {
                var tempFile = runContext.workingDir().createTempFile(".ion").toFile();
                var count = 0;
                try (var outputStream = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                    while (true) {
                        var pageParams = new HashMap<>(queryParams);
                        pageParams.put("limit", String.valueOf(rPageSize));
                        if (currentMarker != null) {
                            pageParams.put("marker", currentMarker);
                        }

                        var batch = client.listServers(pageParams);
                        if (batch == null || batch.isEmpty()) {
                            break;
                        }

                        for (var s : batch) {
                            if (pattern == null || (s.getName() != null && SafeRegex.matches(pattern, s.getName()))) {
                                FileSerde.write(outputStream, s);
                                count++;
                            }
                        }

                        if (batch.size() < rPageSize) {
                            break;
                        }

                        currentMarker = batch.getLast().getId();
                    }
                }
                var uri = runContext.storage().putFile(tempFile);
                logger.info("Streamed {} OpenStack compute server(s) to internal storage URI '{}'.", count, uri);
                return Output.builder().size(count).uri(uri).build();
            }

            var pageSize = Math.min(rLimit, rPageSize);
            var matchingServers = new ArrayList<Server>();
            while (true) {
                var pageParams = new HashMap<>(queryParams);
                pageParams.put("limit", String.valueOf(pageSize));
                if (currentMarker != null) {
                    pageParams.put("marker", currentMarker);
                }

                var batch = client.listServers(pageParams);
                if (batch == null || batch.isEmpty()) {
                    break;
                }

                for (var s : batch) {
                    if (pattern == null || (s.getName() != null && SafeRegex.matches(pattern, s.getName()))) {
                        matchingServers.add(s);
                        if (rFetchType == FetchType.FETCH_ONE) {
                            logger.info("Found single server '{}' for FETCH_ONE mode.", s.getId());
                            return Output.builder().size(1).server(s).build();
                        }
                        if (matchingServers.size() >= rLimit) {
                            break;
                        }
                    }
                }

                if (matchingServers.size() >= rLimit || batch.size() < pageSize) {
                    break;
                }

                currentMarker = batch.getLast().getId();
            }

            logger.info("Found {} OpenStack compute server(s). FetchType: {}", matchingServers.size(), rFetchType);

            if (rFetchType == FetchType.FETCH_ONE) {
                var single = matchingServers.isEmpty() ? null : matchingServers.getFirst();
                return Output.builder().size(matchingServers.size()).server(single).build();
            }

            return Output.builder().size(matchingServers.size()).servers(matchingServers).build();
        }
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "List of servers",
            description = """
                The servers retrieved from OpenStack Nova (populated when `fetchType: FETCH`).
                """
        )
        private final List<Server> servers;

        @Schema(
            title = "Single server record",
            description = """
                The first matching server (populated when `fetchType: FETCH_ONE`).
                """
        )
        private final Server server;

        @Schema(
            title = "Internal storage URI",
            description = """
                URI of the stored ION file in Kestra internal storage (populated when `fetchType: STORE`).
                """
        )
        private final URI uri;

        @Schema(
            title = "Total servers count",
            description = """
                The total number of servers retrieved or written.
                """
        )
        private final int size;
    }
}
