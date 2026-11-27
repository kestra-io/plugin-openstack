package io.kestra.plugin.openstack.compute;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create an OpenStack server snapshot",
    description = """
        Creates a point-in-time snapshot image of an OpenStack server via the Nova `createImage` action, stored in Glance.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Create a server snapshot before patching",
            full = true,
            code = """
                id: create_snapshot
                namespace: company.team

                tasks:
                  - id: snapshot_server
                    type: io.kestra.plugin.openstack.compute.CreateServerSnapshot
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                    name: "pre-patch-backup-20261116"
                """
        )
    }
)
public class CreateServerSnapshot extends AbstractComputeTask implements RunnableTask<CreateServerSnapshot.Output> {

    @NotNull
    @Schema(
        title = "Server UUID",
        description = """
            The unique identifier of the server to snapshot.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> serverId;

    @NotNull
    @Schema(
        title = "Snapshot image name",
        description = """
            The display name for the new Glance snapshot image.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(
        title = "Snapshot metadata",
        description = """
            Optional key-value metadata to attach to the snapshot image in Glance.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<Map<String, String>> metadata;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rServerId = runContext.render(this.serverId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`serverId` is required for CreateServerSnapshot"));
        var rName = runContext.render(this.name).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`name` is required for CreateServerSnapshot"));
        var rMetadata = runContext.render(this.metadata).asMap(String.class, String.class);

        logger.info("Creating snapshot '{}' for OpenStack server '{}'...", rName, rServerId);

        try (var client = this.createClient(runContext)) {
            var imageId = client.createSnapshot(rServerId, rName, rMetadata);
            logger.info("Snapshot action dispatched. Returned image UUID: '{}'", imageId);

            return Output.builder()
                .serverId(rServerId)
                .name(rName)
                .imageId(imageId)
                .build();
        }
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Server UUID",
            description = """
                The UUID of the server that was snapshotted.
                """
        )
        private final String serverId;

        @Schema(
            title = "Snapshot name",
            description = """
                The name assigned to the snapshot image.
                """
        )
        private final String name;

        @Schema(
            title = "Glance image UUID",
            description = """
                The unique identifier of the generated snapshot image in Glance.
                """
        )
        private final String imageId;
    }
}
