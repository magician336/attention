package com.attention.app.fuguang.week.components

import androidx.compose.runtime.Immutable

enum class FuguangWeekAccent {
    GREEN,
    GOLD,
    BLUE,
    ROSE,
    NEUTRAL,
}

enum class FuguangMessageAuthor {
    USER,
    AGENT,
}

@Immutable
data class FuguangChatMessageUi(
    val id: String,
    val author: FuguangMessageAuthor,
    val text: String,
    val emphasizedPhrases: List<String> = emptyList(),
)

enum class FuguangDayMarker {
    NONE,
    DOT,
    RING,
    CONFLICT,
}

@Immutable
data class FuguangDayItem(
    val id: String,
    val weekday: String,
    val date: String,
    val selected: Boolean = false,
    val marker: FuguangDayMarker = FuguangDayMarker.NONE,
)

@Immutable
data class FuguangAdjustmentItemUi(
    val id: String,
    val title: String,
    val detail: String,
    val accepted: Boolean = true,
)

@Immutable
data class FuguangAdjustmentProposalUi(
    val title: String = "调整后的安排",
    val impactLabel: String = "影响 4 天",
    val items: List<FuguangAdjustmentItemUi>,
    val acceptLabel: String = "就按这个改",
    val adjustLabel: String = "我自己调",
)

enum class FuguangAgendaTone {
    STUDY,
    CLASS,
    FIXED,
    COMMUTE,
    PRIVATE,
    BUFFER,
}

@Immutable
data class FuguangAgendaEntryUi(
    val id: String,
    val title: String,
    val location: String,
    val startMinute: Int,
    val endMinute: Int,
    val tone: FuguangAgendaTone = FuguangAgendaTone.STUDY,
    val statusLabel: String? = null,
)

@Immutable
data class FuguangDayPlanUi(
    val id: String,
    val weekday: String,
    val date: String,
    val entries: List<FuguangAgendaEntryUi>,
    val changed: Boolean = false,
)

@Immutable
data class FuguangExternalCardUi(
    val sender: String,
    val group: String,
    val quote: String,
    val emphasizedPhrases: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Immutable
data class FuguangMiniSegmentUi(
    val label: String,
    val weight: Float,
    val accent: FuguangWeekAccent,
    val outlined: Boolean = false,
)

@Immutable
data class FuguangMiniTimelineRowUi(
    val label: String,
    val segments: List<FuguangMiniSegmentUi>,
)

@Immutable
data class FuguangConflictPreviewUi(
    val title: String,
    val rows: List<FuguangMiniTimelineRowUi>,
    val startLabel: String,
    val middleLabel: String,
    val endLabel: String,
)

@Immutable
data class FuguangRelocationOptionUi(
    val id: String,
    val letter: String,
    val time: String,
    val place: String,
    val cost: String,
    val recommended: Boolean = false,
)

@Immutable
data class FuguangWeekLegendItemUi(
    val label: String,
    val accent: FuguangWeekAccent,
)

@Immutable
data class FuguangWeekNarrativeUi(
    val title: String,
    val body: String,
)

fun formatMinute(minute: Int): String {
    val safeMinute = minute.coerceAtLeast(0)
    return "%02d:%02d".format(safeMinute / 60, safeMinute % 60)
}