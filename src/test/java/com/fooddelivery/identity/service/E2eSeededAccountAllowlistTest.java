package com.fooddelivery.identity.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class E2eSeededAccountAllowlistTest {

    private final E2eSeededAccountAllowlist allowlist = new E2eSeededAccountAllowlist();

    @Test
    void acceptsOnlyTheConfiguredSeedRangesForTheirPortal() {
        assertTrue(allowlist.allows("8000000001", AuthPortal.CUSTOMER));
        assertTrue(allowlist.allows("8000000500", AuthPortal.CUSTOMER));
        assertTrue(allowlist.allows("9000000001", AuthPortal.RESTAURANT));
        assertTrue(allowlist.allows("9000000010", AuthPortal.RESTAURANT));
        assertTrue(allowlist.allows("7000000001", AuthPortal.DELIVERY));
        assertTrue(allowlist.allows("7000000030", AuthPortal.DELIVERY));
        assertTrue(allowlist.allows("1000000001", AuthPortal.ADMIN));

        assertFalse(allowlist.allows("8000000505", AuthPortal.CUSTOMER));
        assertFalse(allowlist.allows("9000000015", AuthPortal.RESTAURANT));
        assertFalse(allowlist.allows("7000000035", AuthPortal.DELIVERY));
        assertFalse(allowlist.allows("1000000003", AuthPortal.ADMIN));
        assertFalse(allowlist.allows("8000000001", AuthPortal.ADMIN));
    }
}
