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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.apptolast.checkoutkmp.domain.model.ScaChallenge

/**
 * Simulated 3D Secure (SCA) challenge. The user enters the OTP; the screen supports the three
 * outcomes handled by the ViewModel: success, wrong code (retry via [otpError]) and cancellation.
 */
@Composable
fun ScaChallengeScreen(
    challenge: ScaChallenge,
    otpError: String?,
    isVerifying: Boolean,
    onVerify: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var otp by remember { mutableStateOf("") }
    val hint = challenge.deliveryHint ?: "your device"

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("3D Secure verification", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Enter the ${challenge.otpLength}-digit code sent to $hint.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Demo code: 123456",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = digitsOnly(it, max = challenge.otpLength) },
            label = { Text("Verification code") },
            isError = otpError != null,
            supportingText = { otpError?.let { Text(it) } },
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
            Text("Verify")
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !isVerifying,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        ) {
            Text("Cancel")
        }
    }
}
