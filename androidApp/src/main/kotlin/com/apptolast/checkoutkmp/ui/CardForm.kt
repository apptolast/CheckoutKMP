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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.apptolast.checkoutkmp.data.tokenizer.RawCard
import com.apptolast.checkoutkmp.domain.model.CardBrand
import com.apptolast.checkoutkmp.domain.model.CardExpiry
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
    onSubmit: (RawCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pan by remember { mutableStateOf("") }        // raw digits, sensitive — UI-only
    var expiry by remember { mutableStateOf("") }     // raw MMYY digits
    var cvv by remember { mutableStateOf("") }        // sensitive — UI-only

    val brand = CardBrand.detect(pan)
    val panValid = pan.length in 12..19 && Luhn.isValid(pan)
    val parsedExpiry = CardExpiry.parse(expiry)
    val expiryValid = parsedExpiry != null && !parsedExpiry.isExpiredNow()
    val cvvValid = cvv.length in 3..4
    val formValid = panValid && expiryValid && cvvValid

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = pan,
            onValueChange = { pan = digitsOnly(it, max = 19) },
            label = { Text("Card number") },
            supportingText = {
                val visual = if (pan.length >= 4) "${brand.displayName} · •••• ${pan.takeLast(4)}" else brand.displayName
                val spoken = if (pan.length >= 4) "${brand.displayName}, card ending in ${pan.takeLast(4)}" else "${brand.displayName} card"
                // key() gives the node a fresh identity per distinct value, so screen readers don't
                // re-announce the previous brand/last4 while the number is still being typed.
                key(visual) {
                    Text(visual, modifier = Modifier.semantics { contentDescription = spoken })
                }
            },
            isError = pan.isNotEmpty() && !panValid,
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            visualTransformation = CardNumberVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = expiry,
                onValueChange = { expiry = digitsOnly(it, max = 4) },
                label = { Text("MM/YY") },
                isError = expiry.isNotEmpty() && !expiryValid,
                singleLine = true,
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = ExpiryVisualTransformation(),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = cvv,
                onValueChange = { cvv = digitsOnly(it, max = 4) },
                label = { Text("CVV") },
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
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text("Pay")
        }
    }
}
