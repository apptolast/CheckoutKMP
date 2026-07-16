package com.apptolast.checkoutkmp.ui

import androidx.compose.runtime.Composable
import com.apptolast.checkoutkmp.domain.model.CardBrand

/**
 * Localized display label for a card brand. Real networks (Visa, Mastercard, …) are proper nouns and
 * keep their [CardBrand.displayName]; only the [CardBrand.UNKNOWN] fallback is localized.
 */
@Composable
fun brandLabel(brand: CardBrand): String =
    if (brand == CardBrand.UNKNOWN) LocalStrings.current.card else brand.displayName
