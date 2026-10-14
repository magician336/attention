package com.attention.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.attention.app.ui.theme.AttentionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AttentionTheme {
                WorkspaceApp(
                    openScheduleDate = intent.getStringExtra(com.attention.app.reminder.ReminderReceiver.EXTRA_PLANNING_DATE),
                    openScheduleId = intent.getStringExtra(com.attention.app.reminder.ReminderReceiver.EXTRA_ID),
                )
            }
        }
    }
}
