package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.apptolast.checkoutkmp.R
import com.apptolast.checkoutkmp.domain.model.ScaChallenge

/**
 * Simulated 3D Secure (SCA) challenge. The user enters the OTP; the screen supports the three
 * outcomes handled by the ViewModel: success, wrong code (retry via [otpError]) and cancellation.
 */
@Composable
fun ScaChallengeScreen(
    challenge: ScaChallenge,
    otpError: Boolean,
    isVerifying: Boolean,
    onVerify: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var otp by remember { mutableStateOf("") }
    val deliveryTarget = challenge.deliveryHint ?: stringResource(R.string.sca_delivery_default)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        Text(
            stringResource(R.string.sca_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        )
        Text(
            stringResource(R.string.sca_code_prompt, challenge.otpLength, deliveryTarget),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            stringResource(R.string.sca_demo_code),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = digitsOnly(it, max = challenge.otpLength) },
            label = { Text(stringResource(R.string.sca_code_label)) },
            isError = otpError,
            supportingText = {
                if (otpError) {
                    // Announce a wrong-code error assertively so it interrupts and is not missed.
                    Text(
                        stringResource(R.string.sca_error_wrong_code),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            },
            singleLine = true,
            enabled = !isVerifying,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        if (isVerifying) {
            CircularProgressIndicator()
        }

        Button(
            onClick = { onVerify(otp) },
            enabled = !isVerifying && otp.length == challenge.otpLength,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_verify))
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !isVerifying,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingTiny),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}
