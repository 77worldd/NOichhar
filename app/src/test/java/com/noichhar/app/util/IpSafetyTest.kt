package com.noichhar.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IpSafetyTest {

    @Test
    fun allowsOnlyPublicNonDnsAddresses() {
        assertTrue(IpSafety.isSafeToBlock("93.184.216.34"))
        assertFalse(IpSafety.isSafeToBlock("1.1.1.1"))
        assertFalse(IpSafety.isSafeToBlock("8.8.8.8"))
        assertFalse(IpSafety.isSafeToBlock("10.0.0.5"))
        assertFalse(IpSafety.isSafeToBlock("192.168.1.20"))
        assertFalse(IpSafety.isSafeToBlock("127.0.0.1"))
        assertFalse(IpSafety.isSafeToBlock("224.0.0.1"))
        assertFalse(IpSafety.isSafeToBlock("not-an-ip"))
    }
}
