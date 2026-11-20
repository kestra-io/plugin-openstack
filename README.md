<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png" alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    OpenStack Plugin for Kestra
</h1>

<div align="center">
  <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

# Kestra OpenStack Plugin

Orchestrate OpenStack virtual compute infrastructure, power operations, snapshots, and event-driven automation directly within Kestra workflows. Compatible with any standard OpenStack deployment, including OVHcloud Public Cloud, Canonical OpenStack, Red Hat OpenStack Platform, and self-hosted OpenStack clouds.

## Why

- **Automated Cloud Orchestration**: Automate VM provisioning, scaling, scheduled power management, and disaster recovery.
- **Enterprise Parity**: Provides feature parity with VMware vSphere tasks for OpenStack clouds.
- **Event-Driven Automation**: Trigger flows automatically when server states change or lifecycle actions occur, with persistent KV Store watermarking.

## What

Comprehensive suite of compute tasks and polling triggers under `io.kestra.plugin.openstack.compute`:

### Virtual Machine Lifecycle
- **CreateServer**: Provisions a new Nova VM instance with automatic flavor, image, and network name resolution, automatic cloud-init base64 encoding, and wait-for-completion polling.
- **ListServers**: Queries detailed server instances with regex filtering, pagination markers, and `FETCH`, `FETCH_ONE`, or `STORE` (persisted to Kestra internal storage) fetch modes.
- **UpdateServer**: Updates server name, description, flavor resizing (with auto-confirmation), network interface attachments, and metadata.
- **DeleteServer**: Permanently terminates and deletes a server instance.
- **GetServerMetadata**: Retrieves key-value metadata attached to a server.

### Power & Instance State Management
- **StartServer**: Powers on a stopped/shutoff server instance.
- **StopServer**: Gracefully shuts down a running server instance.
- **RebootServer**: Issues a soft (graceful ACPI) or hard (power cycle) reboot.
- **ResetServer**: Forces an immediate hard reset reboot.
- **SuspendServer**: Suspends an instance to hypervisor memory.
- **ResumeServer**: Resumes a suspended instance.

### Snapshot & Cloning (Glance / Nova)
- **CreateServerSnapshot**: Creates a point-in-time Glance snapshot image from a server.
- **ListServerSnapshots**: Lists all snapshot images originating from an instance.
- **DeleteServerSnapshot**: Deletes an intermediate snapshot image from Glance.
- **RestoreServerFromSnapshot**: Rebuilds an instance from a snapshot image.
- **CloneServer**: Clones an existing server by snapshotting, waiting for the snapshot to transition to `active` in Glance, and launching a new instance from that snapshot.

### Event Triggers
- **Trigger**: Polling trigger monitoring OpenStack servers for lifecycle events (`ALL`, `START`, `STOP`, `REBOOT`, `PAUSE`, `SUSPEND`, `RESUME`, `DELETE`, `RESIZE`, `REBUILD`) with persistent namespace KV Store watermarking and compact event payloads.

---

## Running Kestra locally with this plugin

1. Build the shadow JAR: `./gradlew shadowJar`. The output lands in `build/libs/`.
2. Run `docker compose up`. `docker-compose.yml` builds `kestra/kestra:latest` and mounts `build/libs/` to `/app/plugins/`, so Kestra picks up the jar on startup.
3. Kestra UI is available at [localhost:8080](http://localhost:8080).

## Documentation
* Full documentation: [kestra.io/docs](https://kestra.io/docs)
* Plugin Developer Guide: [kestra.io/docs/plugin-developer-guide](https://kestra.io/docs/plugin-developer-guide)

## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)
