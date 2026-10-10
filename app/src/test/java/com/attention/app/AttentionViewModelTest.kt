package com.attention.app

import com.attention.app.data.AttentionStateRepository
import com.attention.domain.AttentionState
import com.attention.domain.GoalCadence
import com.attention.domain.GoalStage
import com.attention.domain.LaunchDestination
import com.attention.domain.StoredSettings
import com.attention.domain.Target
import com.attention.domain.TimeEntry
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AttentionViewModelTest {
    @Test
    fun editing_a_historical_entry_awards_newly_eligible_stage_feedback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val date = LocalDate.now(ZoneOffset.UTC)
            val target = Target("view-model-target", title = "阶段目标")
            val repository = FakeRepository(
                AttentionState(
                    targets = listOf(target),
                    goalStages = listOf(GoalStage("view-model-stage", target.id, GoalCadence.ONE_TIME, 30, date.minusDays(2).toString())),
                    timeEntries = listOf(
                        TimeEntry("historical-entry", date.minusDays(2).toString(), 10),
                        TimeEntry("later-entry", date.minusDays(1).toString(), 20, target.id),
                    ),
                ),
            )
            val viewModel = AttentionViewModel(repository, ZoneOffset.UTC)

            viewModel.editTime("historical-entry", 10, target.id)
            advanceUntilIdle()

            assertEquals(1, repository.state.value.milestones.count { it.kind == "one_time_goal" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeRepository(initial: AttentionState) : AttentionStateRepository {
        private val values = MutableStateFlow(initial)
        override val state: StateFlow<AttentionState> = values

        override suspend fun updateBusiness(transform: (AttentionState) -> AttentionState) {
            values.value = transform(values.value)
        }

        override suspend fun setPlannerSettings(settings: StoredSettings) = Unit
        override suspend fun setPlanningDayBoundaryMinutes(minutes: Int) = Unit
        override suspend fun setWeekStartDay(day: Int) = Unit
        override suspend fun setDailyCapacityMinutes(minutes: Int?) = Unit
        override suspend fun setLaunchDestination(destination: LaunchDestination) = Unit
        override suspend fun setLastOpenedDestination(destination: LaunchDestination) = Unit
        override suspend fun setOnboardingCompleted(completed: Boolean) = Unit
        override suspend fun setNotificationsEnabled(enabled: Boolean) = Unit
        override suspend fun replace(state: AttentionState) { values.value = state }
        override suspend fun exportJson(): String = ""
        override suspend fun importJson(encoded: String, clearExisting: Boolean): AttentionState = values.value
        override suspend fun lastImportBackup(): String? = null
    }
}
