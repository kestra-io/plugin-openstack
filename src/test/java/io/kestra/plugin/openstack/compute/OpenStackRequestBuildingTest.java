package io.kestra.plugin.openstack.compute;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.plugin.openstack.client.OpenStackClient;
import io.kestra.plugin.openstack.client.model.ServerPayloads;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class OpenStackRequestBuildingTest {

    @Inject
    private RunContextFactory runContextFactory;

    private static WireMockServer wireMockServer;
    private static String authUrl;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        authUrl = "http://localhost:" + wireMockServer.port() + "/v3";
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @BeforeEach
    void setupStubs() {
        wireMockServer.resetAll();
        var tokenBody = String.format("""
            {"token": {"catalog": [
              {"type": "compute", "name": "nova", "endpoints": [
                {"interface": "public", "region": "RegionOne", "url": "http://localhost:%d/compute/v2.1"}]}
            ]}}
            """, wireMockServer.port());
        wireMockServer.stubFor(post(urlEqualTo("/v3/auth/tokens"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("X-Subject-Token", "tok")
                .withBody(tokenBody)));
    }

    @Test
    void rebuildBodyEscapesImageRef() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/compute/v2.1/servers/srv-01/action"))
            .withRequestBody(equalToJson("{\"rebuild\": {\"imageRef\": \"img\\\",\\\"adminPass\\\":\\\"x\"}}"))
            .willReturn(aResponse().withStatus(202)));

        RestoreServerFromSnapshot.builder()
            .authUrl(Property.ofValue(authUrl))
            .applicationCredentialId(Property.ofValue("id"))
            .applicationCredentialSecret(Property.ofValue("secret"))
            .serverId(Property.ofValue("srv-01"))
            .imageId(Property.ofValue("img\",\"adminPass\":\"x"))
            .build()
            .run(runContextFactory.of());
    }

    @Test
    void invalidRebootTypeIsRejected() {
        RebootServer task = RebootServer.builder()
            .authUrl(Property.ofValue(authUrl))
            .applicationCredentialId(Property.ofValue("id"))
            .applicationCredentialSecret(Property.ofValue("secret"))
            .serverId(Property.ofValue("srv-01"))
            .rebootType(Property.ofExpression("{{ 'SOFT\"}, \"os-stop\": {\"' }}"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContextFactory.of()));
        wireMockServer.verify(0, postRequestedFor(urlPathMatching("/compute/v2.1/servers/.*/action")));
    }

    @Test
    void serverIdIsEncodedInPath() throws Exception {
        wireMockServer.stubFor(delete(urlEqualTo("/compute/v2.1/servers/a%2Fb%3Fc"))
            .willReturn(aResponse().withStatus(204)));

        DeleteServer.builder()
            .authUrl(Property.ofValue(authUrl))
            .applicationCredentialId(Property.ofValue("id"))
            .applicationCredentialSecret(Property.ofValue("secret"))
            .serverId(Property.ofValue("a/b?c"))
            .build()
            .run(runContextFactory.of());

        wireMockServer.verify(1, deleteRequestedFor(urlEqualTo("/compute/v2.1/servers/a%2Fb%3Fc")));
    }

    @Test
    void dotSegmentServerIdIsRejected() {
        DeleteServer task = DeleteServer.builder()
            .authUrl(Property.ofValue(authUrl))
            .applicationCredentialId(Property.ofValue("id"))
            .applicationCredentialSecret(Property.ofValue("secret"))
            .serverId(Property.ofValue(".."))
            .build();

        assertThrows(Exception.class, () -> task.run(runContextFactory.of()));
    }

    @Test
    void unsafeNameRegexIsRejectedByListServers() {
        wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/detail"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"servers\": [{\"id\": \"1\", \"name\": \"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!\"}]}")));

        ListServers task = ListServers.builder()
            .authUrl(Property.ofValue(authUrl))
            .applicationCredentialId(Property.ofValue("id"))
            .applicationCredentialSecret(Property.ofValue("secret"))
            .nameRegex(Property.ofValue("(a+)+$"))
            .build();

        assertThrows(Exception.class, () -> task.run(runContextFactory.of()));
    }

    private void stubImageCatalog() {
        var tokenBody = String.format("""
            {"token": {"catalog": [
              {"type": "compute", "name": "nova", "endpoints": [
                {"interface": "public", "region": "RegionOne", "url": "http://localhost:%d/compute/v2.1"}]},
              {"type": "image", "name": "glance", "endpoints": [
                {"interface": "public", "region": "RegionOne", "url": "http://localhost:%d/image"}]}
            ]}}
            """, wireMockServer.port(), wireMockServer.port());
        wireMockServer.stubFor(post(urlEqualTo("/v3/auth/tokens"))
            .willReturn(aResponse().withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withHeader("X-Subject-Token", "tok")
                .withBody(tokenBody)));
    }

    private OpenStackClient client() throws Exception {
        return OpenStackClient.builder()
            .runContext(runContextFactory.of())
            .authUrl(authUrl)
            .applicationCredentialId("id")
            .applicationCredentialSecret("secret")
            .build();
    }

    @Test
    void imageIdIsEncodedInPath() throws Exception {
        stubImageCatalog();
        wireMockServer.stubFor(get(urlEqualTo("/image/v2/images/a%2F..%2Fb%3Fc"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"id\": \"resolved\"}")));

        try (var client = client()) {
            client.getImage("a/../b?c");
        }

        wireMockServer.verify(1, getRequestedFor(urlEqualTo("/image/v2/images/a%2F..%2Fb%3Fc")));
    }

    @Test
    void dotSegmentImageIdIsRejected() throws Exception {
        stubImageCatalog();
        try (var client = client()) {
            assertThrows(IllegalArgumentException.class, () -> client.getImage(".."));
        }
    }

    @Test
    void instanceActionsServerIdIsEncodedInPath() throws Exception {
        wireMockServer.stubFor(get(urlPathEqualTo("/compute/v2.1/servers/a%2Fb%3Fc/os-instance-actions"))
            .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                .withBody("{\"instanceActions\": []}")));

        try (var client = client()) {
            client.listInstanceActions("a/b?c", null);
        }

        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/compute/v2.1/servers/a%2Fb%3Fc/os-instance-actions")));
    }

    @Test
    void resizeBodyEscapesFlavorId() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/compute/v2.1/servers/srv-01/action"))
            .withRequestBody(equalToJson("{\"resize\": {\"flavorRef\": \"f\\\",\\\"x\\\":\\\"y\"}}"))
            .willReturn(aResponse().withStatus(202)));

        try (var client = client()) {
            client.resizeServer("srv-01", "f\",\"x\":\"y");
        }

        wireMockServer.verify(1, postRequestedFor(urlEqualTo("/compute/v2.1/servers/srv-01/action")));
    }

    @Test
    void toStringDoesNotContainCredentials() {
        var trigger = Trigger.builder()
            .applicationCredentialSecret(Property.ofValue("app-secret-value"))
            .password(Property.ofValue("pw-secret-value"))
            .build();
        assertFalse(trigger.toString().contains("app-secret-value"));
        assertFalse(trigger.toString().contains("pw-secret-value"));

        var data = ServerPayloads.ServerData.builder().name("n").adminPass("admin-pass-value").build();
        assertFalse(data.toString().contains("admin-pass-value"));
    }
}
