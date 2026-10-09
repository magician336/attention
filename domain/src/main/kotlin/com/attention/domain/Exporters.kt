package com.attention.domain

/** CSV is intentionally analysis-only: it does not contain enough data to restore a state. */
fun AttentionState.timeEntriesCsv(): String = buildCsv(
    listOf("planning_date", "duration_minutes", "target_id", "target_title", "source", "note"),
    timeEntries.map { entry ->
        listOf(
            entry.planningDate,
            entry.durationMinutes.toString(),
            entry.targetId.orEmpty(),
            targets.firstOrNull { it.id == entry.targetId }?.title.orEmpty(),
            entry.source.name,
            entry.note,
        )
    },
)

fun AttentionState.schedulesCsv(): String = buildCsv(
    listOf("planning_date", "title", "completed", "estimated_minutes", "start_minute", "end_minute", "target_id", "target_title", "note"),
    schedules.map { entry ->
        listOf(
            entry.planningDate,
            entry.title,
            entry.completed.toString(),
            entry.estimatedMinutes?.toString().orEmpty(),
            entry.startMinute?.toString().orEmpty(),
            entry.endMinute?.toString().orEmpty(),
            entry.targetId.orEmpty(),
            targets.firstOrNull { it.id == entry.targetId }?.title.orEmpty(),
            entry.note,
        )
    },
)

fun AttentionState.analysisCsv(): String = buildCsv(
    listOf("record_type", "planning_date", "duration_minutes", "title", "completed", "target_id", "target_title", "source", "note"),
    timeEntries.map { entry ->
        listOf("time_entry", entry.planningDate, entry.durationMinutes.toString(), "", "", entry.targetId.orEmpty(), targets.firstOrNull { it.id == entry.targetId }?.title.orEmpty(), entry.source.name, entry.note)
    } + schedules.map { entry ->
        listOf("schedule", entry.planningDate, (entry.estimatedMinutes ?: 0).toString(), entry.title, entry.completed.toString(), entry.targetId.orEmpty(), targets.firstOrNull { it.id == entry.targetId }?.title.orEmpty(), "", entry.note)
    },
)

private fun buildCsv(header: List<String>, rows: List<List<String>>): String = buildString {
    append('\uFEFF')
    appendLine(header.joinToString(",", transform = ::csvCell))
    rows.forEach { appendLine(it.joinToString(",", transform = ::csvCell)) }
}

private fun csvCell(value: String): String = if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
    "\"${value.replace("\"", "\"\"")}\""
} else value
