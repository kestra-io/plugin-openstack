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
    title = "Resume a suspended OpenStack server",
    description = """
        Sends the `resume` action to resume a suspended OpenStack Nova compute server.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Resume a suspended OpenStack server",
            full = true,
            code = """
                id: resume_openstack_server
                namespace: company.team

                tasks:
                  - id: resume_server
                    type: io.kestra.plugin.openstack.compute.ResumeServer
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                """
        )
    }
)
public class ResumeServer extends AbstractServerActionTask {

    @Override
    protected String actionName() {
        return "resume";
    }

    @Override
    protected void executeAction(OpenStackClient client, String serverId, RunContext runContext) throws Exception {
        client.resumeServer(serverId);
    }

    @Override
    protected String expectedStatus() {
        return "ACTIVE";
    }
}
