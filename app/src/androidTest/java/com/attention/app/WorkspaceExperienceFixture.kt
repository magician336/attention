package com.attention.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.attention.app.data.room.AttentionDatabase
import com.attention.app.data.room.RoomBusinessDataRepository
import com.attention.domain.AttentionState
import com.attention.domain.addTarget
import com.attention.domain.addTimeEntry
import com.attention.domain.planningDate
import java.time.Instant
import kotlinx.coroutines.runBlocking

/** Seeds one isolated Room state for Compose growth-page integration tests. */
internal object WorkspaceExperienceFixture {
    fun seed(targetId: String, targetTitle: String, minutes: Int) {
        val planningDate = AttentionState().planningDate(Instant.now())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = AttentionDatabase.create(context)
        try {
            runBlocking {
                val withTarget = AttentionState().addTarget(targetTitle)
                val target = withTarget.targets.single()
                val state = withTarget
                    .copy(targets = listOf(target.copy(id = targetId)))
                    .addTimeEntry(planningDate.toString(), minutes, targetId)
                RoomBusinessDataRepository(database).replace(state)
            }
        } finally {
            database.close()
        }
    }
}
