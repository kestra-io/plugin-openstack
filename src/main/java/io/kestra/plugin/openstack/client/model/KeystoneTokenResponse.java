package io.kestra.plugin.openstack.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;

@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KeystoneTokenResponse {

    @JsonProperty("token")
    private Token token;

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Token {
        @JsonProperty("expires_at")
        private String expiresAt;

        @JsonProperty("issued_at")
        private String issuedAt;

        @JsonProperty("catalog")
        private List<ServiceCatalogEntry> catalog;

        @JsonProperty("project")
        private Project project;

        @JsonProperty("user")
        private User user;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceCatalogEntry {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private String type;

        @JsonProperty("endpoints")
        private List<ServiceEndpoint> endpoints;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ServiceEndpoint {
        @JsonProperty("id")
        private String id;

        @JsonProperty("interface")
        private String interfaceType;

        @JsonProperty("region")
        private String region;

        @JsonProperty("region_id")
        private String regionId;

        @JsonProperty("url")
        private String url;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;
    }

    @Getter
    @Builder
    @ToString
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class User {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;
    }
}
