package com.fooddelivery.identity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class E2eRunnerSecretVerifierTest {

    @Test
    void rejectsBlankConfiguredSecrets() {
        assertThrows(IllegalStateException.class, () -> new E2eRunnerSecretVerifier(" "));
    }

    @Test
    void acceptsOnlyOneExactRunnerSecret() {
        E2eRunnerSecretVerifier verifier = new E2eRunnerSecretVerifier("runner-secret");

        assertTrue(verifier.matches(List.of("runner-secret")));
        assertFalse(verifier.matches(List.of()));
        assertFalse(verifier.matches(List.of("wrong-secret")));
        assertFalse(verifier.matches(List.of("runner-secret", "runner-secret")));
    }
}
