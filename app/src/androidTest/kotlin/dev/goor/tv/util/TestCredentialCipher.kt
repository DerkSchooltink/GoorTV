package dev.goor.tv.util

import dev.goor.tv.data.crypto.CredentialCipher

/**
 * Identity cipher for DB tests that don't exercise credential encryption — keeps
 * the Room `@ProvidedTypeConverter` satisfied without touching the Android Keystore.
 * The real Keystore cipher is covered by `KeystoreCredentialCipherTest`.
 */
object TestCredentialCipher : CredentialCipher {
    override fun encrypt(plaintext: String): String = plaintext
    override fun decrypt(stored: String): String = stored
}
