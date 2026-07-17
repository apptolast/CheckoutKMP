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
    val checkCardNumber: String
    val cardExpired: String
    val checkExpiry: String
    val checkCvv: String
    fun pay(amount: String): String

    // Card brand / masked card
    val card: String
    fun cardEndingIn(brand: String, last4: String): String
    fun brandCard(brand: String): String

    // Redirect methods (PayPal / Bizum)
    fun continueAt(provider: String): String
    val redirectWebhookNote: String
    val approveSimulated: String
    val simulateProviderFailure: String
    val confirmingWithProvider: String
    fun payWith(provider: String, amount: String): String
    fun simulatedProviderPageLink(provider: String): String

    // After-sales eligibility (per payment method)
    val sizeChange: String
    val refundToOrigin: String
    val available: String
    val notAvailable: String

    // Gift card (split payment)
    val giftCard: String
    val giftCardCode: String
    val apply: String
    val remove: String
    val giftCardNotFound: String
    fun giftCardApplied(amount: String): String
    fun remainingToPay(amount: String): String
    val giftCardCoversTotal: String
    fun payWithGiftCard(amount: String): String
    fun demoGiftCards(codes: String): String
    fun removeGiftCard(code: String): String
    fun giftCardNotUsedWith(provider: String): String

    /** The typographic minus (U+2212) prefixed to a gift-card tender on the receipt. */
    val minusSign: String

    /** Spoken form of a negative tender amount — screen readers don't read [minusSign] reliably. */
    fun minusAmount(amount: String): String

    // Receipt (authorization vs capture lifecycle)
    val paymentAuthorized: String
    val authorizedChargeNote: String
    val paymentCaptured: String
    val paymentRefunded: String
    val refundedNote: String
    val simulateDispatch: String
    val refund: String
    val capturingPayment: String
    val refundingPayment: String
    val paymentVoided: String
    val voidedNote: String
    val voidOrder: String
    val voidingPayment: String
    val authCode: String
    val paymentId: String
    val date: String

    /** Month abbreviations, January first, used to format the receipt date. */
    val monthAbbreviations: List<String>
    val newPayment: String

    // Order history
    val orderHistory: String
    val orderDetail: String
    val timeline: String
    val back: String
    val emptyHistory: String
    val statusAuthorized: String
    val statusCaptured: String
    val statusRefunded: String
    val statusVoided: String

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
    override val checkCardNumber = "Check the card number"
    override val cardExpired = "This card has expired"
    override val checkExpiry = "Enter a valid date (MM/YY)"
    override val checkCvv = "Check the CVV"
    override fun pay(amount: String) = "Pay $amount"

    override val card = "Card"
    override fun cardEndingIn(brand: String, last4: String) = "$brand, card ending in $last4"
    override fun brandCard(brand: String) = "$brand card"

    override fun continueAt(provider: String) = "Continue at $provider"
    override val redirectWebhookNote =
        "You'll approve the payment on the provider's secure page. The final outcome is what the " +
            "provider confirms to our payment gateway (webhook) — not your return to the app."
    override val approveSimulated = "Approve payment (simulated)"
    override val simulateProviderFailure = "Simulate provider failure"
    override val confirmingWithProvider = "Confirming with the provider…"
    override fun payWith(provider: String, amount: String) = "Pay $amount with $provider"
    override fun simulatedProviderPageLink(provider: String) =
        "Link to the simulated $provider approval page"

    override val sizeChange = "Size change"
    override val refundToOrigin = "Refund to original method"
    override val available = "Available"
    override val notAvailable = "Not available"

    override val giftCard = "Gift card"
    override val giftCardCode = "Gift card code"
    override val apply = "Apply"
    override val remove = "Remove"
    override val giftCardNotFound = "Gift card not found. Check the code and try again."
    override fun giftCardApplied(amount: String) = "Applied: −$amount"
    override fun remainingToPay(amount: String) = "Remaining to pay: $amount"
    override val giftCardCoversTotal = "The gift card covers the total — no card or 3D Secure needed."
    override fun payWithGiftCard(amount: String) = "Pay $amount with gift card"
    override fun demoGiftCards(codes: String) = "Demo codes: $codes"
    override fun removeGiftCard(code: String) = "Remove gift card $code"
    override fun giftCardNotUsedWith(provider: String) =
        "The gift card is not used with $provider — the full total will be charged."
    override val minusSign = "−"
    override fun minusAmount(amount: String) = "minus $amount"

    override val paymentAuthorized = "Payment authorized"
    override val authorizedChargeNote = "You'll be charged when the order is dispatched."
    override val paymentCaptured = "Payment charged"
    override val paymentRefunded = "Payment refunded"
    override val refundedNote = "The refund goes back to your original payment method."
    override val simulateDispatch = "Simulate order dispatch"
    override val refund = "Refund"
    override val capturingPayment = "Charging…"
    override val refundingPayment = "Refunding…"
    override val paymentVoided = "Order cancelled"
    override val voidedNote = "The hold was released — you were never charged."
    override val voidOrder = "Cancel order (release hold)"
    override val voidingPayment = "Releasing hold…"
    override val authCode = "Auth code"
    override val paymentId = "Payment id"
    override val date = "Date"
    override val monthAbbreviations =
        listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    override val newPayment = "New payment"

    override val orderHistory = "Order history"
    override val orderDetail = "Order detail"
    override val timeline = "Timeline"
    override val back = "Back"
    override val emptyHistory = "No orders yet — completed payments will show up here."
    override val statusAuthorized = "Authorized"
    override val statusCaptured = "Charged"
    override val statusRefunded = "Refunded"
    override val statusVoided = "Cancelled"

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
    override val checkCardNumber = "Revisa el número de tarjeta"
    override val cardExpired = "La tarjeta está caducada"
    override val checkExpiry = "Introduce una fecha válida (MM/AA)"
    override val checkCvv = "Revisa el CVV"
    override fun pay(amount: String) = "Pagar $amount"

    override val card = "Tarjeta"
    override fun cardEndingIn(brand: String, last4: String) = "$brand, tarjeta terminada en $last4"
    override fun brandCard(brand: String) = "Tarjeta $brand"

    override fun continueAt(provider: String) = "Continúa en $provider"
    override val redirectWebhookNote =
        "Aprobarás el pago en la página segura del proveedor. El resultado final es el que el " +
            "proveedor confirma a nuestra pasarela (webhook), no tu vuelta a la app."
    override val approveSimulated = "Aprobar pago (simulado)"
    override val simulateProviderFailure = "Simular fallo del proveedor"
    override val confirmingWithProvider = "Confirmando con el proveedor…"
    override fun payWith(provider: String, amount: String) = "Pagar $amount con $provider"
    override fun simulatedProviderPageLink(provider: String) =
        "Enlace a la página de aprobación simulada de $provider"

    override val sizeChange = "Cambio de talla"
    override val refundToOrigin = "Reembolso al método original"
    override val available = "Disponible"
    override val notAvailable = "No disponible"

    override val giftCard = "Tarjeta regalo"
    override val giftCardCode = "Código de la tarjeta regalo"
    override val apply = "Aplicar"
    override val remove = "Quitar"
    override val giftCardNotFound = "Tarjeta regalo no encontrada. Revisa el código e inténtalo de nuevo."
    override fun giftCardApplied(amount: String) = "Aplicado: −$amount"
    override fun remainingToPay(amount: String) = "Restante a pagar: $amount"
    override val giftCardCoversTotal = "La tarjeta regalo cubre el total: no hace falta tarjeta ni 3D Secure."
    override fun payWithGiftCard(amount: String) = "Pagar $amount con tarjeta regalo"
    override fun demoGiftCards(codes: String) = "Códigos demo: $codes"
    override fun removeGiftCard(code: String) = "Quitar la tarjeta regalo $code"
    override fun giftCardNotUsedWith(provider: String) =
        "La tarjeta regalo no se usa con $provider: se cobrará el total completo."
    override val minusSign = "−"
    override fun minusAmount(amount: String) = "menos $amount"

    override val paymentAuthorized = "Pago autorizado"
    override val authorizedChargeNote = "Se cobrará al enviar el pedido."
    override val paymentCaptured = "Pago cobrado"
    override val paymentRefunded = "Pago reembolsado"
    override val refundedNote = "El reembolso vuelve a tu método de pago original."
    override val simulateDispatch = "Simular envío del pedido"
    override val refund = "Reembolsar"
    override val capturingPayment = "Cobrando…"
    override val refundingPayment = "Reembolsando…"
    override val paymentVoided = "Pedido cancelado"
    override val voidedNote = "La retención se liberó: no se llegó a cobrar."
    override val voidOrder = "Cancelar pedido (liberar retención)"
    override val voidingPayment = "Liberando retención…"
    override val authCode = "Código de autorización"
    override val paymentId = "ID de pago"
    override val date = "Fecha"
    override val monthAbbreviations =
        listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")
    override val newPayment = "Nuevo pago"

    override val orderHistory = "Histórico de pedidos"
    override val orderDetail = "Detalle del pedido"
    override val timeline = "Cronología"
    override val back = "Atrás"
    override val emptyHistory = "Aún no hay pedidos: los pagos completados aparecerán aquí."
    override val statusAuthorized = "Autorizado"
    override val statusCaptured = "Cobrado"
    override val statusRefunded = "Reembolsado"
    override val statusVoided = "Cancelado"

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
