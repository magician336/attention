package com.attention.app.data

import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.app.data.settings.SettingsStore
import com.attention.domain.AttentionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Combines the Room business stream and the independent settings stream. */
class AttentionStateReader(
    private val businessState: Flow<AttentionState>,
    private val settingsStore: SettingsStore,
) {
    constructor(
        businessDataRepository: RoomBusinessDataRepository,
        settingsStore: SettingsStore,
    ) : this(businessDataRepository.observe(), settingsStore)

    val state: Flow<AttentionState> = combine(
        businessState,
        settingsStore.settings,
    ) { businessState, settings ->
        businessState.copy(
            settings = settings.toStoredSettings(),
            onboardingCompleted = settings.onboardingCompleted,
            launchDestination = settings.launchDestination,
            lastOpenedDestination = settings.lastOpenedDestination,
            notificationsEnabled = settings.notificationsEnabled,
        )
    }.distinctUntilChanged()
}
