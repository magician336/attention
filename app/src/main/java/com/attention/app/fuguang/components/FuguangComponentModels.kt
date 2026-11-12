package com.attention.app.fuguang.components

import androidx.compose.runtime.Immutable

@Immutable
data class FuguangTextSegment(
    val text: String,
    val emphasis: List<String> = emptyList(),
)

enum class FuguangAvatarState {
    GLOW,
    IMAGE,
    INITIAL,
    IMAGE_FAILED,
}

enum class FuguangBadgeKind {
    TARGET,
    COUNT,
    NEUTRAL,
}

enum class FuguangButtonKind {
    PRIMARY,
    SECONDARY,
    TEXT,
    ICON,
}

enum class FuguangScheduleKind {
    GROUP_INTERVIEW,
    CLASS,
    IELTS,
    MEETING,
    TASK,
}

enum class FuguangScheduleState {
    DEFAULT,
    NEW,
    CANCELLED,
}

@Immutable
data class FuguangScheduleBlockUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val startMinute: Int,
    val endMinute: Int,
    val kind: FuguangScheduleKind,
    val state: FuguangScheduleState = FuguangScheduleState.DEFAULT,
)

@Immutable
data class FuguangDateCellUi(
    val id: String,
    val weekday: String,
    val date: String,
    val selected: Boolean = false,
    val changed: Boolean = false,
    val conflicted: Boolean = false,
)

@Immutable
data class FuguangAdjustmentItemUi(
    val id: String,
    val title: String,
    val detail: String,
    val accepted: Boolean,
)

@Immutable
data class FuguangAdjustmentCardUi(
    val title: String,
    val status: String,
    val items: List<FuguangAdjustmentItemUi>,
    val primaryLabel: String,
    val secondaryLabel: String,
)

@Immutable
data class FuguangFeishuCardUi(
    val source: String,
    val time: String,
    val body: String,
    val emphasis: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
)

@Immutable
data class FuguangComparisonRowUi(
    val label: String,
    val text: String,
    val isOriginal: Boolean,
)

@Immutable
data class FuguangWeekEntryUi(
    val id: String,
    val start: String,
    val end: String,
    val title: String,
    val location: String,
    val isNew: Boolean = false,
)

@Immutable
data class FuguangWeekDayCardUi(
    val id: String,
    val weekday: String,
    val date: String,
    val totalLabel: String,
    val entries: List<FuguangWeekEntryUi>,
)

@Immutable
data class FuguangSummaryUi(
    val totalLabel: String,
    val itemCount: Int,
    val actionLabel: String,
)

@Immutable
data class FuguangRelocationOptionUi(
    val id: String,
    val indexLabel: String,
    val title: String,
    val subtitle: String,
    val cost: String,
    val recommended: Boolean = false,
)

enum class FuguangStateMode {
    EMPTY,
    LOADING,
    ERROR,
}
