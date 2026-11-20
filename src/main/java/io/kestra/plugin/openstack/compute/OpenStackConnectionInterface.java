package io.kestra.plugin.openstack.compute;

import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.openstack.client.OpenStackClient;
import io.kestra.plugin.openstack.client.model.EndpointType;

public interface OpenStackConnectionInterface {

    Property<String> getAuthUrl();

    Property<String> getApplicationCredentialId();

    Property<String> getApplicationCredentialSecret();

    Property<String> getUsername();

    Property<String> getPassword();

    Property<String> getProjectName();

    Property<String> getUserDomainName();

    Property<String> getProjectDomainName();

    Property<String> getRegion();

    Property<EndpointType> getEndpointType();

    HttpConfiguration getOptions();

    static OpenStackClient createClient(OpenStackConnectionInterface connection, RunContext runContext) throws Exception {
        var rAuthUrl = runContext.render(connection.getAuthUrl()).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`authUrl` is required for OpenStack authentication"));

        var rAppId = runContext.render(connection.getApplicationCredentialId()).as(String.class).orElse(null);
        var rAppSecret = runContext.render(connection.getApplicationCredentialSecret()).as(String.class).orElse(null);
        var rUser = runContext.render(connection.getUsername()).as(String.class).orElse(null);
        var rPass = runContext.render(connection.getPassword()).as(String.class).orElse(null);
        var rProject = runContext.render(connection.getProjectName()).as(String.class).orElse(null);
        var rUserDomain = runContext.render(connection.getUserDomainName()).as(String.class).orElse("Default");
        var rProjectDomain = runContext.render(connection.getProjectDomainName()).as(String.class).orElse("Default");
        var rRegion = runContext.render(connection.getRegion()).as(String.class).orElse(null);

        var rEndpointTypeEnum = runContext.render(connection.getEndpointType()).as(EndpointType.class).orElse(EndpointType.PUBLIC);
        var rEndpointType = rEndpointTypeEnum != null ? rEndpointTypeEnum.getValue() : "public";

        return OpenStackClient.builder()
            .runContext(runContext)
            .httpConfiguration(connection.getOptions())
            .authUrl(rAuthUrl)
            .applicationCredentialId(rAppId)
            .applicationCredentialSecret(rAppSecret)
            .username(rUser)
            .password(rPass)
            .projectName(rProject)
            .userDomainName(rUserDomain)
            .projectDomainName(rProjectDomain)
            .region(rRegion)
            .endpointType(rEndpointType)
            .build();
    }
}
