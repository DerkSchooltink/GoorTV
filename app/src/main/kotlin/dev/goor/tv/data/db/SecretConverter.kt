package dev.goor.tv.data.db

import androidx.room.ProvidedTypeConverter
import androidx.room.TypeConverter
import dev.goor.tv.data.crypto.CredentialCipher
import dev.goor.tv.data.model.Secret

/**
 * Maps [Secret] columns (Xtream username / password) to/from encrypted TEXT (A3.1).
 *
 * `@ProvidedTypeConverter` so the [CredentialCipher] can be injected (real Keystore
 * in production, a fake in tests) — Room is given the instance via
 * `addTypeConverter(...)` in the DI module. The SQLite affinity stays TEXT, so no
 * schema-version bump or migration is needed; only the column's value is encrypted.
 */
@ProvidedTypeConverter
class SecretConverter(private val cipher: CredentialCipher) {

    @TypeConverter
    fun fromSecret(secret: Secret?): String? = secret?.let { cipher.encrypt(it.value) }

    @TypeConverter
    fun toSecret(stored: String?): Secret? = stored?.let { Secret(cipher.decrypt(it)) }
}
