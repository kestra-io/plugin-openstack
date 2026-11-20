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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create an OpenStack Nova compute server",
    description = """
        Provisions a new virtual server instance in OpenStack Nova using the specified flavor, image, and network configurations.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Create an OpenStack virtual server",
            full = true,
            code = """
                id: create_openstack_server
                namespace: company.team

                tasks:
                  - id: create_server
                    type: io.kestra.plugin.openstack.compute.CreateServer
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    name: "kestra-worker-01"
                    flavorRef: "m1.medium"
                    imageRef: "ubuntu-22.04"
                    networks:
                      - uuid: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                    keyName: "deployer-key"
                """
        )
    }
)
public class CreateServer extends AbstractComputeTask implements RunnableTask<CreateServer.Output> {

    @NotNull
    @Schema(
        title = "Server display name",
        description = """
            The human-readable name of the server to create.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> name;

    @NotNull
    @Schema(
        title = "Flavor reference or ID",
        description = """
            The ID or name of the flavor specifying compute vCPU, RAM, and disk size.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> flavorRef;

    @Schema(
        title = "Image reference or ID",
        description = """
            The UUID or name of the Glance operating system image to boot from.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> imageRef;

    @Schema(
        title = "Network attachments",
        description = """
            List of network definitions (such as network UUID or port UUID) to connect to the server interface.
            """
    )
    @PluginProperty(group = "main")
    private Property<List<NetworkAttachment>> networks;

    @Schema(
        title = "SSH Keypair name",
        description = """
            The name of an existing OpenStack keypair to inject into the server for SSH authentication.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> keyName;

    @Schema(
        title = "User data / cloud-init script",
        description = """
            Cloud-init configuration script or shell script to execute on boot. Will be automatically base64-encoded if plain text.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> userData;

    @Schema(
        title = "Server metadata",
        description = """
            Key-value metadata pairs to attach to the server.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<Map<String, String>> metadata;

    @Schema(
        title = "Admin password",
        description = """
            Optional root/administrator password to assign to the instance on creation.
            """
    )
    @PluginProperty(group = "advanced", secret = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Property<String> adminPass;

    @Schema(
        title = "Server description",
        description = """
            Extended text description of the server instance.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<String> serverDescription;

    @Schema(
        title = "Server tags",
        description = """
            List of tags to assign to the server.
            """
    )
    @PluginProperty(group = "advanced")
    private Property<List<String>> tags;

    @Schema(
        title = "Wait until server is active",
        description = """
            Whether to wait for the server to transition from `BUILD` to `ACTIVE` status. Defaults to `true`.
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<Boolean> waitUntilComplete = Property.ofValue(true);

    @Schema(
        title = "Timeout duration",
        description = """
            Maximum duration to wait for the server to reach `ACTIVE` status when `waitUntilComplete` is true.
            Must be a positive duration up to 6 hours. Defaults to 10 minutes (`PT10M`).
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<Duration> timeout = Property.ofValue(Duration.ofMinutes(10));

    @Override
    public Output run(RunContext runContext) throws Exception {
        var logger = runContext.logger();

        var rName = runContext.render(this.name).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`name` is required for CreateServer"));
        var rFlavorRef = runContext.render(this.flavorRef).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`flavorRef` is required for CreateServer"));
        var rImageRef = runContext.render(this.imageRef).as(String.class).orElse(null);
        var rKeyName = runContext.render(this.keyName).as(String.class).orElse(null);
        var rUserData = runContext.render(this.userData).as(String.class).orElse(null);
        var rAdminPass = runContext.render(this.adminPass).as(String.class).orElse(null);
        var rDescription = runContext.render(this.serverDescription).as(String.class).orElse(null);
        var rMetadata = runContext.render(this.metadata).asMap(String.class, String.class);
        var rTags = runContext.render(this.tags).asList(String.class);
        var rWait = runContext.render(this.waitUntilComplete).as(Boolean.class).orElse(true);
        var rTimeout = validateTimeout(runContext.render(this.timeout).as(Duration.class).orElse(DEFAULT_TIMEOUT));

        try (var client = this.createClient(runContext)) {
            var resolvedFlavorId = client.resolveFlavorId(rFlavorRef);
            var resolvedImageId = client.resolveImageId(rImageRef);

            String encodedUserData = null;
            if (rUserData != null && !rUserData.isBlank()) {
                var trimmed = rUserData.trim();
                var isAlreadyBase64 = false;
                try {
                    var decoded = Base64.getDecoder().decode(trimmed);
                    isAlreadyBase64 = Base64.getEncoder().encodeToString(decoded).equals(trimmed.replaceAll("\\s+", ""));
                } catch (Exception ignored) {
                }
                if (isAlreadyBase64) {
                    encodedUserData = trimmed;
                } else {
                    encodedUserData = Base64.getEncoder().encodeToString(rUserData.getBytes(StandardCharsets.UTF_8));
                }
            }

            List<ServerPayloads.NetworkRef> networkRefs = null;
            var rNetworks = runContext.render(this.networks).asList(NetworkAttachment.class);
            if (rNetworks != null && !rNetworks.isEmpty()) {
                networkRefs = new ArrayList<>();
                for (var net : rNetworks) {
                    var resolvedNetId = client.resolveNetworkId(net.getUuid());
                    networkRefs.add(ServerPayloads.NetworkRef.builder()
                        .uuid(resolvedNetId)
                        .port(net.getPort())
                        .fixedIp(net.getFixedIp())
                        .tag(net.getTag())
                        .build());
                }
            }

            Object networksPayload = (networkRefs != null && !networkRefs.isEmpty()) ? networkRefs : "auto";

            var serverData = ServerPayloads.ServerData.builder()
                .name(rName)
                .flavorRef(resolvedFlavorId)
                .imageRef(resolvedImageId)
                .networks(networksPayload)
                .keyName(rKeyName)
                .userData(encodedUserData)
                .metadata(rMetadata != null && !rMetadata.isEmpty() ? rMetadata : null)
                .adminPass(rAdminPass)
                .description(rDescription)
                .tags(rTags != null && !rTags.isEmpty() ? rTags : null)
                .build();

            var payload = ServerPayloads.Create.builder()
                .server(serverData)
                .build();

            logger.info("Creating OpenStack compute server '{}' with flavor '{}'...", rName, resolvedFlavorId);
            var server = client.createServer(payload);
            logger.info("Created server: id='{}', name='{}', status='{}'", server.getId(), server.getName(), server.getStatus());

            if (Boolean.TRUE.equals(rWait) && !"ACTIVE".equalsIgnoreCase(server.getStatus())) {
                logger.info("Waiting for server '{}' to become ACTIVE (timeout: {})...", server.getId(), rTimeout);
                server = client.waitForServerStatus(server.getId(), "ACTIVE", rTimeout);
                logger.info("Server '{}' is now ACTIVE.", server.getId());
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
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NetworkAttachment {
        @Schema(
            title = "Network UUID or Name",
            description = """
                The UUID or name of the Neutron network to connect.
                """
        )
        private String uuid;

        @Schema(
            title = "Port UUID",
            description = """
                The UUID of an existing Neutron port to attach.
                """
        )
        private String port;

        @Schema(
            title = "Fixed IP",
            description = """
                The fixed IPv4 or IPv6 address to assign to the network interface.
                """
        )
        private String fixedIp;

        @Schema(
            title = "Device Tag",
            description = """
                An arbitrary tag to identify the network device within guest metadata.
                """
        )
        private String tag;
    }

    @Getter
    @Builder
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(
            title = "Server UUID",
            description = """
                The unique identifier of the created server.
                """
        )
        private final String serverId;

        @Schema(
            title = "Server status",
            description = """
                The status of the server (e.g. `BUILD` or `ACTIVE`).
                """
        )
        private final String status;
        @Schema(
            title = "IP addresses",
            description = """
                Network IP addresses assigned to the created server.
                """
        )
        private final Map<String, List<Server.Address>> addresses;
    }
}
