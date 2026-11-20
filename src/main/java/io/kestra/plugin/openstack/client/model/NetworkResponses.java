package io.kestra.plugin.openstack.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

public class NetworkResponses {

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Network {
        @Schema(
            title = "Network UUID",
            description = """
                The unique identifier of the network.
                """
        )
        @JsonProperty("id")
        private String id;

        @Schema(
            title = "Network Name",
            description = """
                The human-readable name of the network.
                """
        )
        @JsonProperty("name")
        private String name;

        @Schema(
            title = "Network Status",
            description = """
                The status of the network (e.g. ACTIVE).
                """
        )
        @JsonProperty("status")
        private String status;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MultipleNetworks {
        @JsonProperty("networks")
        private List<Network> networks;
    }
}
