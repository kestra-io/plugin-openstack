# How to use the OpenStack plugin

Integrate with [OpenStack](https://www.openstack.org/) to orchestrate virtual machine lifecycle, power operations, metadata, snapshots, and event-driven automation across private and public OpenStack clouds (such as OVHcloud Public Cloud, Canonical OpenStack, Red Hat OpenStack Platform, etc.).

The OpenStack plugin provides tasks for [Nova Compute](https://docs.openstack.org/api-ref/compute/) and [Glance Image](https://docs.openstack.org/api-ref/image/v2/) services, authenticated via [Keystone v3](https://docs.openstack.org/api-ref/identity/v3/).

## Authentication

All tasks authenticate against the OpenStack Keystone Identity API (`/v3`) using either **Application Credentials** (recommended for automation and service accounts) or **User Credentials** (username, password, and project/domain scope).

Credentials can be passed via Kestra [secrets](https://kestra.io/docs/concepts/secret) or configured org-wide using [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults).

### 1. Application Credentials (Recommended)

Application credentials provide scoped, non-expiring machine tokens without exposing primary user passwords:

```yaml
tasks:
  - id: list_servers
    type: io.kestra.plugin.openstack.compute.ListServers
    authUrl: "https://identity.region.openstack.example.com/v3"
    applicationCredentialId: "{{ secret('OS_APP_CRED_ID') }}"
    applicationCredentialSecret: "{{ secret('OS_APP_CRED_SECRET') }}"
```

### 2. Username and Password

```yaml
tasks:
  - id: list_servers
    type: io.kestra.plugin.openstack.compute.ListServers
    authUrl: "https://identity.region.openstack.example.com/v3"
    username: "kestra-operator"
    password: "{{ secret('OS_PASSWORD') }}"
    userDomainName: "Default"
    projectName: "production"
    projectDomainName: "Default"
    region: "RegionOne"
```

## OpenStack Snapshot Architecture Gotcha

In OpenStack, VM snapshots created via Nova's `createImage` action are stored and managed directly in Glance (`/v2/images`). Consequently:
- Snapshot operations (such as listing, restoring, and deleting) use Glance image UUIDs (`imageId`).
- When a snapshot is initiated, Glance transitions through asynchronous statuses (`queued` -> `saving` -> `active`). Tasks like `CloneServer` automatically wait for the Glance image to reach `active` status before booting the clone instance.

---

## Compute Tasks

### Virtual Machine Provisioning & Lifecycle

- **CreateServer**: Provision a new Nova VM instance with automatic name resolution (flavor, image, network) and base64 cloud-init encoding.
  - **Required fields**: `name`, `flavorRef` (flavor UUID or name)
  - **Optional fields**: `imageRef` (image UUID or name), `networks` (list of network/port UUIDs or names), `keyName`, `userData`, `metadata`, `adminPass`, `serverDescription`, `tags`, `waitUntilComplete` (defaults to `true`), `waitTimeout`
- **ListServers**: Query servers with regex filtering, status filters, and pagination markers; supports `FETCH`, `FETCH_ONE`, and `STORE` (persisted to Kestra internal storage) fetch modes.
  - **Optional fields**: `fetchType` (`FETCH`, `FETCH_ONE`, `STORE`), `name`, `nameRegex`, `status` (`ACTIVE`, `SHUTOFF`, `ERROR`, etc.), `changesSince`, `limit`, `pageSize`, `marker`
- **UpdateServer**: Update mutable attributes (name, server description, flavor resize with auto-confirmation, network attachments, metadata) of an existing server.
  - **Required fields**: `serverId`
  - **Optional fields**: `name`, `serverDescription`, `flavorRef`, `autoConfirmResize` (defaults to `true`), `waitTimeout`, `metadata`, `networkId`
- **DeleteServer**: Terminate and permanently delete a VM instance.
  - **Required fields**: `serverId`
- **GetServerMetadata**: Retrieve key-value metadata attached to a server.
  - **Required fields**: `serverId`

### Power & Instance State Management

All lifecycle action tasks (`DeleteServer`, `StartServer`, `StopServer`, `RebootServer`, `ResetServer`, `SuspendServer`, `ResumeServer`) accept `waitUntilComplete` (defaults to `false`) and `waitTimeout` (defaults to `PT10M`).

- **StartServer**: Power on a stopped/shutoff server instance.
  - **Required fields**: `serverId`
- **StopServer**: Gracefully power off a running server instance.
  - **Required fields**: `serverId`
- **RebootServer**: Issue a soft or hard reboot to a server instance.
  - **Required fields**: `serverId`
  - **Optional fields**: `rebootType` (`SOFT` or `HARD`, defaults to `SOFT`)
- **ResetServer**: Force a hard reboot on a server instance.
  - **Required fields**: `serverId`
- **SuspendServer**: Suspend a server instance to hypervisor memory.
  - **Required fields**: `serverId`
- **ResumeServer**: Resume a suspended server instance.
  - **Required fields**: `serverId`

### Snapshots & Disaster Recovery

- **CreateServerSnapshot**: Create a point-in-time Glance snapshot image from an active server.
  - **Required fields**: `serverId`, `name`
  - **Optional fields**: `metadata`
- **ListServerSnapshots**: List all snapshot images belonging to an instance.
  - **Required fields**: `serverId`
- **DeleteServerSnapshot**: Permanently delete a snapshot image from Glance.
  - **Required fields**: `imageId`
- **RestoreServerFromSnapshot**: Rebuild an existing server instance from a snapshot image.
  - **Required fields**: `serverId`, `imageId`
- **CloneServer**: Orchestrate automated server cloning by snapshotting a source server, waiting for the snapshot to become active in Glance, and launching a new instance from that snapshot.
  - **Required fields**: `sourceServerId`, `name`
  - **Optional fields**: `flavorRef`, `networks`, `snapshotName`, `deleteSnapshotAfterClone` (defaults to `false`), `waitTimeout`

## Trigger

- **Trigger**: Polling trigger that monitors OpenStack Nova compute servers for state changes and lifecycle events using KV Store watermarking and event filtering.
  - **Properties**: `status`, `serverNameRegex`, `eventType` (`ALL`, `CREATE`, `START`, `STOP`, `REBOOT`, `PAUSE`, `SUSPEND`, `RESUME`, `DELETE`, `RESIZE`, `REBUILD`), `maxEvents` (defaults to 50), `interval` (defaults to `PT1M`)
