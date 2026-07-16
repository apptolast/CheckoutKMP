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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import com.apptolast.checkoutkmp.domain.model.ScaChallenge
import com.apptolast.checkoutkmp.domain.simulation.DemoDefaults

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
    val deliveryTarget = challenge.deliveryHint ?: tr("your device", "tu dispositivo")

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall),
            modifier = Modifier.semantics(mergeDescendants = true) {
                heading()
                liveRegion = LiveRegionMode.Polite
            },
        ) {
            Icon(
                CheckoutIcons.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconMedium),
            )
            Text(
                tr("3D Secure verification", "Verificación 3D Secure"),
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            tr(
                "Enter the ${challenge.otpLength}-digit code sent to $deliveryTarget.",
                "Introduce el código de ${challenge.otpLength} dígitos enviado a $deliveryTarget.",
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            tr("Demo code: ${DemoDefaults.SCA_OTP}", "Código demo: ${DemoDefaults.SCA_OTP}"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = digitsOnly(it, max = challenge.otpLength) },
            label = { Text(tr("Verification code", "Código de verificación")) },
            leadingIcon = {
                Icon(
                    CheckoutIcons.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconMedium),
                )
            },
            isError = otpError,
            supportingText = {
                if (otpError) {
                    // Announce a wrong-code error assertively so it interrupts and is not missed.
                    Text(
                        tr("Incorrect code, try again.", "Código incorrecto, inténtalo de nuevo."),
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
            Icon(CheckoutIcons.VerifiedUser, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
            Spacer(Modifier.width(Dimens.spacingSmall))
            Text(tr("Verify", "Verificar"))
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !isVerifying,
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingTiny),
        ) {
            Text(tr("Cancel", "Cancelar"))
        }
    }
}
