package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.apptolast.checkoutkmp.domain.model.ScaChallenge
import com.apptolast.checkoutkmp.domain.simulation.DemoDefaults

/**
 * Simulated 3D Secure (SCA) challenge. The user enters the OTP; the screen supports the three
 * outcomes handled by the ViewModel: success, wrong code (retry via [otpError]) and cancellation.
 *
 * Entry UX: the code field takes focus on arrival (it is the only task here), submits itself the
 * moment the last digit is typed, and a wrong-code error clears as soon as the user edits the code
 * again. The verify progress lives inside the button so the layout never jumps.
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
    val strings = LocalStrings.current
    var otp by remember { mutableStateOf("") }
    // Re-initialised each verify cycle (isVerifying flips per attempt), so a fresh wrong-code
    // verdict shows again even when otpError was already true on the previous attempt.
    var editedSinceVerdict by remember(isVerifying) { mutableStateOf(false) }
    val showOtpError = otpError && !editedSinceVerdict
    val focusRequester = remember { FocusRequester() }
    val deliveryTarget = challenge.deliveryHint ?: strings.yourDevice

    // The OTP field is the single task on this screen — hand it the focus (and keyboard) on entry.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
                strings.threeDSecureVerification,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
        Text(
            strings.enterCodeSentTo(challenge.otpLength, deliveryTarget),
            style = MaterialTheme.typography.bodyMedium,
        )
        DemoSurface {
            Text(
                "${strings.demoCode}: ${DemoDefaults.SCA_OTP}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = otp,
            onValueChange = { new ->
                val digits = digitsOnly(new, max = challenge.otpLength)
                val justCompleted = digits.length == challenge.otpLength && otp.length < challenge.otpLength
                if (digits != otp) editedSinceVerdict = true
                otp = digits
                // The OTP has a known fixed length, so typing the last digit IS the submit gesture;
                // the Verify button stays as the explicit/accessible alternative.
                if (justCompleted) onVerify(digits)
            },
            label = { Text(strings.verificationCode) },
            leadingIcon = {
                Icon(
                    CheckoutIcons.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Dimens.iconMedium),
                )
            },
            isError = showOtpError,
            supportingText = {
                // Slot always present so showing/clearing the error never shifts the layout.
                if (showOtpError) {
                    // Announce a wrong-code error assertively so it interrupts and is not missed.
                    Text(
                        strings.incorrectCode,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                } else {
                    Text("")
                }
            },
            singleLine = true,
            enabled = !isVerifying,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )

        Button(
            onClick = { onVerify(otp) },
            enabled = !isVerifying && otp.length == challenge.otpLength,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isVerifying) {
                // Progress replaces the icon in place: same footprint, no layout jump.
                CircularProgressIndicator(
                    color = LocalContentColor.current,
                    strokeWidth = Dimens.progressStrokeThin,
                    modifier = Modifier.size(Dimens.iconSmall),
                )
            } else {
                Icon(CheckoutIcons.VerifiedUser, contentDescription = null, modifier = Modifier.size(Dimens.iconSmall))
            }
            Spacer(Modifier.width(Dimens.spacingSmall))
            Text(strings.verify)
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !isVerifying,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(strings.cancel)
        }
    }
}
