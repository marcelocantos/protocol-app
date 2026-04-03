package com.marcelo.protocol.model

import kotlinx.serialization.Serializable

@Serializable
enum class DayType(val label: String, val emoji: String) {
    OFFICE("Office", "\uD83C\uDFE2"),
    WFH("WFH", "\uD83C\uDFE0"),
    REST("Rest", "\u2615");
}

fun checklistFor(type: DayType): List<ChecklistItem> = when (type) {
    DayType.OFFICE -> listOf(
        ChecklistItem("sunrise",    "☀\uFE0F  Morning light (commute)",      "05:30"),
        ChecklistItem("breakfast",  "\uD83C\uDF73  Protein breakfast",        "05:45"),
        ChecklistItem("snack",      "\uD83E\uDD5C  Healthy snack swap",      "10:00"),
        ChecklistItem("move1",      "\uD83E\uDDD1\u200D\uD83E\uDD3C  Movement break",  "10:30"),
        ChecklistItem("move2",      "\uD83E\uDDD1\u200D\uD83E\uDD3C  Movement break",  "12:00"),
        ChecklistItem("lunch",      "\uD83C\uDF7D\uFE0F  Real lunch before leaving",  "12:30"),
        ChecklistItem("gym",        "\uD83C\uDFCB\uFE0F  Gym session",       "14:00"),
        ChecklistItem("move3",      "\uD83E\uDDD1\u200D\uD83E\uDD3C  Movement break",  "16:00"),
        ChecklistItem("dinner",     "\uD83C\uDF7D\uFE0F  Dinner",            "18:30"),
        ChecklistItem("screenoff",  "\uD83D\uDCF5  Screens off",             "22:00"),
        ChecklistItem("bed",        "\uD83D\uDECF\uFE0F  In bed",            "23:00"),
    )
    DayType.WFH -> listOf(
        ChecklistItem("sunrise",    "☀\uFE0F  Morning sunlight (outside 5-10 min)", "07:00"),
        ChecklistItem("breakfast",  "\uD83C\uDF73  Protein breakfast",        "07:30"),
        ChecklistItem("snack",      "\uD83E\uDD5C  Healthy snack swap",      "10:00"),
        ChecklistItem("move1",      "\uD83E\uDDD1\u200D\uD83E\uDD3C  Movement break",  "10:30"),
        ChecklistItem("move2",      "\uD83E\uDDD1\u200D\uD83E\uDD3C  Movement break",  "12:00"),
        ChecklistItem("lunch",      "\uD83C\uDF7D\uFE0F  Lunch + walk",      "12:30"),
        ChecklistItem("gym",        "\uD83C\uDFCB\uFE0F  Gym session",       "15:00"),
        ChecklistItem("move3",      "\uD83E\uDDD1\u200D\uD83E\uDD3C  Movement break",  "16:30"),
        ChecklistItem("dinner",     "\uD83C\uDF7D\uFE0F  Dinner",            "18:30"),
        ChecklistItem("screenoff",  "\uD83D\uDCF5  Screens off",             "22:00"),
        ChecklistItem("bed",        "\uD83D\uDECF\uFE0F  In bed",            "23:00"),
    )
    DayType.REST -> listOf(
        ChecklistItem("sunrise",    "☀\uFE0F  Morning sunlight",             "08:00"),
        ChecklistItem("breakfast",  "\uD83C\uDF73  Protein breakfast",        "08:30"),
        ChecklistItem("gym",        "\uD83C\uDFCB\uFE0F  Gym session (if scheduled)", "10:00"),
        ChecklistItem("lunch",      "\uD83C\uDF7D\uFE0F  Lunch",             "12:30"),
        ChecklistItem("dinner",     "\uD83C\uDF7D\uFE0F  Dinner",            "18:30"),
        ChecklistItem("screenoff",  "\uD83D\uDCF5  Screens off",             "22:00"),
        ChecklistItem("bed",        "\uD83D\uDECF\uFE0F  In bed",            "23:00"),
    )
}
