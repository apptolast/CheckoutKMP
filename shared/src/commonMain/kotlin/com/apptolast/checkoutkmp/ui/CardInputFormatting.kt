package com.apptolast.checkoutkmp.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

// Digits per displayed card group ("4242 4242 4242 4242"); +1 accounts for the separator space.
private const val CARD_GROUP_SIZE = 4
private const val CARD_GROUP_STRIDE = CARD_GROUP_SIZE + 1

// Expiry is entered as MMYY; the slash is inserted after the two month digits.
private const val EXPIRY_MONTH_DIGITS = 2
private const val EXPIRY_TOTAL_DIGITS = 4

/** Keep only digits, capped at [max] characters. */
fun digitsOnly(input: String, max: Int): String =
    input.filter { it.isDigit() }.take(max)

/** Groups the card number into blocks of four for display: `4242 4242 4242 4242`. */
class CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text
        val out = buildString {
            digits.forEachIndexed { i, c ->
                append(c)
                if (i % CARD_GROUP_SIZE == CARD_GROUP_SIZE - 1 && i != digits.lastIndex) append(' ')
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var spaces = offset / CARD_GROUP_SIZE
                if (digits.length % CARD_GROUP_SIZE == 0 && offset == digits.length && offset > 0) spaces -= 1
                return offset + spaces
            }

            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / CARD_GROUP_STRIDE).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** Renders four raw digits as `MM/YY`, inserting the slash once the year is being typed. */
class ExpiryVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(EXPIRY_TOTAL_DIGITS)
        val out = if (digits.length <= EXPIRY_MONTH_DIGITS) {
            digits
        } else {
            digits.substring(0, EXPIRY_MONTH_DIGITS) + "/" + digits.substring(EXPIRY_MONTH_DIGITS)
        }
        val hasSlash = digits.length > EXPIRY_MONTH_DIGITS
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (hasSlash && offset > EXPIRY_MONTH_DIGITS) offset + 1 else offset

            override fun transformedToOriginal(offset: Int): Int =
                if (hasSlash && offset > EXPIRY_MONTH_DIGITS) (offset - 1).coerceAtMost(digits.length) else offset
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
