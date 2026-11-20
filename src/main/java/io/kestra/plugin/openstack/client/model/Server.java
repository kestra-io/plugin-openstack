package io.kestra.plugin.openstack.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Server {

    @Schema(
        title = "Server UUID",
        description = """
            The unique identifier of the Nova server instance.
            """
    )
    @JsonProperty("id")
    private String id;

    @Schema(
        title = "Server Name",
        description = """
            The human-readable name of the server.
            """
    )
    @JsonProperty("name")
    private String name;

    @Schema(
        title = "Server Status",
        description = """
            Current execution status of the server (e.g. ACTIVE, SHUTOFF, ERROR, BUILD).
            """
    )
    @JsonProperty("status")
    private String status;

    @Schema(
        title = "Creation Timestamp",
        description = """
            ISO 8601 date and time when the server was created.
            """
    )
    @JsonProperty("created")
    private String created;

    @Schema(
        title = "Update Timestamp",
        description = """
            ISO 8601 date and time when the server was last modified.
            """
    )
    @JsonProperty("updated")
    private String updated;

    @Schema(
        title = "IP Addresses",
        description = """
            Map of network names to their associated IPv4/IPv6 addresses.
            """
    )
    @JsonProperty("addresses")
    private Map<String, List<Address>> addresses;

    @Schema(
        title = "Flavor Details",
        description = """
            Hardware configuration / flavor reference of the server.
            """
    )
    @JsonProperty("flavor")
    private Map<String, Object> flavor;

    @Schema(
        title = "Image Details",
        description = """
            Operating system image reference of the server.
            """
    )
    @JsonProperty("image")
    private Map<String, Object> image;

    @Schema(
        title = "Metadata",
        description = """
            Key-value metadata pairs attached to the server.
            """
    )
    @JsonProperty("metadata")
    private Map<String, String> metadata;

    @Schema(
        title = "SSH Key Name",
        description = """
            Name of the SSH keypair injected into the server instance.
            """
    )
    @JsonProperty("key_name")
    private String keyName;

    @Schema(
        title = "Server Description",
        description = """
            Free-form description text for the server instance.
            """
    )
    @JsonProperty("description")
    private String description;

    @Schema(
        title = "Server Tags",
        description = """
            List of tags associated with the server.
            """
    )
    @JsonProperty("tags")
    private List<String> tags;

    @Schema(
        title = "Power State",
        description = """
            Power state of the instance (e.g. 1 for Running, 4 for Shutdown).
            """
    )
    @JsonProperty("power_state")
    private Integer powerState;

    @Schema(
        title = "VM State",
        description = """
            Underlying hypervisor state of the virtual machine (e.g. active, stopped).
            """
    )
    @JsonProperty("OS-EXT-STS:vm_state")
    private String vmState;

    @Schema(
        title = "Task State",
        description = """
            Current transient task state (e.g. rebooting, powering-off, resizing).
            """
    )
    @JsonProperty("OS-EXT-STS:task_state")
    private String taskState;

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Address {
        @Schema(
            title = "IP Address",
            description = """
                The assigned fixed or floating IP address string.
                """
        )
        @JsonProperty("addr")
        private String addr;

        @Schema(
            title = "IP Version",
            description = """
                Internet Protocol version (4 or 6).
                """
        )
        @JsonProperty("version")
        private Integer version;

        @Schema(
            title = "Address Type",
            description = """
                Address assignment type (fixed or floating).
                """
        )
        @JsonProperty("OS-EXT-IPS:type")
        private String type;

        @Schema(
            title = "MAC Address",
            description = """
                Physical MAC address of the network interface.
                """
        )
        @JsonProperty("OS-EXT-IPS-MAC:mac_addr")
        private String macAddr;
    }
}
