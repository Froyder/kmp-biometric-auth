package io.github.froyder.biometricauthenticator

import keychainhelper.KeychainBiometricHelper
import kotlinx.cinterop.ExperimentalForeignApi

private const val KEYCHAIN_SERVICE = "io.github.froyder.biometricauthenticator"
private const val KEYCHAIN_ACCOUNT = "io.github.froyder.biometric.key"

private const val STATUS_SUCCESS = 0L
private const val STATUS_CANCELLED = 1L
private const val STATUS_BIOMETRY_CHANGED = 2L
private const val STATUS_NOT_AVAILABLE = 3L

actual class KmpBiometricAuthenticator {
    @OptIn(ExperimentalForeignApi::class)
    actual suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelButtonText: String
    ): BiometricResult {
        val result = KeychainBiometricHelper.authenticateWithService(
            service = KEYCHAIN_SERVICE,
            account = KEYCHAIN_ACCOUNT,
            subtitle = subtitle.ifBlank { title }
        )

        return when (result.status) {
            STATUS_SUCCESS -> {
                println("KmpBiometricAuth: ${result.proofHex}")
                BiometricResult.Success
            }
            STATUS_CANCELLED -> BiometricResult.Cancelled
            STATUS_NOT_AVAILABLE -> BiometricResult.NotAvailable
            STATUS_BIOMETRY_CHANGED -> BiometricResult.Error(
                result.errorMessage ?: "Biometric settings changed"
            )
            else -> BiometricResult.Error(
                result.errorMessage ?: "Unknown error"
            )
        }
    }
}