package dev.goor.tv.data.db

import dev.goor.tv.data.crypto.CredentialCipher
import dev.goor.tv.data.model.Secret
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies the Room converter contract with a fake cipher (the real Keystore
 * cipher is exercised in androidTest). The fake mimics the prefix + legacy
 * passthrough behaviour so we cover the converter's null handling and the
 * encrypt-then-decrypt round trip without a device.
 */
class SecretConverterTest {

    /** Reversible stand-in for KeystoreCredentialCipher: "enc:" prefix, plaintext passthrough. */
    private val fakeCipher = object : CredentialCipher {
        override fun encrypt(plaintext: String) = "enc:$plaintext"
        override fun decrypt(stored: String) = stored.removePrefix("enc:")
    }
    private val converter = SecretConverter(fakeCipher)

    @Test
    fun `null secret maps to null column`() {
        assertNull(converter.fromSecret(null))
    }

    @Test
    fun `null column maps to null secret`() {
        assertNull(converter.toSecret(null))
    }

    @Test
    fun `secret is encrypted on write`() {
        assertEquals("enc:hunter2", converter.fromSecret(Secret("hunter2")))
    }

    @Test
    fun `column is decrypted on read`() {
        assertEquals(Secret("hunter2"), converter.toSecret("enc:hunter2"))
    }

    @Test
    fun `legacy plaintext column passes through on read`() {
        // Rows written before encryption have no prefix; the cipher returns them as-is.
        assertEquals(Secret("legacy-password"), converter.toSecret("legacy-password"))
    }

    @Test
    fun `write then read round-trips`() {
        val original = Secret("p@ss:word/with?specials")
        val stored = converter.fromSecret(original)
        assertEquals(original, converter.toSecret(stored))
    }

    @Test
    fun `toString is redacted so credentials never leak`() {
        assertEquals("Secret(••••••)", Secret("supersecret").toString())
    }
}
