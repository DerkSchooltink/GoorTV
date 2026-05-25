package dev.goor.tv.data.crypto

/**
 * Symmetric encrypt/decrypt for credentials stored at rest (A3.1, Path B).
 *
 * Abstracted so the Room [dev.goor.tv.data.db.SecretConverter] can be unit-tested
 * with a fake, while production uses [KeystoreCredentialCipher] (Android Keystore).
 *
 * Implementations MUST tolerate legacy plaintext on [decrypt] (values written
 * before encryption was introduced) so no DB migration is required, and MUST NOT
 * throw on [decrypt] — it runs inside a Room type converter, so a throw would
 * crash every `sources` read.
 */
interface CredentialCipher {
    fun encrypt(plaintext: String): String
    fun decrypt(stored: String): String
}
