package com.lwos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LwosAccessTest {

    @Test
    void onlyTheAuthorizedNameIsAllowed() {
        assertTrue(LwosAccess.isAllowed("Plomph"), "the authorized builder is allowed");
        assertEquals("Plomph", LwosAccess.ALLOWED_NAME);
    }

    @Test
    void everyoneElseIsRejected() {
        assertFalse(LwosAccess.isAllowed("Dev"), "other names are rejected now (server-enforced)");
        assertFalse(LwosAccess.isAllowed("plomph"), "the check is case-sensitive");
        assertFalse(LwosAccess.isAllowed("Notch"));
        assertFalse(LwosAccess.isAllowed(""));
        assertFalse(LwosAccess.isAllowed(null));
    }
}
