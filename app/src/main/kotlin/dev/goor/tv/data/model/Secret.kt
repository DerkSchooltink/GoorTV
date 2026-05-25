package dev.goor.tv.data.model

/**
 * A sensitive string (Xtream username / password) that is encrypted at rest.
 *
 * In memory it holds plaintext; Room persists it as Keystore-encrypted ciphertext
 * via `SecretConverter`. [toString] is redacted so credentials never leak into
 * logs, crash reports, or `Source.toString()`.
 */
data class Secret(val value: String) {
    override fun toString(): String = "Secret(••••••)"
}
