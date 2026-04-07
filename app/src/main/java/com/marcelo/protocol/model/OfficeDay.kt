// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.model

import kotlinx.serialization.Serializable

@Serializable
enum class ParkingStatus(val label: String, val emoji: String) {
    UNPLANNED("Unplanned", "\u2753"),  // ❓
    PLANNED("Planned", "\uD83D\uDCCC"),  // 📌
    BOOKED("Booked", "\u2705"),  // ✅
}

@Serializable
data class OfficeDayPlan(
    val dayType: DayType? = null,  // null = undecided (❓)
    val parkingStatus: ParkingStatus = ParkingStatus.UNPLANNED,
) {
    val isDecided: Boolean get() = dayType != null
}
