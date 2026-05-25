package dev.goor.tv.data.crypto

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Android Keystore cipher on-device (A3.1, Path B).
 */
@RunWith(AndroidJUnit4::class)
class KeystoreCredentialCipherTest {

    private val cipher = KeystoreCredentialCipher()

    @Test
    fun encryptThenDecrypt_roundTrips() {
        val plaintext = "p@ss:word/with?specials&more=1"
        assertEquals(plaintext, cipher.decrypt(cipher.encrypt(plaintext)))
    }

    @Test
    fun ciphertext_isNotPlaintext_andIsMarked() {
        val encrypted = cipher.encrypt("hunter2")
        assertNotEquals("hunter2", encrypted)
        assertTrue("expected a version marker", encrypted.startsWith("v1:"))
    }

    @Test
    fun encryption_isNonDeterministic() {
        // Random IV per call → same plaintext encrypts to different ciphertext.
        assertNotEquals(cipher.encrypt("same"), cipher.encrypt("same"))
    }

    @Test
    fun legacyPlaintext_passesThrough() {
        // A value with no marker is a pre-encryption row; returned unchanged.
        assertEquals("legacy-plaintext", cipher.decrypt("legacy-plaintext"))
    }

    @Test
    fun emptyString_roundTrips() {
        assertEquals("", cipher.decrypt(cipher.encrypt("")))
    }
}
