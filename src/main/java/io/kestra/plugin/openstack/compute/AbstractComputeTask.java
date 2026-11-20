package io.kestra.plugin.openstack.compute;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.openstack.client.OpenStackClient;
import io.kestra.plugin.openstack.client.model.EndpointType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractComputeTask extends Task implements OpenStackConnectionInterface {

    @NotNull
    @Schema(
        title = "Keystone authentication URL",
        description = """
            The Identity service endpoint URL for authentication, e.g. `https://keystone.example.com:5000/v3`.
            """
    )
    @PluginProperty(group = "connection")
    protected Property<String> authUrl;

    @Schema(
        title = "Application Credential ID",
        description = """
            The OpenStack Keystone Application Credential ID for authentication.
            """
    )
    @PluginProperty(group = "connection")
    protected Property<String> applicationCredentialId;

    @Schema(
        title = "Application Credential Secret",
        description = """
            The OpenStack Keystone Application Credential Secret for authentication.
            """
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Property<String> applicationCredentialSecret;

    @Schema(
        title = "OpenStack username",
        description = """
            The username for OpenStack password-based authentication.
            """
    )
    @PluginProperty(group = "connection")
    protected Property<String> username;

    @Schema(
        title = "OpenStack password",
        description = """
            The password for OpenStack password-based authentication.
            """
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    protected Property<String> password;

    @Schema(
        title = "Target project name",
        description = """
            The OpenStack project/tenant name to scope authentication to when using password credentials.
            """
    )
    @PluginProperty(group = "connection")
    protected Property<String> projectName;

    @Schema(
        title = "User domain name",
        description = """
            The domain name for the user when using password credentials. Defaults to `Default`.
            """
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<String> userDomainName = Property.ofValue("Default");

    @Schema(
        title = "Project domain name",
        description = """
            The domain name for the project when using password credentials. Defaults to `Default`.
            """
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<String> projectDomainName = Property.ofValue("Default");

    @Schema(
        title = "Region name",
        description = """
            The OpenStack region to target in the service catalog (e.g. `RegionOne`).
            """
    )
    @PluginProperty(group = "connection")
    protected Property<String> region;

    @Schema(
        title = "Endpoint interface type",
        description = """
            The endpoint interface type to resolve from the service catalog: `PUBLIC`, `INTERNAL`, or `ADMIN`. Defaults to `PUBLIC`.
            """
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<EndpointType> endpointType = Property.ofValue(EndpointType.PUBLIC);

    @Schema(
        title = "HTTP client options",
        description = """
            Advanced HTTP client configuration such as timeouts and proxy settings.
            """
    )
    @PluginProperty(group = "connection")
    protected HttpConfiguration options;

    public static final Duration MAX_TIMEOUT = Duration.ofHours(6);
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(10);

    public static Duration validateTimeout(Duration timeout) {
        if (timeout == null) {
            return DEFAULT_TIMEOUT;
        }
        if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("`timeout` must be a positive duration");
        }
        if (timeout.compareTo(MAX_TIMEOUT) > 0) {
            throw new IllegalArgumentException("`timeout` cannot exceed " + MAX_TIMEOUT.toHours() + " hours (provided: " + timeout + ")");
        }
        return timeout;
    }

    protected OpenStackClient createClient(RunContext runContext) throws Exception {
        return OpenStackConnectionInterface.createClient(this, runContext);
    }
}
