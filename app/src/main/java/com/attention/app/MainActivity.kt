package com.attention.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.attention.app.data.DataStoreSettingsRepository
import com.attention.app.data.SettingsRepository
import com.attention.domain.PlannerSettings
import java.time.DayOfWeek
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private val Context.settingsDataStore by preferencesDataStore(name = "attention_settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AttentionApp() }
    }
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings: StateFlow<PlannerSettings> = repository.settings.stateIn(
        scope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
        PlannerSettings(),
    )

    fun setBoundary(minutes: Int) = scope.launch { repository.setPlanningDayBoundary(minutes) }
    fun setWeekStart(day: DayOfWeek) = scope.launch { repository.setWeekStartDay(day) }
    fun setCapacity(minutes: Int?) = scope.launch { repository.setDailyCapacity(minutes) }

    override fun onCleared() {
        scope.cancel()
        super.onCleared()
    }
}

private class SettingsViewModelFactory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(repository) as T
}

@Composable
fun AttentionApp() {
    val context = LocalContext.current
    val repository = remember { DataStoreSettingsRepository(context.settingsDataStore) }
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModelFactory(repository))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(settings, viewModel, onBack = { showSettings = false })
    } else {
        TodayScreen(settings, onOpenSettings = { showSettings = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayScreen(settings: PlannerSettings, onOpenSettings: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今天") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("今天工作台", style = MaterialTheme.typography.headlineMedium)
            Text("规划日界线 ${settings.planningDayBoundary} · 每周从${settings.weekStartDay.chineseName()}开始")
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("每日容量", style = MaterialTheme.typography.titleMedium)
                    Text(settings.dailyCapacityMinutes?.let { "$it 分钟" } ?: "未设置")
                }
            }
            Text("今天还没有日程。先记录你准备投入的时间。")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(settings: PlannerSettings, viewModel: SettingsViewModel, onBack: () -> Unit) {
    var boundaryText by remember(settings.planningDayBoundaryMinutes) {
        mutableStateOf(settings.planningDayBoundary)
    }
    var capacityText by remember(settings.dailyCapacityMinutes) {
        mutableStateOf(settings.dailyCapacityMinutes?.toString() ?: "")
    }
    Scaffold(topBar = { TopAppBar(title = { Text("设置") }, navigationIcon = {
        OutlinedButton(onClick = onBack, modifier = Modifier.padding(start = 8.dp)) { Text("返回") }
    }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("规划日界线", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = boundaryText,
                onValueChange = { boundaryText = it },
                label = { Text("时间（HH:mm）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = { boundaryText.toMinutes()?.let(viewModel::setBoundary) }) { Text("保存界线") }

            Text("周起始日", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { day ->
                    if (day == settings.weekStartDay) Button(onClick = {}) { Text(day.chineseName()) }
                    else OutlinedButton(onClick = { viewModel.setWeekStart(day) }) { Text(day.chineseName()) }
                }
            }

            Text("每日容量", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = capacityText,
                onValueChange = { capacityText = it.filter(Char::isDigit) },
                label = { Text("分钟，可留空清除") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val capacity = capacityText.toIntOrNull()
                    when {
                        capacityText.isBlank() -> viewModel.setCapacity(null)
                        capacity != null && capacity > 0 -> viewModel.setCapacity(capacity)
                    }
                }) { Text("保存容量") }
                OutlinedButton(onClick = { capacityText = ""; viewModel.setCapacity(null) }) { Text("清除") }
            }
            Spacer(Modifier.height(8.dp))
            Text("这些设置只保存在本机，无需网络连接。", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun String.toMinutes(): Int? {
    val parts = split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return (hour * 60 + minute).takeIf { hour in 0..23 && minute in 0..59 }
}

private fun DayOfWeek.chineseName(): String = when (this) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}
