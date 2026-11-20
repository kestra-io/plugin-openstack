package io.kestra.plugin.openstack.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum EndpointType {
    PUBLIC("public"),
    INTERNAL("internal"),
    ADMIN("admin");

    private final String value;

    EndpointType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
