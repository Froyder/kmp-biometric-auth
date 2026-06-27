package io.github.froyder.biometricauthenticator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class BiometricResultTest {

    // Sealed interface exhaustiveness
    @Test
    fun `Success is distinct from other results`() {
        val result: BiometricResult = BiometricResult.Success
        assertIs<BiometricResult.Success>(result)
    }

    @Test
    fun `Cancelled is distinct from other results`() {
        val result: BiometricResult = BiometricResult.Cancelled
        assertIs<BiometricResult.Cancelled>(result)
    }

    @Test
    fun `NotAvailable is distinct from other results`() {
        val result: BiometricResult = BiometricResult.NotAvailable
        assertIs<BiometricResult.NotAvailable>(result)
    }

    @Test
    fun `Error carries message`() {
        val message = "Something went wrong"
        val result: BiometricResult = BiometricResult.Error(message)
        assertIs<BiometricResult.Error>(result)
        assertEquals(message, result.message)
    }

    @Test
    fun `Error with different messages are not equal`() {
        val result1 = BiometricResult.Error("error one")
        val result2 = BiometricResult.Error("error two")
        assertNotEquals(result1, result2)
    }

    @Test
    fun `Error with same message are equal`() {
        val result1 = BiometricResult.Error("same error")
        val result2 = BiometricResult.Error("same error")
        assertEquals(result1, result2)
    }

    // when exhaustiveness — compiler would catch this but good to document intent
    @Test
    fun `when on BiometricResult covers all cases`() {
        val results = listOf(
            BiometricResult.Success,
            BiometricResult.Cancelled,
            BiometricResult.NotAvailable,
            BiometricResult.Error("test")
        )

        results.forEach { result ->
            val handled = when (result) {
                BiometricResult.Success -> "success"
                BiometricResult.Cancelled -> "cancelled"
                BiometricResult.NotAvailable -> "not_available"
                is BiometricResult.Error -> "error"
            }
            assertNotEquals("", handled)
        }
    }
}