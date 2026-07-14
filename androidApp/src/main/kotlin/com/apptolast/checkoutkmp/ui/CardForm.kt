package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.apptolast.checkoutkmp.R
import com.apptolast.checkoutkmp.data.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.model.CardBrand
import com.apptolast.checkoutkmp.domain.model.CardExpiry
import com.apptolast.checkoutkmp.domain.usecase.Luhn

// Card field bounds. The PAN range spans the shortest (some Maestro) to longest (19-digit) cards.
private const val MIN_PAN_LENGTH = 12
private const val MAX_PAN_LENGTH = 19
private const val MIN_CVV_LENGTH = 3
private const val MAX_CVV_LENGTH = 4
private const val EXPIRY_MAX_DIGITS = 4
private const val LAST_FOUR = 4

/**
 * Card entry form with live validation.
 *
 * The PAN and CVV live only in this composable's local state (plain [remember], intentionally NOT
 * `rememberSaveable`, so they are never persisted). They leave only via [onSubmit] as a [RawCard].
 */
@Composable
fun CardForm(
    enabled: Boolean,
    onSubmit: (RawCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pan by remember { mutableStateOf("") }        // raw digits, sensitive — UI-only
    var expiry by remember { mutableStateOf("") }     // raw MMYY digits
    var cvv by remember { mutableStateOf("") }        // sensitive — UI-only

    val brand = CardBrand.detect(pan)
    val panValid = pan.length in MIN_PAN_LENGTH..MAX_PAN_LENGTH && Luhn.isValid(pan)
    val parsedExpiry = CardExpiry.parse(expiry)
    val expiryValid = parsedExpiry != null && !parsedExpiry.isExpiredNow()
    val cvvValid = cvv.length in MIN_CVV_LENGTH..MAX_CVV_LENGTH
    val formValid = panValid && expiryValid && cvvValid

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        OutlinedTextField(
            value = pan,
            onValueChange = { pan = digitsOnly(it, max = MAX_PAN_LENGTH) },
            label = { Text(stringResource(R.string.card_number_label)) },
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
                onValueChange = { expiry = digitsOnly(it, max = EXPIRY_MAX_DIGITS) },
                label = { Text(stringResource(R.string.card_expiry_label)) },
                isError = expiry.isNotEmpty() && !expiryValid,
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ExpiryVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = cvv,
                onValueChange = { cvv = digitsOnly(it, max = MAX_CVV_LENGTH) },
                label = { Text(stringResource(R.string.card_cvv_label)) },
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
            Text(stringResource(R.string.action_pay))
        }
    }
}

/**
 * Supporting text under the card field: shows the detected brand and, once enough digits are
 * entered, the masked last four. A [key] gives the node a fresh identity per distinct value so
 * screen readers don't re-announce the previous brand/last4 while the number is still being typed.
 */
@Composable
private fun CardBrandSupportingText(brand: CardBrand, pan: String) {
    val hasLast4 = pan.length >= LAST_FOUR
    val last4 = pan.takeLast(LAST_FOUR)
    val visual = if (hasLast4) {
        stringResource(R.string.card_brand_last4, brand.displayName, last4)
    } else {
        stringResource(R.string.card_brand_only, brand.displayName)
    }
    val spoken = if (hasLast4) {
        stringResource(R.string.card_ending_in_a11y, brand.displayName, last4)
    } else {
        stringResource(R.string.card_brand_card_a11y, brand.displayName)
    }
    key(visual) {
        Text(visual, modifier = Modifier.semantics { contentDescription = spoken })
    }
}
