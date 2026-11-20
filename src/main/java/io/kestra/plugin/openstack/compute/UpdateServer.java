package io.kestra.plugin.openstack.compute;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.openstack.client.model.Server;
import io.kestra.plugin.openstack.client.model.ServerPayloads;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Update attributes of an OpenStack server",
    description = """
        Updates the name, server description, flavor resize, attached network interfaces, or metadata of an existing OpenStack Nova compute server.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Update server name and description",
            full = true,
            code = """
                id: update_openstack_server
                namespace: company.team

                tasks:
                  - id: update_server
                    type: io.kestra.plugin.openstack.compute.UpdateServer
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                    name: "kestra-worker-renamed"
                    serverDescription: "Updated by maintenance workflow"
                """
        )
    }
)
public class UpdateServer extends AbstractComputeTask implements RunnableTask<UpdateServer.Output> {

    @NotNull
    @Schema(
        title = "Server UUID",
        description = """
            The unique identifier of the server to update.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> serverId;

    @Schema(
        title = "New server name",
        description = """
            New name for the server instance.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> name;

    @Schema(
        title = "New server description",
        description = """
            New description for the server instance.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> serverDescription;

    @Schema(
        title = "Target flavor reference or ID",
        description = """
            New flavor to resize the server to.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> flavorRef;

    @Schema(
        title = "Auto-confirm resize",
        description = """
            Whether to automatically wait for `VERIFY_RESIZE` status and confirm the resize operation. Defaults to `true`.
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<Boolean> autoConfirmResize = Property.ofValue(true);

    @Schema(
        title = "Timeout for resize operations",
        description = """
            Maximum time to wait for the server to reach `VERIFY_RESIZE` and then `ACTIVE`.
            Must be a positive duration up to 6 hours. Defaults to 10 minutes (`PT10M`).
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<Duration> timeout = Property.ofValue(Duration.ofMinutes(10));

    @Schema(
        title = "Server metadata",
        description = """
            Metadata key-value pairs to set or update on the server.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<Map<String, String>> metadata;

    @Schema(
        title = "Network UUID to attach",
        description = """
            UUID or name of a network interface to attach to the server.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> networkId;

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rServerId = runContext.render(this.serverId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`serverId` is required for UpdateServer"));
        var rName = runContext.render(this.name).as(String.class).orElse(null);
        var rDescription = runContext.render(this.serverDescription).as(String.class).orElse(null);
        var rFlavorRef = runContext.render(this.flavorRef).as(String.class).orElse(null);
        var rAutoConfirm = runContext.render(this.autoConfirmResize).as(Boolean.class).orElse(true);
        var rTimeout = validateTimeout(runContext.render(this.timeout).as(Duration.class).orElse(DEFAULT_TIMEOUT));
        var rMetadata = runContext.render(this.metadata).asMap(String.class, String.class);
        var rNetworkId = runContext.render(this.networkId).as(String.class).orElse(null);

        logger.info("Updating OpenStack server '{}'...", rServerId);

        try (var client = this.createClient(runContext)) {
            Server server = null;

            if (rName != null || rDescription != null) {
                var payload = ServerPayloads.Update.builder()
                    .server(ServerPayloads.UpdateData.builder()
                        .name(rName)
                        .description(rDescription)
                        .build())
                    .build();
                server = client.updateServer(rServerId, payload);
                logger.info("Updated attributes for server '{}': name='{}'", server.getId(), server.getName());
            }

            if (rFlavorRef != null) {
                var targetFlavorId = client.resolveFlavorId(rFlavorRef);
                logger.info("Resizing server '{}' to flavor '{}'...", rServerId, targetFlavorId);
                client.resizeServer(rServerId, targetFlavorId);
                if (Boolean.TRUE.equals(rAutoConfirm)) {
                    logger.info("Waiting for server '{}' to reach VERIFY_RESIZE...", rServerId);
                    client.waitForServerStatus(rServerId, "VERIFY_RESIZE", rTimeout);
                    client.confirmResize(rServerId);
                    logger.info("Confirmed resize for server '{}'. Waiting for ACTIVE...", rServerId);
                    server = client.waitForServerStatus(rServerId, "ACTIVE", rTimeout);
                }
            }

            if (rMetadata != null && !rMetadata.isEmpty()) {
                logger.info("Updating metadata on server '{}'...", rServerId);
                client.updateServerMetadata(rServerId, rMetadata);
            }

            if (rNetworkId != null) {
                var resolvedNetId = client.resolveNetworkId(rNetworkId);
                logger.info("Attaching network interface '{}' to server '{}'...", resolvedNetId, rServerId);
                client.attachInterface(rServerId, resolvedNetId);
            }

            if (server == null) {
                server = client.getServer(rServerId);
            }

            return Output.builder()
                .serverId(server.getId())
                .status(server.getStatus())
                .addresses(server.getAddresses())
                .build();
        }
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Server UUID",
            description = """
                The unique identifier of the updated server.
                """
        )
        private final String serverId;

        @Schema(
            title = "Server status",
            description = """
                The current status of the server after the updates.
                """
        )
        private final String status;

        @Schema(
            title = "IP addresses",
            description = """
                Map of network names to IP address details after interface attachment.
                """
        )
        private final Map<String, List<Server.Address>> addresses;
    }
}
