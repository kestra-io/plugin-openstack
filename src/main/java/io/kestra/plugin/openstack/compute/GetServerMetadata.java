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
    title = "Get metadata of an OpenStack server",
    description = """
        Retrieves all key-value metadata items associated with an OpenStack Nova compute server. Equivalent to VMware `GetVmCustomAttributes`.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Retrieve server metadata",
            full = true,
            code = """
                id: get_server_metadata
                namespace: company.team

                tasks:
                  - id: get_metadata
                    type: io.kestra.plugin.openstack.compute.GetServerMetadata
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                """
        )
    }
)
public class GetServerMetadata extends AbstractComputeTask implements RunnableTask<GetServerMetadata.Output> {

    @NotNull
    @Schema(
        title = "Server UUID",
        description = """
            The unique identifier of the server whose metadata to fetch.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> serverId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rServerId = runContext.render(this.serverId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`serverId` is required for GetServerMetadata"));

        logger.debug("Fetching metadata for OpenStack server '{}'...", rServerId);

        try (var client = this.createClient(runContext)) {
            var metadata = client.getServerMetadata(rServerId);
            logger.info("Retrieved {} metadata key(s) for server '{}'.", metadata.size(), rServerId);

            return Output.builder()
                .serverId(rServerId)
                .metadata(metadata)
                .build();
        }
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Server UUID",
            description = """
                The UUID of the server.
                """
        )
        private final String serverId;

        @Schema(
            title = "Server metadata map",
            description = """
                Map of key-value pairs assigned to the server.
                """
        )
        private final Map<String, String> metadata;
    }
}
