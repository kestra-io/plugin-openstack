package io.kestra.plugin.openstack.client.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum RebootType {
    SOFT("SOFT"),
    HARD("HARD");

    private final String value;

    RebootType(String value) {
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
