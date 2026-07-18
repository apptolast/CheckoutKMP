package com.apptolast.checkoutkmp.ui

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

private const val TIME_COMPONENT_DIGITS = 2

/**
 * App-wide timestamp format in the device time zone, e.g. `17 Jul 2026, 13:25`. Month names come
 * from the localized [Strings] catalog; the numeric parts are locale-neutral. One formatter so every
 * date in the app — the receipt, the order-history rows and the lifecycle timeline — reads the same.
 */
fun formatDateTime(instant: Instant, strings: Strings): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = strings.monthAbbreviations[local.month.ordinal]
    val hour = local.hour.toString().padStart(TIME_COMPONENT_DIGITS, '0')
    val minute = local.minute.toString().padStart(TIME_COMPONENT_DIGITS, '0')
    return "${local.day} $month ${local.year}, $hour:$minute"
}
