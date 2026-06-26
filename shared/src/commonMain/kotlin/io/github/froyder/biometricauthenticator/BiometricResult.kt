package io.github.froyder.biometricauthenticator

sealed interface BiometricResult {
    data object Success : BiometricResult
    data object Cancelled : BiometricResult
    data object NotAvailable : BiometricResult
    data class Error(val message: String) : BiometricResult
}