package com.apptolast.checkoutkmp.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.apptolast.checkoutkmp.domain.model.CardBrand
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.model.CardRules
import com.apptolast.checkoutkmp.domain.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.usecase.Luhn

/**
 * Card entry form with live validation.
 *
 * The PAN and CVV live only in this composable's local state (plain [remember], intentionally NOT
 * `rememberSaveable`, so they are never persisted). They leave only via [onSubmit] as a [RawCard].
 *
 * Validation UX: a field is only flagged as an error once the user has left it ([TouchedState]) or
 * once the input is unambiguously complete (a full-length PAN failing Luhn, four expiry digits that
 * don't parse) — never while a value is still legitimately in progress.
 */
@Composable
fun CardForm(
    enabled: Boolean,
    payAmount: String,
    onSubmit: (RawCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val focusManager = LocalFocusManager.current
    var pan by remember { mutableStateOf("") }        // raw digits, sensitive — UI-only
    var expiry by remember { mutableStateOf("") }     // raw MMYY digits
    var cvv by remember { mutableStateOf("") }        // sensitive — UI-only
    val panTouched = remember { TouchedState() }
    val expiryTouched = remember { TouchedState() }
    val cvvTouched = remember { TouchedState() }

    val brand = CardBrand.detect(pan)
    val panValid = pan.length in CardRules.PAN_LENGTHS && Luhn.isValid(pan)
    val parsedExpiry = CardExpiry.parse(expiry)
    val expiryValid = parsedExpiry != null && !parsedExpiry.isExpiredNow()
    val cvvValid = cvv.length in CardRules.CVV_LENGTHS
    val formValid = panValid && expiryValid && cvvValid

    val panError = pan.isNotEmpty() && !panValid &&
        (panTouched.touched || pan.length == CardRules.PAN_LENGTHS.last)
    val expiryError = expiry.isNotEmpty() && !expiryValid &&
        (expiryTouched.touched || expiry.length == CardRules.EXPIRY_TOTAL_DIGITS)
    val cvvError = cvv.isNotEmpty() && !cvvValid && cvvTouched.touched

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        OutlinedTextField(
            value = pan,
            onValueChange = { pan = digitsOnly(it, max = CardRules.PAN_LENGTHS.last) },
            label = { Text(strings.cardNumber) },
            leadingIcon = {
                // The mark follows the detected brand; Crossfade keeps the swap subtle. It is
                // purely decorative (contentDescription = null) — the brand is announced by the
                // supporting text below, so nothing is double-spoken.
                Crossfade(targetState = brand) { detected -> FieldIcon(brandIcon(detected)) }
            },
            supportingText = {
                if (panError) Text(strings.checkCardNumber) else CardBrandSupportingText(brand, pan)
            },
            isError = panError,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            visualTransformation = CardNumberVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { panTouched.onFocusChanged(it.isFocused) }
                // Autofill only: lets the platform offer a saved card. The filled value goes
                // through onValueChange like typed input, so it is digit-stripped and stays in
                // this composable's local state — the PAN still never reaches any shared state.
                .semantics { contentType = ContentType.CreditCardNumber },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
            OutlinedTextField(
                value = expiry,
                onValueChange = { new ->
                    val digits = digitsOnly(new, max = CardRules.EXPIRY_TOTAL_DIGITS)
                    val justCompleted = digits.length == CardRules.EXPIRY_TOTAL_DIGITS &&
                        expiry.length < CardRules.EXPIRY_TOTAL_DIGITS
                    expiry = digits
                    // Hop straight to the CVV once MM/YY is fully typed — expiry has a fixed length,
                    // so completion is unambiguous (unlike the 12–19 digit PAN).
                    if (justCompleted) focusManager.moveFocus(FocusDirection.Next)
                },
                label = { Text(strings.expiryMmYy) },
                leadingIcon = { FieldIcon(CheckoutIcons.CalendarMonth) },
                supportingText = {
                    // Always present so the row's two fields keep the same height and nothing
                    // jumps when the message appears. Expired cards get their own message.
                    val message = when {
                        !expiryError -> ""
                        parsedExpiry != null -> strings.cardExpired
                        else -> strings.checkExpiry
                    }
                    Text(message)
                },
                isError = expiryError,
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                visualTransformation = ExpiryVisualTransformation(),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { expiryTouched.onFocusChanged(it.isFocused) }
                    .semantics { contentType = ContentType.CreditCardExpirationDate },
            )
            OutlinedTextField(
                value = cvv,
                onValueChange = { cvv = digitsOnly(it, max = CardRules.CVV_LENGTHS.last) },
                label = { Text(strings.cvv) },
                leadingIcon = { FieldIcon(CheckoutIcons.Lock) },
                supportingText = { Text(if (cvvError) strings.checkCvv else "") },
                isError = cvvError,
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                // Masked on screen like any secret; it never leaves local state except via Submit.
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { cvvTouched.onFocusChanged(it.isFocused) }
                    .semantics { contentType = ContentType.CreditCardSecurityCode },
            )
        }

        PayCta(
            label = strings.pay(payAmount),
            enabled = enabled && formValid,
            onClick = {
                val exp = parsedExpiry ?: return@PayCta
                onSubmit(RawCard(pan = pan, expiry = exp, cvv = cvv))
            },
            modifier = Modifier.padding(top = Dimens.spacingXSmall),
        )
    }
}

/**
 * Tracks whether a field has been visited and abandoned: focus gained, then lost. Errors are held
 * back until then so users aren't shouted at mid-typing.
 */
@Stable
private class TouchedState {
    var touched by mutableStateOf(false)
        private set

    private var hadFocus = false

    fun onFocusChanged(isFocused: Boolean) {
        if (isFocused) hadFocus = true else if (hadFocus) touched = true
    }
}

/** The abstract letter mark for a detected brand; [CardBrand.UNKNOWN] keeps the generic card. */
private fun brandIcon(brand: CardBrand): ImageVector = when (brand) {
    CardBrand.VISA -> CheckoutIcons.BrandVisa
    CardBrand.MASTERCARD -> CheckoutIcons.BrandMastercard
    CardBrand.AMEX -> CheckoutIcons.BrandAmex
    CardBrand.DISCOVER -> CheckoutIcons.BrandDiscover
    CardBrand.UNKNOWN -> CheckoutIcons.CreditCard
}

/** A muted leading icon shared by the card fields. */
@Composable
private fun FieldIcon(icon: ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(Dimens.iconMedium),
    )
}

/**
 * Supporting text under the card field: shows the detected brand and, once enough digits are
 * entered, the masked last four. A [key] gives the node a fresh identity per distinct value so
 * screen readers don't re-announce the previous brand/last4 while the number is still being typed.
 */
@Composable
private fun CardBrandSupportingText(brand: CardBrand, pan: String) {
    val strings = LocalStrings.current
    val hasLast4 = pan.length >= CardRules.LAST4_LENGTH
    val last4 = pan.takeLast(CardRules.LAST4_LENGTH)
    val label = brandLabel(brand)
    // Until a brand is detected, stay blank instead of echoing the field's own "Card" label.
    val visual = when {
        hasLast4 -> "$label · •••• $last4"
        brand == CardBrand.UNKNOWN -> ""
        else -> label
    }
    val spoken = if (hasLast4) strings.cardEndingIn(label, last4) else strings.brandCard(label)
    key(visual) {
        Text(visual, modifier = Modifier.semantics { contentDescription = spoken })
    }
}
