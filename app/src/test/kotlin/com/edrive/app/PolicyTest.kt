package com.edrive.app

import com.edrive.app.data.AccountException
import com.edrive.app.data.PinPolicy
import com.edrive.app.data.SecurityKeyPolicy
import com.edrive.app.data.SecurityKeyPolicy.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test fun trivialPins() {
        listOf("0000", "7777", "1234", "6789", "9876", "3210", "1212", "2580").forEach {
            assertTrue(it, PinPolicy.isTrivial(it.toCharArray()))
        }
        listOf("4826", "5937", "1397", "8402").forEach { assertFalse(it, PinPolicy.isTrivial(it.toCharArray())) }
    }

    @Test fun pinFormat() {
        listOf("482", "48261", "48a6", "").forEach {
            assertThrows(it, AccountException::class.java) { PinPolicy.validate(it.toCharArray()) }
        }
        PinPolicy.validate("4826".toCharArray())
    }

    @Test fun lockoutSteps() {
        assertEquals(0, PinPolicy.lockoutMillis(1))
        assertEquals(0, PinPolicy.lockoutMillis(4))
        assertEquals(60_000, PinPolicy.lockoutMillis(5))
        assertEquals(0, PinPolicy.lockoutMillis(6))
        assertEquals(5 * 60_000, PinPolicy.lockoutMillis(10))
        assertEquals(15 * 60_000, PinPolicy.lockoutMillis(15))
        assertEquals(60 * 60_000, PinPolicy.lockoutMillis(20))
        assertEquals("1 saatdan artıq olmur", 60 * 60_000, PinPolicy.lockoutMillis(100))
    }

    @Test fun securityKey() {
        assertThrows(AccountException::class.java) { SecurityKeyPolicy.validate("qisa-acar".toCharArray(), "qisa-acar".toCharArray()) }
        assertThrows(AccountException::class.java) { SecurityKeyPolicy.validate("uzun-təhlükəsizlik".toCharArray(), "uzun-təhlükəsizliK".toCharArray()) }
        SecurityKeyPolicy.validate("uzun-təhlükəsizlik".toCharArray(), "uzun-təhlükəsizlik".toCharArray())

        assertEquals(Strength.WEAK, SecurityKeyPolicy.strength("short".toCharArray()))
        assertEquals("təkrarlanan simvollar", Strength.WEAK, SecurityKeyPolicy.strength("aaaaaaaaaaaaaaaa".toCharArray()))
        assertEquals(Strength.WEAK, SecurityKeyPolicy.strength("abcdefghijkl".toCharArray()))
        assertEquals(Strength.FAIR, SecurityKeyPolicy.strength("abcdefgh1234".toCharArray()))
        assertEquals(Strength.STRONG, SecurityKeyPolicy.strength("Deniz-Kenari-2026".toCharArray()))
        assertEquals("uzun ifadə", Strength.STRONG, SecurityKeyPolicy.strength("dəniz kənarında dörd ağac".toCharArray()))
    }
}
