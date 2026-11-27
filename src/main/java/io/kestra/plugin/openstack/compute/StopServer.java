package io.kestra.plugin.openstack.compute;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.openstack.client.OpenStackClient;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(
    title = "Stop a running OpenStack server",
    description = """
        Sends the `os-stop` action to gracefully shut down a running OpenStack Nova compute server.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Stop an OpenStack server",
            full = true,
            code = """
                id: stop_openstack_server
                namespace: company.team

                tasks:
                  - id: stop_server
                    type: io.kestra.plugin.openstack.compute.StopServer
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                """
        )
    }
)
public class StopServer extends AbstractServerActionTask {

    @Override
    protected String actionName() {
        return "os-stop";
    }

    @Override
    protected void executeAction(OpenStackClient client, String serverId, RunContext runContext) throws Exception {
        client.stopServer(serverId);
    }

    @Override
    protected String expectedStatus() {
        return "SHUTOFF";
    }
}
