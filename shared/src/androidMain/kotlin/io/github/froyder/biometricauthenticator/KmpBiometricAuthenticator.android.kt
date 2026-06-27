package io.github.froyder.biometricauthenticator

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.coroutines.resume

private const val KEY_NAME = "biometric_demo_key"
private const val KEYSTORE_PROVIDER = "AndroidKeyStore"

actual class KmpBiometricAuthenticator(
    private val activity: FragmentActivity
) {
    actual suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelButtonText: String
    ): BiometricResult {
        val canAuth = BiometricManager.from(activity)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricResult.NotAvailable
        }

        val cipher = try {
            getCipher()
        } catch (e: KeyPermanentlyInvalidatedException) {
            return BiometricResult.Error("Biometric settings changed — please log in again")
        } catch (e: Exception) {
            return BiometricResult.Error("Key setup failed: ${e.message}")
        }

        return suspendCancellableCoroutine { continuation ->
            val executor = ContextCompat.getMainExecutor(activity)

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (!continuation.isActive) return

                    val unlockedCipher = result.cryptoObject?.cipher
                    if (unlockedCipher == null) {
                        continuation.resume(BiometricResult.Error("No cipher returned"))
                        return
                    }

                    try {
                        val encrypted = unlockedCipher.doFinal("verified".toByteArray())
                        val hex = encrypted.take(8).joinToString("") {
                            (it.toInt() and 0xFF).toString(16).padStart(2, '0')
                        }
                        android.util.Log.d("KmpBiometricAuth", "Keystore cipher verified — ${encrypted.size} bytes, iv: $hex")
                        continuation.resume(BiometricResult.Success)
                    } catch (e: Exception) {
                        continuation.resume(BiometricResult.Error("Cipher unusable: ${e.message}"))
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (!continuation.isActive) return
                    val result = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED -> BiometricResult.Cancelled
                        else -> BiometricResult.Error(errString.toString())
                    }
                    continuation.resume(result)
                }
            }

            BiometricPrompt(activity, executor, callback).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText(cancelButtonText)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .build(),
                BiometricPrompt.CryptoObject(cipher)
            )
        }
    }

    private fun getCipher(): Cipher =
        try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            }
        } catch (e: KeyPermanentlyInvalidatedException) {
            KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }.deleteEntry(KEY_NAME)
            throw e
        }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_NAME, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER
        )
        val builder = KeyGenParameterSpec.Builder(
            KEY_NAME,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }
}