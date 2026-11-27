package io.kestra.plugin.openstack.compute;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.openstack.client.model.GlanceResponses;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List snapshots of an OpenStack server",
    description = """
        Queries Glance image service for snapshot images originating from the specified OpenStack Nova compute server.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "List snapshots for a server",
            full = true,
            code = """
                id: list_snapshots
                namespace: company.team

                tasks:
                  - id: list_server_snapshots
                    type: io.kestra.plugin.openstack.compute.ListServerSnapshots
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                """
        )
    }
)
public class ListServerSnapshots extends AbstractComputeTask implements RunnableTask<ListServerSnapshots.Output> {

    @NotNull
    @Schema(
        title = "Server UUID",
        description = """
            The unique identifier of the server whose snapshots to list.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> serverId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rServerId = runContext.render(this.serverId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`serverId` is required for ListServerSnapshots"));

        logger.info("Listing snapshot images for OpenStack server '{}'...", rServerId);

        try (var client = this.createClient(runContext)) {
            var snapshots = client.listServerSnapshots(rServerId);
            logger.info("Found {} snapshot(s) for server '{}'.", snapshots.size(), rServerId);

            return Output.builder()
                .serverId(rServerId)
                .snapshots(snapshots)
                .size(snapshots.size())
                .build();
        }
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Server UUID",
            description = """
                The UUID of the queried server.
                """
        )
        private final String serverId;

        @Schema(
            title = "Snapshot images",
            description = """
                List of Glance snapshot images associated with the server.
                """
        )
        private final List<GlanceResponses.GlanceImage> snapshots;

        @Schema(
            title = "Snapshot count",
            description = """
                The number of snapshots returned.
                """
        )
        private final int size;
    }
}
