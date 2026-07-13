package com.apptolast.checkoutkmp.domain.usecase

/**
 * The Luhn (mod-10) checksum used to catch mistyped card numbers. Pure and platform-agnostic.
 * This is a checksum, not a length/brand check — callers combine it with those where needed.
 */
object Luhn {
    /** True if [number] (ignoring spaces) is all digits and passes the Luhn checksum. */
    fun isValid(number: String): Boolean {
        val digits = number.filterNot { it.isWhitespace() }
        if (digits.length < 2 || digits.any { !it.isDigit() }) return false

        var sum = 0
        var doubleIt = false
        for (i in digits.indices.reversed()) {
            var d = digits[i].digitToInt()
            if (doubleIt) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
            doubleIt = !doubleIt
        }
        return sum % 10 == 0
    }
}
