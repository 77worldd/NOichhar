package com.noichhar.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainValidatorTest {

    @Test
    fun normalizesExactAndWildcardRules() {
        val exact = DomainValidator.normalize(" Ads.Example.COM. ")
        val wildcard = DomainValidator.normalize("*.Tracker.Example.com")
        assertEquals("ads.example.com", exact?.domain)
        assertFalse(exact!!.wildcard)
        assertEquals("tracker.example.com", wildcard?.domain)
        assertTrue(wildcard!!.wildcard)
    }

    @Test
    fun rejectsInvalidRules() {
        assertNull(DomainValidator.normalize("https://example.com"))
        assertNull(DomainValidator.normalize("singlelabel"))
        assertNull(DomainValidator.normalize("bad domain.com"))
        assertNull(DomainValidator.normalize("*.com"))
    }
}
