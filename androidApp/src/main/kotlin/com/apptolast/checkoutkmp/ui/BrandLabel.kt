package com.apptolast.checkoutkmp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.apptolast.checkoutkmp.R
import com.apptolast.checkoutkmp.domain.model.CardBrand

/**
 * Localized display label for a card brand. Real networks (Visa, Mastercard, …) are proper nouns and
 * keep their [CardBrand.displayName]; only the [CardBrand.UNKNOWN] fallback is localized.
 */
@Composable
fun brandLabel(brand: CardBrand): String =
    if (brand == CardBrand.UNKNOWN) stringResource(R.string.card_brand_generic) else brand.displayName
