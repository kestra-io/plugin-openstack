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

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Restore an OpenStack server from a snapshot",
    description = """
        Rebuilds an existing OpenStack Nova compute server with a specific snapshot image from Glance. Equivalent to VMware `RestoreVmFromSnapshot`.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Restore a server from a snapshot image",
            full = true,
            code = """
                id: restore_server
                namespace: company.team

                tasks:
                  - id: restore
                    type: io.kestra.plugin.openstack.compute.RestoreServerFromSnapshot
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                    imageId: "da3052bf-1a52-472e-8c38-ceb5c1fb9811"
                """
        )
    }
)
public class RestoreServerFromSnapshot extends AbstractComputeTask implements RunnableTask<RestoreServerFromSnapshot.Output> {

    @NotNull
    @Schema(
        title = "Server UUID",
        description = """
            The unique identifier of the server to rebuild.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> serverId;

    @NotNull
    @Schema(
        title = "Snapshot image UUID",
        description = """
            The UUID of the Glance snapshot image to rebuild from.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> imageId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rServerId = runContext.render(this.serverId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`serverId` is required for RestoreServerFromSnapshot"));
        var rImageId = runContext.render(this.imageId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`imageId` is required for RestoreServerFromSnapshot"));

        logger.info("Restoring OpenStack server '{}' from snapshot image '{}'...", rServerId, rImageId);

        try (var client = this.createClient(runContext)) {
            var resolvedImageId = client.resolveImageId(rImageId);
            client.restoreServer(rServerId, resolvedImageId);
            logger.info("Successfully dispatched rebuild action for server '{}'.", rServerId);

            return Output.builder()
                .serverId(rServerId)
                .imageId(resolvedImageId)
                .build();
        }
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Server UUID",
            description = """
                The UUID of the rebuilt server.
                """
        )
        private final String serverId;

        @Schema(
            title = "Restored snapshot image UUID",
            description = """
                The Glance image UUID used to rebuild the instance.
                """
        )
        private final String imageId;
    }
}
