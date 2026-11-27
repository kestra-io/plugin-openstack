package io.kestra.plugin.openstack.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.List;

public class InstanceActionResponses {

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstanceAction {
        @Schema(
            title = "Action Name",
            description = """
                The name of the action performed (e.g. create, start, stop, reboot).
                """
        )
        @JsonProperty("action")
        private String action;

        @Schema(
            title = "Instance UUID",
            description = """
                The UUID of the instance on which the action occurred.
                """
        )
        @JsonProperty("instance_uuid")
        private String instanceUuid;

        @Schema(
            title = "Request ID",
            description = """
                The unique request ID identifying this action execution.
                """
        )
        @JsonProperty("request_id")
        private String requestId;

        @Schema(
            title = "User ID",
            description = """
                The ID of the user who initiated the action.
                """
        )
        @JsonProperty("user_id")
        private String userId;

        @Schema(
            title = "Project ID",
            description = """
                The ID of the project.
                """
        )
        @JsonProperty("project_id")
        private String projectId;

        @Schema(
            title = "Start Time",
            description = """
                ISO 8601 timestamp when the action started.
                """
        )
        @JsonProperty("start_time")
        private String startTime;

        @Schema(
            title = "Action Message",
            description = """
                Optional message or error description associated with the action.
                """
        )
        @JsonProperty("message")
        private String message;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MultipleActions {
        @JsonProperty("instanceActions")
        private List<InstanceAction> instanceActions;
    }
}
