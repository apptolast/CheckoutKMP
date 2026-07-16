package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
 */
@Composable
fun CardForm(
    enabled: Boolean,
    payAmount: String,
    onSubmit: (RawCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pan by remember { mutableStateOf("") }        // raw digits, sensitive — UI-only
    var expiry by remember { mutableStateOf("") }     // raw MMYY digits
    var cvv by remember { mutableStateOf("") }        // sensitive — UI-only

    val brand = CardBrand.detect(pan)
    val panValid = pan.length in CardRules.PAN_LENGTHS && Luhn.isValid(pan)
    val parsedExpiry = CardExpiry.parse(expiry)
    val expiryValid = parsedExpiry != null && !parsedExpiry.isExpiredNow()
    val cvvValid = cvv.length in CardRules.CVV_LENGTHS
    val formValid = panValid && expiryValid && cvvValid

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        OutlinedTextField(
            value = pan,
            onValueChange = { pan = digitsOnly(it, max = CardRules.PAN_LENGTHS.last) },
            label = { Text(tr("Card number", "Número de tarjeta")) },
            leadingIcon = { FieldIcon(CheckoutIcons.CreditCard) },
            supportingText = { CardBrandSupportingText(brand, pan) },
            isError = pan.isNotEmpty() && !panValid,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = CardNumberVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
            OutlinedTextField(
                value = expiry,
                onValueChange = { expiry = digitsOnly(it, max = CardRules.EXPIRY_TOTAL_DIGITS) },
                label = { Text(tr("MM/YY", "MM/AA")) },
                leadingIcon = { FieldIcon(CheckoutIcons.CalendarMonth) },
                isError = expiry.isNotEmpty() && !expiryValid,
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ExpiryVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = cvv,
                onValueChange = { cvv = digitsOnly(it, max = CardRules.CVV_LENGTHS.last) },
                label = { Text(tr("CVV", "CVV")) },
                leadingIcon = { FieldIcon(CheckoutIcons.Lock) },
                isError = cvv.isNotEmpty() && !cvvValid,
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = {
                val exp = parsedExpiry ?: return@Button
                onSubmit(RawCard(pan = pan, expiry = exp, cvv = cvv))
            },
            enabled = enabled && formValid,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingXSmall),
        ) {
            Icon(CheckoutIcons.Lock, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
            Spacer(Modifier.width(Dimens.spacingSmall))
            Text(tr("Pay $payAmount", "Pagar $payAmount"))
        }
    }
}

/** A muted leading icon shared by the card fields. */
@Composable
private fun FieldIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
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
    val hasLast4 = pan.length >= CardRules.LAST4_LENGTH
    val last4 = pan.takeLast(CardRules.LAST4_LENGTH)
    val label = brandLabel(brand)
    val visual = if (hasLast4) "$label · •••• $last4" else label
    val spoken = if (hasLast4) {
        tr("$label, card ending in $last4", "$label, tarjeta terminada en $last4")
    } else {
        tr("$label card", "Tarjeta $label")
    }
    key(visual) {
        Text(visual, modifier = Modifier.semantics { contentDescription = spoken })
    }
}
