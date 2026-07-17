package com.apptolast.checkoutkmp.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
 *
 * The code renders as one segmented box per digit, but it IS a single [BasicTextField]: the real
 * (invisible) field owns focus, the IME, paste and screen-reader semantics, and the cells are only
 * its decoration. TalkBack therefore reads one text field ("Verification code, 3 of 6 digits
 * entered"), not six focus stops.
 *
 * Resending: the "Resend code" action stays disabled while [resendSecondsLeft] ticks (the countdown
 * is driven by the ViewModel, not by this composable) and [otpResent] confirms a reissue politely.
 */
@Composable
fun ScaChallengeScreen(
    challenge: ScaChallenge,
    otpError: Boolean,
    isVerifying: Boolean,
    resendSecondsLeft: Int,
    otpResent: Boolean,
    onVerify: (String) -> Unit,
    onResend: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    var otp by remember { mutableStateOf("") }
    // Re-initialised each verify cycle (isVerifying flips per attempt), so a fresh wrong-code
    // verdict shows again even when otpError was already true on the previous attempt.
    var editedSinceVerdict by remember(isVerifying) { mutableStateOf(false) }
    val showOtpError = otpError && !editedSinceVerdict
    var fieldFocused by remember { mutableStateOf(false) }
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

        BasicTextField(
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
            enabled = !isVerifying,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            // The real text stays invisible; the segmented cells below are the visual layer.
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { fieldFocused = it.isFocused }
                .semantics {
                    // One field, one announcement — progress instead of six focus stops.
                    contentDescription =
                        strings.verificationCodeProgress(otp.length, challenge.otpLength)
                    // Lets the platform offer the one-time code straight from the incoming SMS.
                    contentType = ContentType.SmsOtpCode
                },
            decorationBox = { innerTextField ->
                Box {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            Dimens.spacingSmall,
                            Alignment.CenterHorizontally,
                        ),
                        // The cells are decoration only: without semantics they can't split the
                        // field into per-digit focus stops for screen readers.
                        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    ) {
                        repeat(challenge.otpLength) { index ->
                            OtpCell(
                                digit = otp.getOrNull(index),
                                // The next digit lands here; only meaningful while focused.
                                isCurrent = fieldFocused && index == otp.length,
                                isError = showOtpError,
                            )
                        }
                    }
                    // The invisible field overlays the cells so tapping any cell focuses it and
                    // the paste/selection gestures keep their native touch target.
                    Box(Modifier.matchParentSize()) { innerTextField() }
                }
            },
        )

        // Slot always present so verdicts appearing/clearing never shift the layout.
        Text(
            text = when {
                showOtpError -> strings.incorrectCode
                otpResent -> strings.codeSentAgain
                else -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (showOtpError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.semantics {
                // A wrong code must interrupt; the resend confirmation can wait its turn.
                liveRegion = if (showOtpError) LiveRegionMode.Assertive else LiveRegionMode.Polite
            },
        )

        TextButton(
            onClick = onResend,
            enabled = !isVerifying && resendSecondsLeft == 0,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (resendSecondsLeft > 0) {
                    strings.resendCodeIn(resendSecondsLeft)
                } else {
                    strings.resendCode
                },
            )
        }

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

/** One digit box of the segmented OTP entry — purely visual, all semantics live on the field. */
@Composable
private fun OtpCell(
    digit: Char?,
    isCurrent: Boolean,
    isError: Boolean,
) {
    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val borderWidth = if (isCurrent) Dimens.otpCellStrokeActive else Dimens.otpCellStroke
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = Dimens.otpCellWidth, height = Dimens.otpCellHeight)
            .border(borderWidth, borderColor, RoundedCornerShape(Dimens.otpCellCorner)),
    ) {
        Text(
            digit?.toString().orEmpty(),
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
