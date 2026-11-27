package io.kestra.plugin.openstack.compute;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.openstack.client.OpenStackClient;
import io.kestra.plugin.openstack.client.model.RebootType;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(
    title = "Reboot an OpenStack server",
    description = """
        Dispatches a soft (graceful ACPI) or hard (power cycle) reboot action to an OpenStack Nova compute server.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Perform a soft reboot of an OpenStack server",
            full = true,
            code = """
                id: reboot_openstack_server
                namespace: company.team

                tasks:
                  - id: reboot_server
                    type: io.kestra.plugin.openstack.compute.RebootServer
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    serverId: "9a2f6dc1-b1e8-45d2-a745-cfa098650392"
                    rebootType: SOFT
                """
        )
    }
)
public class RebootServer extends AbstractServerActionTask {

    @Schema(
        title = "Reboot type",
        description = """
            The type of reboot to dispatch (`SOFT` for ACPI graceful reboot, `HARD` for immediate power reset). Defaults to `SOFT`.
            """
    )
    @PluginProperty(group = "advanced")
    @Builder.Default
    private Property<RebootType> rebootType = Property.ofValue(RebootType.SOFT);

    @Override
    protected String actionName() {
        return "reboot";
    }

    @Override
    protected void executeAction(OpenStackClient client, String serverId, RunContext runContext) throws Exception {
        var rType = runContext.render(this.rebootType).as(RebootType.class).orElse(RebootType.SOFT);
        client.rebootServer(serverId, rType.getValue());
    }

    @Override
    protected String expectedStatus() {
        return "ACTIVE";
    }
}
