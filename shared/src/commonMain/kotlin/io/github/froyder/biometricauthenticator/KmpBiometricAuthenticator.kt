package io.github.froyder.biometricauthenticator

expect class KmpBiometricAuthenticator {
    suspend fun authenticate(
        title: String,
        subtitle: String,
        cancelButtonText: String = "Cancel"
    ): BiometricResult
}