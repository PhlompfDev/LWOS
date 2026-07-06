package com.lwos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LwosAccessTest {

    @Test
    void authorizedNamesAreAllowed() {
        assertTrue(LwosAccess.isAllowed("Plomph"), "Plomph is allowed");
        assertTrue(LwosAccess.isAllowed("Dev"), "Dev is allowed");
    }

    @Test
    void everyoneElseIsRejected() {
        assertFalse(LwosAccess.isAllowed("plomph"), "the check is case-sensitive");
        assertFalse(LwosAccess.isAllowed("dev"), "the check is case-sensitive");
        assertFalse(LwosAccess.isAllowed("Notch"));
        assertFalse(LwosAccess.isAllowed(""));
        assertFalse(LwosAccess.isAllowed(null));
    }
}
