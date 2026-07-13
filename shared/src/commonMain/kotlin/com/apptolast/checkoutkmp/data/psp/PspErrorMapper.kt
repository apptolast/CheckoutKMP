package com.apptolast.checkoutkmp.data.psp

import com.apptolast.checkoutkmp.domain.model.PaymentError

/**
 * Single place where raw PSP failures are translated into the domain [PaymentError] taxonomy.
 * Keeping this at the boundary means the domain and UI never see PSP-specific codes.
 * (Phase 7 extends this with richer decline-code handling.)
 */
internal object PspErrorMapper {

    fun map(exception: PspException): PaymentError = when (exception.kind) {
        PspException.Kind.NETWORK -> PaymentError.Network
        PspException.Kind.TIMEOUT -> PaymentError.Timeout
        PspException.Kind.RATE_LIMITED -> PaymentError.RateLimited
    }

    fun mapDeclined(response: PspResponse.Declined): PaymentError =
        PaymentError.Declined(response.code)

    fun mapScaFailed(response: PspResponse.ScaFailed): PaymentError =
        PaymentError.ScaFailed(response.reason)
}
