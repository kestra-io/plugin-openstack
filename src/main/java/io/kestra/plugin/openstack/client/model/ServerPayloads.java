package io.kestra.plugin.openstack.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.Map;

public class ServerPayloads {

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Create {
        @JsonProperty("server")
        private ServerData server;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServerData {
        @JsonProperty("name")
        private String name;

        @JsonProperty("flavorRef")
        private String flavorRef;

        @JsonProperty("imageRef")
        private String imageRef;

        @JsonProperty("networks")
        private Object networks;

        @JsonProperty("key_name")
        private String keyName;

        @JsonProperty("user_data")
        private String userData;

        @JsonProperty("metadata")
        private Map<String, String> metadata;

        @ToString.Exclude
        @JsonProperty("adminPass")
        private String adminPass;

        @JsonProperty("description")
        private String description;

        @JsonProperty("tags")
        private List<String> tags;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetworkRef {
        @JsonProperty("uuid")
        private String uuid;

        @JsonProperty("port")
        private String port;

        @JsonProperty("fixed_ip")
        private String fixedIp;

        @JsonProperty("tag")
        private String tag;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Update {
        @JsonProperty("server")
        private UpdateData server;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UpdateData {
        @JsonProperty("name")
        private String name;

        @JsonProperty("description")
        private String description;
    }
}
