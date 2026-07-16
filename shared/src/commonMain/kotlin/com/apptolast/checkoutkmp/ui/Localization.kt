package com.apptolast.checkoutkmp.ui

import androidx.compose.runtime.staticCompositionLocalOf

/** Two-letter language code of the device (e.g. `en`, `es`), provided per platform. */
expect fun deviceLanguageCode(): String

/**
 * All user-facing copy in one place, one member per string. Plain [String] properties for static
 * copy; functions for parameterized copy (so a value is never interpolated into both languages by
 * hand). Kotlin-based i18n: the AGP 9 KMP-library plugin does not package Compose resources, so we
 * keep the catalog in common code. Add a language by adding one more [Strings] implementation.
 */
interface Strings {
    // Checkout screen
    val checkout: String
    val orderTotal: String
    val securePaymentDemo: String
    val testScenarioDemo: String
    val paymentMethod: String
    val creditDebitCard: String
    val processingPayment: String

    // Demo scenario chips
    val scenarioApproved: String
    val scenario3dSecure: String
    val scenarioDeclined: String
    val scenarioNetworkError: String
    val scenarioTimeout: String
    val scenarioRateLimited: String

    // Card form
    val cardNumber: String
    val expiryMmYy: String
    val cvv: String
    fun pay(amount: String): String

    // Card brand / masked card
    val card: String
    fun cardEndingIn(brand: String, last4: String): String
    fun brandCard(brand: String): String

    // Receipt
    val paymentApproved: String
    val authCode: String
    val paymentId: String
    val newPayment: String

    // Failure
    val paymentFailed: String
    val retry: String
    val startOver: String
    val errorDeclined: String
    val errorInvalidCard: String
    val errorNetwork: String
    val errorTimeout: String
    val errorRateLimited: String
    val errorScaFailed: String
    val errorCancelled: String
    val errorUnknown: String

    // 3D Secure (SCA)
    val yourDevice: String
    val threeDSecureVerification: String
    fun enterCodeSentTo(otpLength: Int, target: String): String
    val demoCode: String
    val verificationCode: String
    val incorrectCode: String
    val verify: String
    val cancel: String
}

/** English copy (default). */
object EnStrings : Strings {
    override val checkout = "Checkout"
    override val orderTotal = "Order total"
    override val securePaymentDemo = "Secure payment · demo"
    override val testScenarioDemo = "Test scenario (demo)"
    override val paymentMethod = "Payment method"
    override val creditDebitCard = "Credit / debit card"
    override val processingPayment = "Processing payment…"

    override val scenarioApproved = "Approved"
    override val scenario3dSecure = "3D Secure"
    override val scenarioDeclined = "Declined"
    override val scenarioNetworkError = "Network error"
    override val scenarioTimeout = "Timeout"
    override val scenarioRateLimited = "Rate limited"

    override val cardNumber = "Card number"
    override val expiryMmYy = "MM/YY"
    override val cvv = "CVV"
    override fun pay(amount: String) = "Pay $amount"

    override val card = "Card"
    override fun cardEndingIn(brand: String, last4: String) = "$brand, card ending in $last4"
    override fun brandCard(brand: String) = "$brand card"

    override val paymentApproved = "Payment approved"
    override val authCode = "Auth code"
    override val paymentId = "Payment id"
    override val newPayment = "New payment"

    override val paymentFailed = "Payment failed"
    override val retry = "Retry"
    override val startOver = "Start over"
    override val errorDeclined = "Your card was declined. Please try another card."
    override val errorInvalidCard = "Card details are invalid. Please check and try again."
    override val errorNetwork = "Network problem. Please try again."
    override val errorTimeout = "The request timed out. Please try again."
    override val errorRateLimited = "Too many attempts. Please wait a moment."
    override val errorScaFailed = "Authentication failed. Please try again."
    override val errorCancelled = "Payment cancelled."
    override val errorUnknown = "Something went wrong. Please try again."

    override val yourDevice = "your device"
    override val threeDSecureVerification = "3D Secure verification"
    override fun enterCodeSentTo(otpLength: Int, target: String) =
        "Enter the $otpLength-digit code sent to $target."
    override val demoCode = "Demo code"
    override val verificationCode = "Verification code"
    override val incorrectCode = "Incorrect code, try again."
    override val verify = "Verify"
    override val cancel = "Cancel"
}

/** Spanish copy. */
object EsStrings : Strings {
    override val checkout = "Pago"
    override val orderTotal = "Total del pedido"
    override val securePaymentDemo = "Pago seguro · demo"
    override val testScenarioDemo = "Escenario de prueba (demo)"
    override val paymentMethod = "Método de pago"
    override val creditDebitCard = "Tarjeta de crédito / débito"
    override val processingPayment = "Procesando pago…"

    override val scenarioApproved = "Aprobado"
    override val scenario3dSecure = "3D Secure"
    override val scenarioDeclined = "Rechazado"
    override val scenarioNetworkError = "Error de red"
    override val scenarioTimeout = "Tiempo agotado"
    override val scenarioRateLimited = "Límite de peticiones"

    override val cardNumber = "Número de tarjeta"
    override val expiryMmYy = "MM/AA"
    override val cvv = "CVV"
    override fun pay(amount: String) = "Pagar $amount"

    override val card = "Tarjeta"
    override fun cardEndingIn(brand: String, last4: String) = "$brand, tarjeta terminada en $last4"
    override fun brandCard(brand: String) = "Tarjeta $brand"

    override val paymentApproved = "Pago aprobado"
    override val authCode = "Código de autorización"
    override val paymentId = "ID de pago"
    override val newPayment = "Nuevo pago"

    override val paymentFailed = "Pago fallido"
    override val retry = "Reintentar"
    override val startOver = "Empezar de nuevo"
    override val errorDeclined = "Tu tarjeta fue rechazada. Prueba con otra tarjeta."
    override val errorInvalidCard = "Los datos de la tarjeta no son válidos. Revísalos e inténtalo de nuevo."
    override val errorNetwork = "Problema de red. Inténtalo de nuevo."
    override val errorTimeout = "La solicitud tardó demasiado. Inténtalo de nuevo."
    override val errorRateLimited = "Demasiados intentos. Espera un momento."
    override val errorScaFailed = "La autenticación falló. Inténtalo de nuevo."
    override val errorCancelled = "Pago cancelado."
    override val errorUnknown = "Algo salió mal. Inténtalo de nuevo."

    override val yourDevice = "tu dispositivo"
    override val threeDSecureVerification = "Verificación 3D Secure"
    override fun enterCodeSentTo(otpLength: Int, target: String) =
        "Introduce el código de $otpLength dígitos enviado a $target."
    override val demoCode = "Código demo"
    override val verificationCode = "Código de verificación"
    override val incorrectCode = "Código incorrecto, inténtalo de nuevo."
    override val verify = "Verificar"
    override val cancel = "Cancelar"
}

/** Resolve the [Strings] catalog for a device [languageCode] (Spanish for `es*`, English otherwise). */
fun stringsFor(languageCode: String): Strings =
    if (languageCode.lowercase().startsWith("es")) EsStrings else EnStrings

/**
 * The active string catalog, provided by [CheckoutTheme] from [deviceLanguageCode]. Composables read
 * `LocalStrings.current` instead of hardcoding copy. Defaults to [EnStrings] (e.g. in previews).
 */
val LocalStrings = staticCompositionLocalOf<Strings> { EnStrings }
