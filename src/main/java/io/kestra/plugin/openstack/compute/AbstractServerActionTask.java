package io.kestra.plugin.openstack.compute;

import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.openstack.client.OpenStackClient;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractServerActionTask extends AbstractComputeTask implements RunnableTask<VoidOutput> {

    @NotNull
    @Schema(
        title = "Server UUID",
        description = """
            The unique identifier of the target OpenStack Nova server.
            """
    )
    @PluginProperty(group = "main")
    protected Property<String> serverId;

    @Schema(
        title = "Wait until action completes",
        description = """
            Whether to wait for the server to transition to the expected lifecycle status after the action.
            Defaults to `false`.
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    protected Property<Boolean> waitUntilComplete = Property.ofValue(false);

    @Schema(
        title = "Timeout for lifecycle status transition",
        description = """
            Maximum time to wait for the server status transition when `waitUntilComplete` is true.
            Must be a positive duration up to 6 hours. Defaults to 10 minutes (`PT10M`).
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    protected Property<Duration> timeout = Property.ofValue(Duration.ofMinutes(10));

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rServerId = runContext.render(this.serverId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`serverId` is required"));
        var rWait = runContext.render(this.waitUntilComplete).as(Boolean.class).orElse(false);
        var rTimeout = validateTimeout(runContext.render(this.timeout).as(Duration.class).orElse(DEFAULT_TIMEOUT));

        logger.info("Executing {} on OpenStack server '{}'...", actionName(), rServerId);

        try (var client = this.createClient(runContext)) {
            executeAction(client, rServerId, runContext);
            logger.info("Successfully executed {} for server '{}'.", actionName(), rServerId);

            if (Boolean.TRUE.equals(rWait)) {
                waitForCompletion(client, rServerId, rTimeout, runContext);
            }

            return null;
        }
    }

    protected abstract String actionName();

    protected abstract void executeAction(OpenStackClient client, String serverId, RunContext runContext) throws Exception;

    protected String expectedStatus() {
        return null;
    }

    protected void waitForCompletion(OpenStackClient client, String serverId, Duration timeout, RunContext runContext) throws Exception {
        if (expectedStatus() != null) {
            runContext.logger().info("Waiting for server '{}' to reach status '{}'...", serverId, expectedStatus());
            client.waitForServerStatus(serverId, expectedStatus(), timeout);
        }
    }
}
