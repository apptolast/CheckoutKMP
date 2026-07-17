package com.apptolast.checkoutkmp.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.apptolast.checkoutkmp.domain.model.PaymentMethod
import com.apptolast.checkoutkmp.presentation.MethodOption

/** The mark for a redirect wallet, so PayPal and Bizum no longer share the generic card glyph. */
internal fun walletIcon(provider: PaymentMethod.Wallet.Provider): ImageVector = when (provider) {
    PaymentMethod.Wallet.Provider.PAYPAL -> CheckoutIcons.BrandPaypal
    PaymentMethod.Wallet.Provider.BIZUM -> CheckoutIcons.BrandBizum
}

/** The leading mark for a method radio in the checkout method picker. */
internal fun methodOptionIcon(option: MethodOption): ImageVector = when (option) {
    MethodOption.CARD -> CheckoutIcons.CreditCard
    MethodOption.PAYPAL -> CheckoutIcons.BrandPaypal
    MethodOption.BIZUM -> CheckoutIcons.BrandBizum
}

/** The leading mark for a settled payment (receipt row, order history), keyed by its method. */
internal fun paymentMethodIcon(method: PaymentMethod): ImageVector = when (method) {
    is PaymentMethod.Card -> CheckoutIcons.CreditCard
    is PaymentMethod.Wallet -> walletIcon(method.provider)
    is PaymentMethod.GiftCard -> CheckoutIcons.Receipt
}
