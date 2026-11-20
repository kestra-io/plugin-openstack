package io.kestra.plugin.openstack.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.openstack.client.model.*;
import lombok.Builder;
import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.io.Closeable;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class OpenStackClient implements Closeable {

    private static final String DEFAULT_DOMAIN = "Default";
    private static final String DEFAULT_INTERFACE = "public";
    private static final String NOVA_MICROVERSION = "2.58";
    private static final ObjectMapper MAPPER = JacksonMapper.ofJson();

    @Getter
    private final RunContext runContext;
    @Getter
    private final HttpClient httpClient;
    @Getter
    private final String authUrl;
    @Getter
    private final String applicationCredentialId;
    @Getter(AccessLevel.NONE)
    private final String applicationCredentialSecret;
    @Getter
    private final String username;
    @Getter(AccessLevel.NONE)
    private final String password;
    @Getter
    private final String projectName;
    @Getter
    private final String userDomainName;
    @Getter
    private final String projectDomainName;
    @Getter
    private final String region;
    @Getter
    private final String endpointType;

    @Getter(AccessLevel.NONE)
    private String token;
    @Getter
    private String computeUrl;
    @Getter
    private String imageUrl;
    private String networkUrl;

    @Builder
    public OpenStackClient(
        RunContext runContext,
        HttpConfiguration httpConfiguration,
        String authUrl,
        String applicationCredentialId,
        String applicationCredentialSecret,
        String username,
        String password,
        String projectName,
        String userDomainName,
        String projectDomainName,
        String region,
        String endpointType
    ) throws Exception {
        this.runContext = runContext;
        this.httpClient = new HttpClient(runContext, httpConfiguration);
        this.authUrl = cleanUrl(authUrl);
        this.applicationCredentialId = applicationCredentialId;
        this.applicationCredentialSecret = applicationCredentialSecret;
        this.username = username;
        this.password = password;
        this.projectName = projectName;
        this.userDomainName = StringUtils.isNotBlank(userDomainName) ? userDomainName : DEFAULT_DOMAIN;
        this.projectDomainName = StringUtils.isNotBlank(projectDomainName) ? projectDomainName : DEFAULT_DOMAIN;
        this.region = region;
        this.endpointType = StringUtils.isNotBlank(endpointType) ? endpointType : DEFAULT_INTERFACE;
    }

    private static String pathSegment(String value) {
        if (StringUtils.isBlank(value) || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("Invalid resource identifier: '" + value + "'.");
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String cleanUrl(String url) {
        if (url == null) {
            return null;
        }
        var trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public synchronized void authenticate() throws Exception {
        if (this.token != null) {
            return;
        }

        var identityBuilder = KeystoneAuthRequest.Identity.builder();
        KeystoneAuthRequest.Scope scope = null;

        if (StringUtils.isNotBlank(applicationCredentialId) && StringUtils.isNotBlank(applicationCredentialSecret)) {
            identityBuilder
                .methods(List.of("application_credential"))
                .applicationCredential(KeystoneAuthRequest.ApplicationCredential.builder()
                    .id(applicationCredentialId)
                    .secret(applicationCredentialSecret)
                    .build());
        } else if (StringUtils.isNotBlank(username) && StringUtils.isNotBlank(password)) {
            identityBuilder
                .methods(List.of("password"))
                .password(KeystoneAuthRequest.Password.builder()
                    .user(KeystoneAuthRequest.User.builder()
                        .name(username)
                        .password(password)
                        .domain(KeystoneAuthRequest.Domain.builder().name(userDomainName).build())
                        .build())
                    .build());

            if (StringUtils.isNotBlank(projectName)) {
                scope = KeystoneAuthRequest.Scope.builder()
                    .project(KeystoneAuthRequest.Project.builder()
                        .name(projectName)
                        .domain(KeystoneAuthRequest.Domain.builder().name(projectDomainName).build())
                        .build())
                    .build();
            }
        } else {
            throw new IllegalArgumentException(
                "Missing credentials: must supply either applicationCredentialId/Secret or username/password."
            );
        }

        var authRequest = KeystoneAuthRequest.builder()
            .auth(KeystoneAuthRequest.Auth.builder()
                .identity(identityBuilder.build())
                .scope(scope)
                .build())
            .build();

        var tokenEndpoint = this.authUrl.endsWith("/v3")
            ? this.authUrl + "/auth/tokens"
            : (this.authUrl.endsWith("/auth/tokens") ? this.authUrl : this.authUrl + "/v3/auth/tokens");

        var requestBody = MAPPER.writeValueAsString(authRequest);

        var req = HttpRequest.builder()
            .method("POST")
            .uri(URI.create(tokenEndpoint))
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .body(HttpRequest.StringRequestBody.builder()
                .content(requestBody)
                .contentType("application/json")
                .charset(StandardCharsets.UTF_8)
                .build())
            .build();

        var resp = executeHttp(req, String.class);

        this.token = resp.getHeaders().firstValue("X-Subject-Token")
            .orElseThrow(() -> new IllegalStateException("Keystone response did not contain X-Subject-Token header"));

        var tokenResponse = parseResponse(resp, KeystoneTokenResponse.class, "Keystone token authentication");
        resolveServiceEndpoints(tokenResponse);
    }

    private void resolveServiceEndpoints(KeystoneTokenResponse tokenResponse) {
        if (tokenResponse == null || tokenResponse.getToken() == null || tokenResponse.getToken().getCatalog() == null) {
            throw new IllegalStateException("Keystone token response contains empty service catalog.");
        }

        var catalog = tokenResponse.getToken().getCatalog();

        for (var entry : catalog) {
            if ("compute".equalsIgnoreCase(entry.getType()) && entry.getEndpoints() != null) {
                for (var ep : entry.getEndpoints()) {
                    if (matchesEndpoint(ep)) {
                        this.computeUrl = cleanUrl(ep.getUrl());
                        break;
                    }
                }
            } else if ("image".equalsIgnoreCase(entry.getType()) && entry.getEndpoints() != null) {
                for (var ep : entry.getEndpoints()) {
                    if (matchesEndpoint(ep)) {
                        this.imageUrl = cleanUrl(ep.getUrl());
                        break;
                    }
                }
            } else if ("network".equalsIgnoreCase(entry.getType()) && entry.getEndpoints() != null) {
                for (var ep : entry.getEndpoints()) {
                    if (matchesEndpoint(ep)) {
                        this.networkUrl = cleanUrl(ep.getUrl());
                        break;
                    }
                }
            }
        }

        if (this.computeUrl == null) {
            throw new IllegalStateException("Could not resolve compute endpoint URL from Keystone catalog for region " + region);
        }
    }

    private boolean matchesEndpoint(KeystoneTokenResponse.ServiceEndpoint ep) {
        if (ep.getUrl() == null) {
            return false;
        }
        var interfaceMatch = this.endpointType.equalsIgnoreCase(ep.getInterfaceType());
        if (!interfaceMatch) {
            return false;
        }
        if (StringUtils.isNotBlank(this.region)) {
            return this.region.equalsIgnoreCase(ep.getRegion()) || this.region.equalsIgnoreCase(ep.getRegionId());
        }
        return true;
    }

    private <T> T parseResponse(HttpResponse<String> resp, Class<T> clazz, String operation) throws Exception {
        var body = resp.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Empty response body received from OpenStack API during " + operation);
        }
        return MAPPER.readValue(body, clazz);
    }

    public Server createServer(ServerPayloads.Create payload) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/servers";
        var body = MAPPER.writeValueAsString(payload);

        var req = computeRequest("POST", uri, body);
        var resp = executeHttp(req, String.class);
        var single = parseResponse(resp, ServerResponses.SingleServer.class, "server creation");
        return single.getServer();
    }

    public Server getServer(String serverId) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/servers/" + pathSegment(serverId);

        var req = computeRequest("GET", uri, null);
        var resp = executeHttp(req, String.class);
        var single = parseResponse(resp, ServerResponses.SingleServer.class, "get server");
        return single.getServer();
    }

    public List<Server> listServers(Map<String, String> queryParams) throws Exception {
        authenticate();
        var uriBuilder = new StringBuilder(this.computeUrl).append("/servers/detail");
        if (queryParams != null && !queryParams.isEmpty()) {
            uriBuilder.append("?");
            var first = true;
            var sortedParams = new TreeMap<>(queryParams);
            for (var entry : sortedParams.entrySet()) {
                if (entry.getValue() != null) {
                    if (!first) {
                        uriBuilder.append("&");
                    }
                    first = false;
                    uriBuilder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                        .append("=")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                }
            }
        }

        var req = computeRequest("GET", uriBuilder.toString(), null);
        var resp = executeHttp(req, String.class);
        var multiple = parseResponse(resp, ServerResponses.MultipleServers.class, "list servers");
        return multiple != null && multiple.getServers() != null ? multiple.getServers() : Collections.emptyList();
    }

    public Server updateServer(String serverId, ServerPayloads.Update payload) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/servers/" + pathSegment(serverId);
        var body = MAPPER.writeValueAsString(payload);

        var req = computeRequest("PUT", uri, body);
        var resp = executeHttp(req, String.class);
        var single = parseResponse(resp, ServerResponses.SingleServer.class, "update server");
        return single.getServer();
    }

    public void deleteServer(String serverId) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/servers/" + pathSegment(serverId);
        var req = computeRequest("DELETE", uri, null);
        executeHttp(req, String.class);
    }

    public String serverAction(String serverId, String actionJson) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/servers/" + pathSegment(serverId) + "/action";
        var req = computeRequest("POST", uri, actionJson);
        var resp = executeHttp(req, String.class);
        return resp.getBody();
    }

    public void startServer(String serverId) throws Exception {
        serverAction(serverId, "{\"os-start\": null}");
    }

    public void stopServer(String serverId) throws Exception {
        serverAction(serverId, "{\"os-stop\": null}");
    }

    public void rebootServer(String serverId, String type) throws Exception {
        String rebootType = StringUtils.isNotBlank(type) ? type.trim().toUpperCase(Locale.ROOT) : "SOFT";
        if (!rebootType.equals("SOFT") && !rebootType.equals("HARD")) {
            throw new IllegalArgumentException("Invalid reboot type '" + type + "': expected SOFT or HARD.");
        }
        serverAction(serverId, MAPPER.writeValueAsString(Map.of("reboot", Map.of("type", rebootType))));
    }

    public void suspendServer(String serverId) throws Exception {
        serverAction(serverId, "{\"suspend\": null}");
    }

    public void resumeServer(String serverId) throws Exception {
        serverAction(serverId, "{\"resume\": null}");
    }

    public void resizeServer(String serverId, String flavorId) throws Exception {
        serverAction(serverId, MAPPER.writeValueAsString(Map.of("resize", Map.of("flavorRef", flavorId))));
    }

    public void confirmResize(String serverId) throws Exception {
        serverAction(serverId, "{\"confirmResize\": null}");
    }

    public String createSnapshot(String serverId, String snapshotName, Map<String, String> metadata) throws Exception {
        authenticate();
        var inner = new HashMap<String, Object>();
        inner.put("name", snapshotName);
        if (metadata != null && !metadata.isEmpty()) {
            inner.put("metadata", metadata);
        }
        var payload = Map.of("createImage", inner);
        var body = MAPPER.writeValueAsString(payload);

        var respJson = serverAction(serverId, body);
        if (StringUtils.isNotBlank(respJson)) {
            var img = MAPPER.readValue(respJson, ServerResponses.CreateImage.class);
            return img.getImageId();
        }
        return null;
    }

    public void restoreServer(String serverId, String imageRef) throws Exception {
        serverAction(serverId, MAPPER.writeValueAsString(Map.of("rebuild", Map.of("imageRef", imageRef))));
    }

    public Map<String, String> getServerMetadata(String serverId) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/servers/" + pathSegment(serverId) + "/metadata";
        var req = computeRequest("GET", uri, null);
        var resp = executeHttp(req, String.class);
        var meta = parseResponse(resp, ServerResponses.Metadata.class, "get server metadata");
        return meta != null && meta.getMetadata() != null ? meta.getMetadata() : Collections.emptyMap();
    }

    public void updateServerMetadata(String serverId, Map<String, String> metadata) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/servers/" + pathSegment(serverId) + "/metadata";
        var body = MAPPER.writeValueAsString(Map.of("metadata", metadata));
        var req = computeRequest("POST", uri, body);
        executeHttp(req, String.class);
    }

    public void attachInterface(String serverId, String networkId) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/servers/" + pathSegment(serverId) + "/os-interface";
        var body = MAPPER.writeValueAsString(Map.of("interfaceAttachment", Map.of("net_id", networkId)));
        var req = computeRequest("POST", uri, body);
        executeHttp(req, String.class);
    }

    public GlanceResponses.GlanceImage getImage(String imageId) throws Exception {
        authenticate();
        if (this.imageUrl == null) {
            throw new IllegalStateException("Glance image service URL is not resolved from service catalog.");
        }
        var baseImage = this.imageUrl.endsWith("/v2") ? this.imageUrl : this.imageUrl + "/v2";
        var uri = baseImage + "/images/" + pathSegment(imageId);
        var req = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(uri))
            .addHeader("X-Auth-Token", this.token)
            .addHeader("Accept", "application/json")
            .build();

        var resp = executeHttp(req, String.class);
        return parseResponse(resp, GlanceResponses.GlanceImage.class, "get glance image");
    }

    public List<GlanceResponses.GlanceImage> listServerSnapshots(String serverId) throws Exception {
        authenticate();
        if (this.imageUrl == null) {
            throw new IllegalStateException("Glance image service URL is not resolved from service catalog.");
        }
        var baseImage = this.imageUrl.endsWith("/v2") ? this.imageUrl : this.imageUrl + "/v2";
        var uri = baseImage + "/images?instance_uuid=" + URLEncoder.encode(serverId, StandardCharsets.UTF_8)
            + "&image_type=snapshot";

        var req = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(uri))
            .addHeader("X-Auth-Token", this.token)
            .addHeader("Accept", "application/json")
            .build();

        var resp = executeHttp(req, String.class);
        var res = parseResponse(resp, GlanceResponses.MultipleImages.class, "list server snapshots");
        return res != null && res.getImages() != null ? res.getImages() : Collections.emptyList();
    }

    public void deleteSnapshot(String imageId) throws Exception {
        authenticate();
        if (this.imageUrl == null) {
            throw new IllegalStateException("Glance image service URL is not resolved from service catalog.");
        }
        var baseImage = this.imageUrl.endsWith("/v2") ? this.imageUrl : this.imageUrl + "/v2";
        var uri = baseImage + "/images/" + pathSegment(imageId);
        var req = HttpRequest.builder()
            .method("DELETE")
            .uri(URI.create(uri))
            .addHeader("X-Auth-Token", this.token)
            .addHeader("Accept", "application/json")
            .build();

        executeHttp(req, String.class);
    }

    public List<FlavorResponses.Flavor> listFlavors() throws Exception {
        authenticate();
        var uri = this.computeUrl + "/flavors/detail";
        var req = computeRequest("GET", uri, null);
        var resp = executeHttp(req, String.class);
        var res = parseResponse(resp, FlavorResponses.MultipleFlavors.class, "list flavors");
        return res != null && res.getFlavors() != null ? res.getFlavors() : Collections.emptyList();
    }

    public String resolveServerId(String serverNameOrId) throws Exception {
        if (StringUtils.isBlank(serverNameOrId)) {
            return null;
        }
        authenticate();
        try {
            var directServer = getServer(serverNameOrId);
            if (directServer != null && directServer.getId() != null) {
                return directServer.getId();
            }
        } catch (Exception ignored) {
        }

        var uri = this.computeUrl + "/servers?name=" + URLEncoder.encode(serverNameOrId, StandardCharsets.UTF_8);
        var req = computeRequest("GET", uri, null);
        var resp = executeHttp(req, String.class);
        var res = parseResponse(resp, ServerResponses.MultipleServers.class, "resolve server by name");
        var servers = res != null && res.getServers() != null ? res.getServers() : Collections.<Server>emptyList();

        if (servers.isEmpty()) {
            throw new IllegalArgumentException("OpenStack server not found: '" + serverNameOrId + "'");
        }
        if (servers.size() > 1) {
            throw new IllegalArgumentException("Ambiguous OpenStack server: '" + serverNameOrId + "' matches "
                + servers.size() + " servers. Please specify the exact server UUID.");
        }
        return servers.getFirst().getId();
    }

    public FlavorResponses.Flavor getFlavor(String flavorId) throws Exception {
        authenticate();
        var uri = this.computeUrl + "/flavors/" + pathSegment(flavorId);
        var req = computeRequest("GET", uri, null);
        var resp = executeHttp(req, String.class);
        var res = parseResponse(resp, FlavorResponses.SingleFlavor.class, "get flavor");
        return res != null ? res.getFlavor() : null;
    }

    public String resolveFlavorId(String flavorNameOrId) throws Exception {
        if (StringUtils.isBlank(flavorNameOrId)) {
            return null;
        }
        authenticate();
        try {
            var directFlavor = getFlavor(flavorNameOrId);
            if (directFlavor != null && directFlavor.getId() != null) {
                return directFlavor.getId();
            }
        } catch (Exception ignored) {
        }

        var flavors = listFlavors();
        var matches = new ArrayList<FlavorResponses.Flavor>();
        for (var flv : flavors) {
            if (flavorNameOrId.equals(flv.getId()) || flavorNameOrId.equalsIgnoreCase(flv.getName())) {
                matches.add(flv);
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("OpenStack flavor not found: '" + flavorNameOrId + "'");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous OpenStack flavor: '" + flavorNameOrId + "' matches "
                + matches.size() + " flavors. Please specify the exact flavor UUID.");
        }
        return matches.getFirst().getId();
    }

    public String resolveImageId(String imageNameOrId) throws Exception {
        if (StringUtils.isBlank(imageNameOrId)) {
            return null;
        }
        authenticate();
        if (this.imageUrl == null) {
            return imageNameOrId;
        }
        var baseImage = this.imageUrl.endsWith("/v2") ? this.imageUrl : this.imageUrl + "/v2";

        try {
            var directImage = getImage(imageNameOrId);
            if (directImage != null && directImage.getId() != null) {
                return directImage.getId();
            }
        } catch (Exception ignored) {
        }

        var uri = baseImage + "/images?name=" + URLEncoder.encode(imageNameOrId, StandardCharsets.UTF_8);
        var req = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(uri))
            .addHeader("X-Auth-Token", this.token)
            .addHeader("Accept", "application/json")
            .build();

        var resp = executeHttp(req, String.class);
        var res = parseResponse(resp, GlanceResponses.MultipleImages.class, "resolve image");
        var images = res != null && res.getImages() != null ? res.getImages() : Collections.<GlanceResponses.GlanceImage>emptyList();

        if (images.isEmpty()) {
            throw new IllegalArgumentException("OpenStack image not found: '" + imageNameOrId + "'");
        }
        if (images.size() > 1) {
            throw new IllegalArgumentException("Ambiguous OpenStack image: '" + imageNameOrId + "' matches "
                + images.size() + " images. Please specify the exact image UUID.");
        }
        return images.getFirst().getId();
    }

    public String resolveNetworkId(String networkNameOrId) throws Exception {
        if (StringUtils.isBlank(networkNameOrId)) {
            return null;
        }
        authenticate();
        if (this.networkUrl == null) {
            return networkNameOrId;
        }
        var baseNetwork = this.networkUrl.endsWith("/v2.0") ? this.networkUrl : this.networkUrl + "/v2.0";

        var uriByName = baseNetwork + "/networks?name=" + URLEncoder.encode(networkNameOrId, StandardCharsets.UTF_8);
        var reqByName = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(uriByName))
            .addHeader("X-Auth-Token", this.token)
            .addHeader("Accept", "application/json")
            .build();

        var respByName = executeHttp(reqByName, String.class);
        var resByName = parseResponse(respByName, NetworkResponses.MultipleNetworks.class, "resolve network by name");
        var networksByName = resByName != null && resByName.getNetworks() != null ? resByName.getNetworks() : Collections.<NetworkResponses.Network>emptyList();

        if (networksByName.size() == 1) {
            return networksByName.getFirst().getId();
        }
        if (networksByName.size() > 1) {
            throw new IllegalArgumentException("Ambiguous OpenStack network: '" + networkNameOrId + "' matches "
                + networksByName.size() + " networks. Please specify the exact network UUID.");
        }

        var uriById = baseNetwork + "/networks?id=" + URLEncoder.encode(networkNameOrId, StandardCharsets.UTF_8);
        var reqById = HttpRequest.builder()
            .method("GET")
            .uri(URI.create(uriById))
            .addHeader("X-Auth-Token", this.token)
            .addHeader("Accept", "application/json")
            .build();

        var respById = executeHttp(reqById, String.class);
        var resById = parseResponse(respById, NetworkResponses.MultipleNetworks.class, "resolve network by id");
        var networksById = resById != null && resById.getNetworks() != null ? resById.getNetworks() : Collections.<NetworkResponses.Network>emptyList();

        if (!networksById.isEmpty()) {
            return networksById.getFirst().getId();
        }

        throw new IllegalArgumentException("OpenStack network not found: '" + networkNameOrId + "'");
    }

    @FunctionalInterface
    private interface ConditionSupplier<T> {
        T get() throws Exception;
    }

    private <T> T pollUntil(Duration timeout, String timeoutMessage, ConditionSupplier<T> supplier) throws Exception {
        var start = Instant.now();
        var limit = timeout != null ? timeout : Duration.ofMinutes(10);
        long sleepMs = 1000;

        while (Duration.between(start, Instant.now()).compareTo(limit) < 0) {
            var result = supplier.get();
            if (result != null) {
                return result;
            }
            try {
                Thread.sleep(sleepMs);
                sleepMs = Math.min(sleepMs + 1000, 5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while polling", e);
            }
        }
        throw new IllegalStateException(timeoutMessage + " (limit: " + limit + ").");
    }

    public GlanceResponses.GlanceImage waitForImageActive(String imageId, Duration timeout) throws Exception {
        return pollUntil(timeout, "Timed out waiting for snapshot image '" + imageId + "' to become active", () -> {
            var img = getImage(imageId);
            var status = img != null ? img.getStatus() : null;
            if ("active".equalsIgnoreCase(status)) {
                return img;
            }
            if ("killed".equalsIgnoreCase(status) || "deleted".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Snapshot image '" + imageId + "' entered failure status: " + status);
            }
            return null;
        });
    }

    public Server waitForServerStatus(String serverId, String targetStatus, Duration timeout) throws Exception {
        return pollUntil(timeout, "Timed out waiting for server '" + serverId + "' to reach status '" + targetStatus + "'", () -> {
            var srv = getServer(serverId);
            var status = srv.getStatus();
            if (targetStatus.equalsIgnoreCase(status)) {
                return srv;
            }
            if ("ERROR".equalsIgnoreCase(status)) {
                throw new IllegalStateException("Server '" + serverId + "' entered ERROR status.");
            }
            return null;
        });
    }

    public void waitForServerDeletion(String serverId, Duration timeout) throws Exception {
        pollUntil(timeout, "Timed out waiting for server '" + serverId + "' to be deleted", () -> {
            try {
                var srv = getServer(serverId);
                if (srv == null || "DELETED".equalsIgnoreCase(srv.getStatus())) {
                    return Boolean.TRUE;
                }
            } catch (IllegalArgumentException e) {
                if (e.getMessage() != null && e.getMessage().contains("HTTP 404")) {
                    return Boolean.TRUE;
                }
                throw e;
            }
            return null;
        });
    }

    public List<InstanceActionResponses.InstanceAction> listInstanceActions(String serverId, String changesSince) throws Exception {
        authenticate();
        var uriBuilder = new StringBuilder(this.computeUrl).append("/servers/").append(pathSegment(serverId)).append("/os-instance-actions");
        if (StringUtils.isNotBlank(changesSince)) {
            uriBuilder.append("?changes-since=").append(URLEncoder.encode(changesSince, StandardCharsets.UTF_8));
        }
        var req = computeRequest("GET", uriBuilder.toString(), null);
        var resp = executeHttp(req, String.class);
        var res = parseResponse(resp, InstanceActionResponses.MultipleActions.class, "list instance actions");
        return res != null && res.getInstanceActions() != null ? res.getInstanceActions() : Collections.emptyList();
    }

    private HttpRequest computeRequest(String method, String uri, String body) {
        var builder = HttpRequest.builder()
            .method(method)
            .uri(URI.create(uri))
            .addHeader("X-Auth-Token", this.token)
            .addHeader("OpenStack-API-Version", "compute " + NOVA_MICROVERSION)
            .addHeader("Accept", "application/json");

        if (body != null && !body.isBlank() && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
            builder.addHeader("Content-Type", "application/json");
            builder.body(HttpRequest.StringRequestBody.builder()
                .content(body)
                .contentType("application/json")
                .charset(StandardCharsets.UTF_8)
                .build());
        }

        return builder.build();
    }

    private <T> HttpResponse<T> executeHttp(HttpRequest request, Class<T> responseType) throws Exception {
        try {
            return httpClient.request(request, responseType);
        } catch (HttpClientResponseException e) {
            var response = e.getResponse();
            var statusCode = response != null && response.getStatus() != null ? response.getStatus().getCode() : 0;
            var errorBody = response != null && response.getBody() != null ? response.getBody().toString() : "";

            if (statusCode == 401 || statusCode == 403) {
                throw new IllegalStateException("OpenStack authentication/authorization failed (HTTP " + statusCode
                    + "). Please verify Keystone credentials, project scope, user domain, and role permissions. Response: " + errorBody, e);
            } else if (statusCode == 404) {
                throw new IllegalArgumentException("OpenStack resource not found (HTTP 404). Please verify that the specified resource ID or endpoint exists. Response: " + errorBody, e);
            } else if (statusCode == 406) {
                throw new IllegalStateException("OpenStack compute microversion rejected (HTTP 406). Ensure Nova supports API microversion "
                    + NOVA_MICROVERSION + ". Response: " + errorBody, e);
            } else if (statusCode == 409) {
                throw new IllegalStateException("OpenStack conflict (HTTP 409). The resource is in an incompatible state for this operation. Response: " + errorBody, e);
            }

            throw new RuntimeException("OpenStack HTTP error (HTTP " + statusCode + "): " + e.getMessage()
                + (errorBody.isEmpty() ? "" : " | Response Body: " + errorBody), e);
        }
    }

    @Override
    public void close() {
        if (this.httpClient != null) {
            try {
                this.httpClient.close();
            } catch (Exception ignored) {
            }
        }
    }
}
