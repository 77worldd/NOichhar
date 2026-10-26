package com.noichhar.app.util

import com.noichhar.app.util.Constants.DnsProtocol
import com.noichhar.app.util.Constants.DnsProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsEndpointValidatorTest {

    @Test
    fun validatesDohEndpoints() {
        assertTrue(DnsEndpointValidator.isValid(DnsProvider.CUSTOM, DnsProtocol.DOH, "https://dns.example/dns-query"))
        assertFalse(DnsEndpointValidator.isValid(DnsProvider.CUSTOM, DnsProtocol.DOH, "http://dns.example/dns-query"))
        assertFalse(DnsEndpointValidator.isValid(DnsProvider.CUSTOM, DnsProtocol.DOH, "not-a-url"))
    }

    @Test
    fun validatesDotAndPlainEndpoints() {
        assertTrue(DnsEndpointValidator.isValid(DnsProvider.CUSTOM, DnsProtocol.DOT, "tls://dns.example"))
        assertTrue(DnsEndpointValidator.isValid(DnsProvider.CUSTOM, DnsProtocol.DOT, "dns.example"))
        assertFalse(DnsEndpointValidator.isValid(DnsProvider.CUSTOM, DnsProtocol.DOT, "1.1.1.1"))
        assertTrue(DnsEndpointValidator.isValid(DnsProvider.CUSTOM, DnsProtocol.PLAIN, "1.1.1.1"))
        assertFalse(DnsEndpointValidator.isValid(DnsProvider.CUSTOM, DnsProtocol.PLAIN, "dns.example"))
    }

    @Test
    fun builtInProvidersDoNotNeedCustomEndpoint() {
        assertTrue(DnsEndpointValidator.isValid(DnsProvider.CLOUDFLARE, DnsProtocol.DOH, ""))
        assertTrue(DnsEndpointValidator.isValid(DnsProvider.SYSTEM, DnsProtocol.PLAIN, ""))
    }
}
