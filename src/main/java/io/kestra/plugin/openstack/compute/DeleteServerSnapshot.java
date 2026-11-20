package io.kestra.plugin.openstack.compute;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "Delete an OpenStack server snapshot",
    description = """
        Deletes a Glance snapshot image previously created from an OpenStack Nova compute server instance.
        """
)
@Plugin(
    examples = {
        @Example(
            title = "Delete an OpenStack server snapshot image",
            full = true,
            code = """
                id: delete_openstack_snapshot
                namespace: company.team

                tasks:
                  - id: delete_snapshot
                    type: io.kestra.plugin.openstack.compute.DeleteServerSnapshot
                    authUrl: "https://keystone.company.local:5000/v3"
                    applicationCredentialId: "{{ secret('OPENSTACK_APP_CREDENTIAL_ID') }}"
                    applicationCredentialSecret: "{{ secret('OPENSTACK_APP_CREDENTIAL_SECRET') }}"
                    imageId: "4f7a2b91-9e23-4412-a4f6-8bb819c94723"
                """
        )
    }
)
public class DeleteServerSnapshot extends AbstractComputeTask implements RunnableTask<VoidOutput> {

    @NotNull
    @Schema(
        title = "Snapshot image UUID or name",
        description = """
            The unique identifier or name of the snapshot image to delete.
            """
    )
    @PluginProperty(group = "main")
    private Property<String> imageId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        var logger = runContext.logger();
        var rImageId = runContext.render(this.imageId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("`imageId` is required for DeleteServerSnapshot"));

        logger.info("Deleting Glance snapshot image '{}'...", rImageId);

        try (var client = this.createClient(runContext)) {
            var targetImageId = client.resolveImageId(rImageId);
            client.deleteSnapshot(targetImageId);
            logger.info("Successfully deleted snapshot image '{}'.", targetImageId);

            return null;
        }
    }
}
