package io.kestra.plugin.openstack.client.model;

public enum EventType {
    ALL,
    CREATE,
    START,
    STOP,
    REBOOT,
    PAUSE,
    SUSPEND,
    RESUME,
    DELETE,
    RESIZE,
    REBUILD
}
