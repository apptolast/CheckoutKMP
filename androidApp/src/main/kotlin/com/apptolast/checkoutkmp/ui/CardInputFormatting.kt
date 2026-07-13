package com.apptolast.checkoutkmp.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

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
                if (i % 4 == 3 && i != digits.lastIndex) append(' ')
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                var spaces = offset / 4
                if (digits.length % 4 == 0 && offset == digits.length && offset > 0) spaces -= 1
                return offset + spaces
            }

            override fun transformedToOriginal(offset: Int): Int =
                (offset - offset / 5).coerceIn(0, digits.length)
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}

/** Renders four raw digits as `MM/YY`, inserting the slash once the year is being typed. */
class ExpiryVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(4)
        val out = if (digits.length <= 2) digits else digits.substring(0, 2) + "/" + digits.substring(2)
        val hasSlash = digits.length > 2
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                if (hasSlash && offset > 2) offset + 1 else offset

            override fun transformedToOriginal(offset: Int): Int =
                if (hasSlash && offset > 2) (offset - 1).coerceAtMost(digits.length) else offset
        }
        return TransformedText(AnnotatedString(out), mapping)
    }
}
