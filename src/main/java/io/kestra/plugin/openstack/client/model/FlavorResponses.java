package io.kestra.plugin.openstack.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

public class FlavorResponses {

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Flavor {
        @Schema(
            title = "Flavor ID",
            description = """
                The unique identifier of the flavor.
                """
        )
        @JsonProperty("id")
        private String id;

        @Schema(
            title = "Flavor Name",
            description = """
                The human-readable name of the flavor (e.g. m1.small).
                """
        )
        @JsonProperty("name")
        private String name;

        @Schema(
            title = "Virtual CPUs",
            description = """
                Number of vCPUs allocated.
                """
        )
        @JsonProperty("vcpus")
        private Integer vcpus;

        @Schema(
            title = "RAM (MB)",
            description = """
                Memory in megabytes.
                """
        )
        @JsonProperty("ram")
        private Integer ram;

        @Schema(
            title = "Root Disk (GB)",
            description = """
                Root disk size in gigabytes.
                """
        )
        @JsonProperty("disk")
        private Integer disk;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SingleFlavor {
        @JsonProperty("flavor")
        private Flavor flavor;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MultipleFlavors {
        @JsonProperty("flavors")
        private List<Flavor> flavors;
    }
}
