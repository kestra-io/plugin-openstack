# Kestra OpenStack Plugin

## What

- Provides OpenStack compute, snapshot, and trigger automation components under `io.kestra.plugin.openstack.compute`.
- Client and response models located in `io.kestra.plugin.openstack.client.model`.
- Includes tasks: `CreateServer`, `ListServers`, `UpdateServer`, `DeleteServer`, `GetServerMetadata`, `StartServer`, `StopServer`, `RebootServer`, `ResetServer`, `SuspendServer`, `ResumeServer`, `CreateServerSnapshot`, `ListServerSnapshots`, `DeleteServerSnapshot`, `RestoreServerFromSnapshot`, `CloneServer`, and `Trigger`.

## Why

- **What user problem does this solve?** Teams orchestrating hybrid clouds require seamless, automated virtual machine provisioning, power management, disaster recovery snapshotting, and event-driven reactions on OpenStack infrastructure.
- **Why would a team adopt this plugin in a workflow?** It eliminates manual cloud administration scripts by providing declarative, enterprise-grade Kestra tasks with full name resolution, base64 cloud-init encoding, and KV Store watermark isolation.
- **What operational/business outcome does it enable?** Automates cost-saving schedules (auto-stopping non-production VMs), enables VMware-to-OpenStack workload migrations, and ensures zero-data-loss point-in-time disaster recovery snapshots.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin`:

- `openstack.client`: HTTP client (`OpenStackClient`) for Keystone v3, Nova Compute, and Glance Image APIs.
- `openstack.client.model`: Typed Jackson models, Enums (`EndpointType`, `RebootType`, `EventType`), and DTOs with full `@Schema` annotations.
- `openstack.compute`: Declarative Kestra tasks, shared connection interface (`OpenStackConnectionInterface`), server action base (`AbstractServerActionTask`), and state trigger (`Trigger`).

### Project Structure

```
plugin-openstack/
├── src/main/java/io/kestra/plugin/openstack/
│   ├── client/
│   │   ├── model/
│   │   └── OpenStackClient.java
│   └── compute/
│       ├── AbstractComputeTask.java
│       ├── AbstractServerActionTask.java
│       ├── CloneServer.java
│       ├── CreateServer.java
│       ├── CreateServerSnapshot.java
│       ├── DeleteServer.java
│       ├── DeleteServerSnapshot.java
│       ├── GetServerMetadata.java
│       ├── ListServerSnapshots.java
│       ├── ListServers.java
│       ├── OpenStackConnectionInterface.java
│       ├── RebootServer.java
│       ├── ResetServer.java
│       ├── RestoreServerFromSnapshot.java
│       ├── ResumeServer.java
│       ├── StartServer.java
│       ├── StopServer.java
│       ├── SuspendServer.java
│       ├── Trigger.java
│       └── UpdateServer.java
├── src/test/java/io/kestra/plugin/openstack/compute/
│   ├── OpenStackComputeTest.java
│   └── OpenStackTriggerTest.java
├── build.gradle
└── README.md
```

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
- https://docs.openstack.org/api-ref/compute/
- https://docs.openstack.org/api-ref/image/v2/
