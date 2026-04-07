// Copyright 2026 Marcelo Cantos
// SPDX-License-Identifier: Apache-2.0

package com.marcelo.protocol.model

const val GYM_WEEKLY_TARGET = 3

data class ChecklistItem(
    val id: String,
    val label: String,
    val timeHint: String,
)
