package dev.goor.tv.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM encryption backed by a non-exportable Android Keystore key (A3.1, Path B).
 *
 * The key is hardware-backed where available and never leaves the TEE, so the
 * `sources` DB file is useless to an attacker who extracts it (rooted / forensic).
 * The key is NOT user-authentication-bound, so it survives lock-screen changes;
 * credentials are still lost when the user clears app data (the key is wiped with
 * it) — the same as the previous plaintext behaviour.
 *
 * Storage format: `v1:` + Base64(iv ‖ ciphertext+tag). Values without the prefix
 * are treated as legacy plaintext and returned as-is, so existing rows keep
 * working and get encrypted lazily on their next write — no DB migration needed.
 */
class KeystoreCredentialCipher : CredentialCipher {

    override fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
    }

    override fun decrypt(stored: String): String {
        if (!stored.startsWith(PREFIX)) return stored // legacy plaintext, written before encryption
        return runCatching {
            val raw = Base64.decode(stored.substring(PREFIX.length), Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, IV_LENGTH)
            val ciphertext = raw.copyOfRange(IV_LENGTH, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            }
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrElse {
            // Key lost/rotated or data corrupt. Don't crash the Room read; treat the
            // credential as missing so the user is prompted to re-enter it.
            Log.w(TAG, "Failed to decrypt credential; treating as empty", it)
            ""
        }
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val TAG = "CredentialCipher"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "goortv_credential_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFIX = "v1:"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
