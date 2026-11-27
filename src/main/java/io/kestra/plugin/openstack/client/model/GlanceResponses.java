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

public class GlanceResponses {

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GlanceImage {

        @Schema(
            title = "Image ID",
            description = """
                The UUID of the Glance image or snapshot.
                """
        )
        @JsonProperty("id")
        private String id;

        @Schema(
            title = "Image Name",
            description = """
                Human-readable name of the image or snapshot.
                """
        )
        @JsonProperty("name")
        private String name;

        @Schema(
            title = "Image Status",
            description = """
                Status of the image (e.g. active, queued, saving, deleted).
                """
        )
        @JsonProperty("status")
        private String status;

        @Schema(
            title = "Visibility",
            description = """
                Image access visibility (e.g. public, private, shared, community).
                """
        )
        @JsonProperty("visibility")
        private String visibility;

        @Schema(
            title = "Size in Bytes",
            description = """
                Total size of the image payload in bytes.
                """
        )
        @JsonProperty("size")
        private Long size;

        @Schema(
            title = "Disk Format",
            description = """
                Format of the disk image (e.g. qcow2, raw, iso).
                """
        )
        @JsonProperty("disk_format")
        private String diskFormat;

        @Schema(
            title = "Container Format",
            description = """
                Container format of the image (e.g. bare, ovf).
                """
        )
        @JsonProperty("container_format")
        private String containerFormat;

        @Schema(
            title = "Creation Timestamp",
            description = """
                ISO 8601 timestamp when the image was uploaded.
                """
        )
        @JsonProperty("created_at")
        private String createdAt;

        @Schema(
            title = "Update Timestamp",
            description = """
                ISO 8601 timestamp when the image was last updated.
                """
        )
        @JsonProperty("updated_at")
        private String updatedAt;

        @Schema(
            title = "Instance UUID",
            description = """
                UUID of the Nova server from which the snapshot was taken, if applicable.
                """
        )
        @JsonProperty("instance_uuid")
        private String instanceUuid;

        @Schema(
            title = "Image Type",
            description = """
                Classification of the image (e.g. snapshot, base).
                """
        )
        @JsonProperty("image_type")
        private String imageType;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MultipleImages {
        @JsonProperty("images")
        private List<GlanceImage> images;
    }
}
