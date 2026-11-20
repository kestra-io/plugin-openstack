package io.kestra.plugin.openstack.client;

import io.kestra.plugin.openstack.client.model.KeystoneAuthRequest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeRegexTest {

    @Test
    void acceptsRegularPatterns() {
        assertTrue(SafeRegex.matches(SafeRegex.compile("prod-.*"), "prod-app-01"));
        assertFalse(SafeRegex.matches(SafeRegex.compile("prod-[a-z]+-\\d{2}"), "stage-app-01"));
        assertTrue(SafeRegex.matches(SafeRegex.compile("(prod|stage)-app-\\d+"), "stage-app-7"));
    }

    @Test
    void rejectsOverlongPattern() {
        assertThrows(IllegalArgumentException.class, () -> SafeRegex.compile("a".repeat(SafeRegex.MAX_PATTERN_LENGTH + 1)));
    }

    @Test
    void rejectsNestedQuantifiers() {
        assertThrows(IllegalArgumentException.class, () -> SafeRegex.compile("(a+)+$"));
        assertThrows(IllegalArgumentException.class, () -> SafeRegex.compile("(a*)*"));
        assertThrows(IllegalArgumentException.class, () -> SafeRegex.compile("(x(a+))+"));
        assertThrows(IllegalArgumentException.class, () -> SafeRegex.compile("(a{1,5})+"));
    }

    @Test
    void reportsSyntaxErrorsClearly() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SafeRegex.compile("[abc"));
        assertThat(e.getMessage(), containsString("Invalid name regex"));
    }

    @Test
    void credentialsAreNotPrintedByToString() {
        KeystoneAuthRequest.User user = KeystoneAuthRequest.User.builder().name("u").password("pw-value-1").build();
        KeystoneAuthRequest.ApplicationCredential cred = KeystoneAuthRequest.ApplicationCredential.builder().id("i").secret("secret-value-2").build();
        assertThat(user.toString(), not(containsString("pw-value-1")));
        assertThat(cred.toString(), not(containsString("secret-value-2")));
    }
}
