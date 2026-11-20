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

import java.time.Duration;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Delete an OpenStack server",
    description = """
        Permanently terminates and deletes an OpenStack Nova compute server.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an OpenStack server",
            full = true,
            code = """
                id: delete_openstack_server
                namespace: company.team

                tasks:
                  - id: delete_server
                    type: io.kestra.plugin.openstack.compute.DeleteServer
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                """
        )
    }
)
public class DeleteServer extends AbstractServerActionTask {

    @Override
    protected String actionName() {
        return "delete";
    }

    @Override
    protected void executeAction(OpenStackClient client, String serverId, RunContext runContext) throws Exception {
        client.deleteServer(serverId);
    }

    @Override
    protected void waitForCompletion(OpenStackClient client, String serverId, Duration timeout, RunContext runContext) throws Exception {
        runContext.logger().info("Waiting for server '{}' to be deleted...", serverId);
        client.waitForServerDeletion(serverId, timeout);
    }
}
