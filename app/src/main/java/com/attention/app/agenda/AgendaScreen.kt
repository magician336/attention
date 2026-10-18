package com.attention.app.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.attention.app.ui.components.AttentionButton
import com.attention.app.ui.components.AttentionConfirmDialog
import com.attention.app.ui.components.AttentionErrorState
import com.attention.app.ui.components.AttentionInlineError
import com.attention.app.ui.components.AttentionLoadingState
import com.attention.app.ui.components.AttentionOutlinedButton
import com.attention.app.ui.components.AttentionSelectedIndicator
import com.attention.app.ui.components.AttentionTextButton
import com.attention.app.ui.components.AttentionTouchTargetMinSize
import com.attention.app.ui.format.DurationFormat
import com.attention.app.ui.format.TimeEntrySourceFormat
import com.attention.app.ui.theme.attentionTokens
import com.attention.domain.ScheduleEntry
import com.attention.domain.ScheduleFrequency
import com.attention.workspace.state.AgendaActions
import com.attention.workspace.state.AgendaActiveTimer
import com.attention.workspace.state.AgendaBackfillDraft
import com.attention.workspace.state.AgendaFreeTimerOption
import com.attention.workspace.state.AgendaRangeScope
import com.attention.workspace.state.AgendaScheduleDraft
import com.attention.workspace.state.AgendaScreenState
import com.attention.workspace.state.AgendaScheduleRow
import com.attention.workspace.state.AgendaStopResult
import com.attention.workspace.state.AgendaTargetOption
import com.attention.workspace.state.AgendaTimeRecord
import com.attention.workspace.state.AgendaWorkbenchSection
import com.attention.workspace.state.AGENDA_RETRY_LABEL
import com.attention.workspace.state.agendaLabel
import com.attention.workspace.state.agendaWeekdayLabel
import java.time.LocalDate

/**
 * 日程页。它只接收自己的 [AgendaScreenState] 与 [AgendaActions]：
 * 不读完整领域状态，不读 ViewModel，也不读 Android API 与系统时间——
 * 规划日、日程条目、每日容量与提醒时间都由组合根注入的投影算好。
 *
 * 原来的今天工作台与日程页在这一页合并：今天、明天、所有日期与任意规划日
 * 是同一页上的日期范围，共享同一套列表、编辑、完成、提醒与从条目开始计时的交互。
 *
 * ## 首屏顺序
 *
 * 页面按投影给出的 [AgendaScreenState.workbenchSections] 自上而下渲染：
 * 规划日与每日容量摘要、时间轴、进行中的计时状态、自由计时、事后补记，
 * 重复日程作为折叠的次级入口排在最后；添加与补记的主入口常驻在页面右下角。
 * 页面因此没有自己的顺序副本，
 * 「首屏固定为这个顺序」是一条由投影与测试共同钉住的约定。
 *
 * 提醒权限不在这里申请：页面只发出 [AgendaActions.requestReminderPermission] intent，
 * 权限检查与申请由组合根 adapter 完成（ADR-0005 第 4 节「Android adapter 归属组合根」）。
 */
@Composable
internal fun AgendaScreen(
    state: AgendaScreenState,
    actions: AgendaActions,
    openScheduleId: String? = null,
) {
    val tokens = attentionTokens()
    val today = state.planningDate.toString()
    var date by remember(state.selectedDate) { mutableStateOf(state.selectedDate?.toString() ?: today) }
    var dateMenuExpanded by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf(false) }
    val scheduleForm = rememberAgendaScheduleForm()
    val recurrenceForm = rememberAgendaRecurrenceForm()
    // 重复日程折叠在页面 module 内：它是次级入口，能力没有删除，只是收在一次点击之后。
    val disclosure = rememberAgendaDisclosureState()
    val scrollState = rememberScrollState()
    var addSheetOpen by remember { mutableStateOf(false) }
    var freeTimerSheetOpen by remember { mutableStateOf(false) }
    var addMoreExpanded by remember { mutableStateOf(false) }
    var addBackfillExpanded by remember { mutableStateOf(false) }
    var editingRow by remember { mutableStateOf<AgendaScheduleRow?>(null) }
    var editingRecord by remember { mutableStateOf<AgendaTimeRecord?>(null) }
    var pendingDeleteRecordId by remember { mutableStateOf<String?>(null) }
    var notificationOpenHandled by remember(openScheduleId) { mutableStateOf(false) }
    LaunchedEffect(openScheduleId, state.pendingEditRow) {
        if (!notificationOpenHandled && state.pendingEditRow != null) {
            editingRow = state.pendingEditRow
            notificationOpenHandled = true
        }
    }
    // 面板里的规划日默认落在正在看的那一天：投影已经算好，页面不自己判断「哪一天」。
    val sheetDate = state.addScheduleDefaultDate.ifBlank { date }
    fun selectDate(value: LocalDate?) {
        actions.selectPlanningDate(value)
        date = value?.toString() ?: today
        dateError = false
    }
    fun openPlanningDay() {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
        if (parsed == null) dateError = true else selectDate(parsed)
    }
    fun closeAddSheet() {
        addSheetOpen = false
        addMoreExpanded = false
        addBackfillExpanded = false
    }

    // 主操作与两张面板共用同一套打开方式，避免 flag 组合在多处漂移。
    fun openAddSchedule() {
        addMoreExpanded = false
        addBackfillExpanded = false
        addSheetOpen = true
    }

    fun openBackfill() {
        addMoreExpanded = false
        addBackfillExpanded = true
        addSheetOpen = true
    }

    fun openFreeTimer() {
        addSheetOpen = false
        freeTimerSheetOpen = true
    }

    val loadError = state.loadError
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = tokens.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 读取本地数据的两件事都是组合根注入的事实，页面只按它们渲染（ADR-0005 第 1 节）：
            // 还没读到给轻量时间轴骨架，读失败给带「重试」的错误状态。两者都不重排区块——
            // 骨架复用同一批区块容器标识，因此首屏结构与顺序在三种状态下始终一致。
            if (loadError != null) {
                AgendaLoadFailure(loadError = loadError, onRetry = actions::retryLoad)
            } else if (state.loading) {
                AgendaLoadingSkeleton()
            } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val wideLayout = maxWidth >= 600.dp
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    state.workbenchSections.forEach { section ->
                        when (section) {
                    AgendaWorkbenchSection.PLANNING_DAY_SUMMARY -> AgendaSummarySection(
                        state = state,
                        date = date,
                        dateError = dateError,
                        onDateChange = { value -> date = value; dateError = false },
                        onOpenPlanningDay = ::openPlanningDay,
                        onSelectDate = ::selectDate,
                        dateMenuExpanded = dateMenuExpanded,
                        onDateMenuChange = { dateMenuExpanded = it },
                        actions = actions,
                    )

                    AgendaWorkbenchSection.TIMELINE -> if (wideLayout) {
                        // 宽屏把时间轴与辅助操作栈并排；左列仍保留时间轴与运行计时的先序，
                        // 这样读屏与窄屏都沿着「今天发生什么 → 下一步做什么」的顺序走。
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.medium),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                AgendaTimelineSection(
                                    state = state,
                                    actions = actions,
                                    onEditRow = { editingRow = it },
                                    includeUnscheduled = false,
                                )
                                if (state.activeTimer != null) {
                                    // Compatibility seam: the running card now lives inside the
                                    // first timeline row, so this marker has no second visual card.
                                    Box(Modifier.height(0.dp).testTag("agenda-section-timer"))
                                }
                            }
                            AgendaAuxiliaryStack(
                                state = state,
                                actions = actions,
                                onEditRow = { editingRow = it },
                                onOpenFreeTimer = ::openFreeTimer,
                            )
                        }
                    } else {
                        AgendaTimelineSection(
                            state = state,
                            actions = actions,
                            onEditRow = { editingRow = it },
                            includeUnscheduled = false,
                        )
                    }

                    AgendaWorkbenchSection.RUNNING_TIMER -> if (!wideLayout) state.activeTimer?.let { _ ->
                        // The running controls are rendered in the first timeline row. Keep the
                        // section seam for ordering and integrations without duplicating the card.
                        Box(Modifier.height(0.dp).testTag("agenda-section-timer"))
                    }

                    AgendaWorkbenchSection.ADD_SCHEDULE -> if (!wideLayout) {
                        // 窄屏也复用原型 A 的辅助栈：下一步、自由计时与未安排时间保持同一组结构；
                        // 添加与补记的入口统一常驻在右下角的「调整计划」。
                        AgendaAuxiliaryStack(
                            state = state,
                            actions = actions,
                            onEditRow = { editingRow = it },
                            onOpenFreeTimer = ::openFreeTimer,
                            compact = true,
                        )
                    }

                    AgendaWorkbenchSection.BACKFILL -> if (state.timeRecords.isNotEmpty()) {
                        AgendaBackfillSection(
                            state = state,
                            editingRecord = editingRecord,
                            onEditRecord = { editingRecord = it },
                            onStopEditingRecord = { editingRecord = null },
                            onDeleteRecord = { pendingDeleteRecordId = it },
                            actions = actions,
                        )
                    } else {
                        // 原型 A 的空态只保留时间轴说明和统一操作卡；保留零高度 seam，
                        // 让既有集成仍能定位这个能力，但不再渲染一个空的「事后补记」标题。
                        Box(Modifier.height(0.dp).testTag("agenda-section-backfill"))
                    }

                    AgendaWorkbenchSection.RECURRENCE -> AgendaRecurrenceSection(
                        state = state,
                        form = recurrenceForm,
                        expanded = disclosure.recurrenceVisible,
                        onToggle = disclosure::toggleRecurrence,
                        date = date,
                        actions = actions,
                    )
                        }
                    }
                }
            }
            }
            // 停止计时的结果面板：只在刚刚停止之后出现，四个要素与两个决定都来自投影。
            state.stopResult?.let { result ->
                AgendaStopResultSection(result = result, actions = actions)
            }
            // 悬浮主操作常驻在内容之上；给滚动内容留出它的高度，最后一项不会被遮住。
            Spacer(Modifier.height(AttentionTouchTargetMinSize + 32.dp))
        }
        if (loadError == null && !state.loading) {
            AgendaPlanAction(
                onAddSchedule = ::openAddSchedule,
                onBackfill = ::openBackfill,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
            )
        }
    }
    state.timeRecords.firstOrNull { it.entryId == pendingDeleteRecordId }?.let { pendingDelete ->
        // 删除实际投入是破坏性操作，先确认影响；撤销入口由组合根的提示宿主提供。
        AttentionConfirmDialog(
            visible = true,
            title = "删除这条实际投入",
            impact = "删除后这 ${DurationFormat.ofMinutes(pendingDelete.durationMinutes)} 的实际投入不再计入目标进度、投入经验与统计。" +
                "删除后仍可以在底部提示里撤销一次。",
            confirmLabel = "删除",
            onConfirm = {
                actions.deleteTime(pendingDelete.entryId)
                pendingDeleteRecordId = null
                if (editingRecord?.entryId == pendingDelete.entryId) editingRecord = null
            },
            onDismiss = { pendingDeleteRecordId = null },
        )
    }
    editingRow?.let { row ->
        AgendaScheduleEditDialog(
            row = row,
            targets = state.targets.map { it.targetId to it.title },
            onDismiss = { editingRow = null },
            onSave = { updated, reminderMinute, reminderRequested, backfill ->
                if (reminderRequested) actions.requestReminderPermission()
                if (backfill == null) actions.updateSchedule(updated, reminderMinute)
                else actions.updateScheduleWithBackfill(updated, reminderMinute, backfill)
                editingRow = null
            },
        )
    }
    // 两块底部面板都是 Material 3 的 sheet：表单控件与选择器都在页面 module 内，
    // 页面因此不需要任何 Android API（ADR-0005 第 4 节）。
    AgendaAddScheduleSheet(
        state = state,
        form = scheduleForm,
        visible = addSheetOpen,
        initialPlanningDay = sheetDate,
        moreExpanded = addMoreExpanded,
        onToggleMore = { addMoreExpanded = !addMoreExpanded },
        backfillExpanded = addBackfillExpanded,
        onToggleBackfill = { addBackfillExpanded = !addBackfillExpanded },
        onDismiss = ::closeAddSheet,
        onSubmit = { draft, reminderRequested, backfill ->
            if (backfill == null) actions.addSchedule(draft)
            else actions.addScheduleWithBackfill(draft, backfill)
            if (reminderRequested) actions.requestReminderPermission()
            closeAddSheet()
        },
    )
    AgendaFreeTimerSheet(
        options = state.freeTimerOptions,
        visible = freeTimerSheetOpen,
        onStart = { targetId ->
            actions.startFreeTimer(targetId)
            freeTimerSheetOpen = false
        },
        onDismiss = { freeTimerSheetOpen = false },
    )
}

/**
 * 常驻的「调整计划」主操作。
 *
 * 它固定在页面内容区的右下角，不随滚动移走；菜单只承担添加与补记两项。
 * 行内修改走时间轴行自己的菜单，因此这里不再放「修改日程」。
 */
@Composable
private fun AgendaPlanAction(
    onAddSchedule: () -> Unit,
    onBackfill: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    var expanded by remember { mutableStateOf(false) }
    val fabShape = ButtonDefaults.shape
    Box(modifier) {
        AttentionButton(
            onClick = { expanded = true },
            modifier = Modifier
                .testTag("agenda-plan-action")
                .shadow(6.dp, fabShape),
            shape = fabShape,
        ) {
            Text("调整计划")
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.padding(start = tokens.spacing.small).size(18.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .widthIn(min = 220.dp, max = 280.dp)
                .testTag("agenda-plan-menu"),
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("添加日程")
                        Text(
                            "新建一条计划",
                            style = tokens.typography.caption,
                            color = tokens.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onAddSchedule()
                },
                modifier = Modifier.testTag("agenda-plan-menu-add"),
            )
            DropdownMenuItem(
                text = {
                    Column {
                        Text("事后补记")
                        Text(
                            "把已经发生的投入记下来",
                            style = tokens.typography.caption,
                            color = tokens.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                onClick = {
                    expanded = false
                    onBackfill()
                },
                modifier = Modifier.testTag("agenda-plan-menu-backfill"),
            )
        }
    }
}

/**
 * 首次读取本地数据时的轻量时间轴骨架。
 *
 * 读取期间 [AgendaScreenState] 里的行与标签都还是空投影，因此这里画的不是「提前渲染真实区块」，
 * 而是复用同一批区块容器标识的占位：用户看到的位置与读上来之后一致，页面的高度不会在
 * 加载结束时突然跳一下。首屏区块的顺序语义没有被改动，骨架只是同一结构的一种填充状态。
 */
@Composable
private fun AgendaLoadingSkeleton() {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxWidth().testTag("agenda-skeleton"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AttentionLoadingState(
            label = "正在读取本地数据",
            description = "马上就好",
        )
        // 容量摘要与时间轴两块在读上来之后就在这个位置，因此骨架占住同样的容器标识。
        Column(Modifier.fillMaxWidth().testTag("agenda-section-summary"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(2) { AgendaSkeletonBar(tokens.spacing.medium) }
        }
        Column(Modifier.fillMaxWidth().testTag("agenda-section-timeline"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(4) { AgendaSkeletonBar(tokens.spacing.large) }
        }
    }
}

/** 一条骨架占位行。它不带任何文案，因此读屏用户听到的是加载状态而不是一串假内容。 */
@Composable
private fun AgendaSkeletonBar(height: androidx.compose.ui.unit.Dp) {
    val tokens = attentionTokens()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .testTag("agenda-skeleton-row"),
        shape = RoundedCornerShape(tokens.radius.small),
        color = tokens.colorScheme.surfaceContainerHigh,
        content = {},
    )
}

/**
 * 本地数据读取失败时的错误状态。
 *
 * 它复用共享的 [AttentionErrorState]，因此错误在任意页面都是同一套标识与同一套表达；
 * 动作是「重试」而不是只有关闭——用户自己能救回来，不必退出应用（#195 验收标准 8）。
 */
@Composable
private fun AgendaLoadFailure(loadError: String, onRetry: () -> Unit) {
    AttentionErrorState(
        description = loadError,
        retryLabel = AGENDA_RETRY_LABEL,
        onRetry = onRetry,
    )
}

/**
 * 首屏第一块：规划日与每日容量的只读摘要，外加切换查看范围的导航。
 *
 * 它只读不写——完整设置继续走统计页底部的「设置与数据」，因此这里没有任何设置表单。
 * 容量摘要本身是那个入口：点它发出 [AgendaActions.openSettings] 意图，实际跳转由组合根完成。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaSummarySection(
    state: AgendaScreenState,
    date: String,
    dateError: Boolean,
    onDateChange: (String) -> Unit,
    onOpenPlanningDay: () -> Unit,
    onSelectDate: (LocalDate?) -> Unit,
    dateMenuExpanded: Boolean,
    onDateMenuChange: (Boolean) -> Unit,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    val summary = state.workbenchSummary
    val itemCount = state.timelineRows.size + state.unscheduledRows.size +
        state.dateSections.sumOf { it.timelineRows.size + it.unscheduledRows.size }
    val compactDateLabel = when {
        state.selectedDate == null -> "所有日期"
        state.selectedDate == state.planningDate -> "今天"
        state.selectedDate == state.planningDate.plusDays(1) -> "明天"
        else -> state.selectedDate.toString()
    }
    val compactCapacityStatus = summary.capacityStatusLabel
        ?.replace("每日容量未设置", "未设置")
        ?.replace("暂无多日数据", "暂无数据")
    val plannedMinutes = summary.plannedMinutes ?: 0
    val actualMinutes = summary.actualMinutes ?: 0
    val statusLabel = when {
        summary.capacityStatusLabel?.contains("超载") == true ||
            summary.capacityStatusLabel?.contains("超出") == true -> "超载"
        actualMinutes > 0 -> "进行中"
        plannedMinutes > 0 -> "未开始"
        else -> "空闲"
    }
    val statusColor = when (statusLabel) {
        "进行中" -> tokens.colorScheme.primary
        "超载" -> tokens.colorScheme.error
        else -> tokens.colorScheme.outline
    }
    fun compactDuration(minutes: Int): String = DurationFormat.ofMinutes(minutes)
        .replace(" ", "")
        .replace("分钟", "分")

    val progress = if (statusLabel == "未开始" || statusLabel == "空闲") {
        0f
    } else {
        summary.capacityProgress?.coerceIn(0f, 1f) ?: 0f
    }
    Column(Modifier.fillMaxWidth().testTag("agenda-section-summary"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 日期切换收进一个入口，首屏只保留当前范围；这样窄屏不会出现横向滚动的按钮串。
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .testTag("agenda-filter")
                    .semantics { text = AnnotatedString(state.filterLabel) },
            ) {
                AttentionTextButton(
                    onClick = { onDateMenuChange(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                    .testTag("agenda-date-menu"),
                ) {
                    Text(
                        compactDateLabel,
                        style = tokens.typography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { text = AnnotatedString(state.filterLabel) },
                    )
                }
                DropdownMenu(expanded = dateMenuExpanded, onDismissRequest = { onDateMenuChange(false) }) {
                    Text("查看日期", style = tokens.typography.caption, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    state.ranges.forEach { range ->
                        val tag = when (range.scope) {
                            AgendaRangeScope.TODAY -> "agenda-range-today"
                            AgendaRangeScope.TOMORROW -> "agenda-range-tomorrow"
                            AgendaRangeScope.ALL_DATES -> "agenda-range-all-dates"
                        }
                        DropdownMenuItem(
                            text = { Text(range.label) },
                            onClick = {
                                if (range.scope == AgendaRangeScope.ALL_DATES) actions.selectAllDates()
                                else range.date?.let(actions::selectPlanningDate)
                                onDateMenuChange(false)
                            },
                            modifier = Modifier.testTag(tag),
                        )
                    }
                    state.availableDates
                        .filterNot { option -> state.ranges.any { it.date == option.date } }
                        .forEach { availableDate ->
                            DropdownMenuItem(
                                text = { Text(availableDate.label) },
                                onClick = { onSelectDate(availableDate.date); onDateMenuChange(false) },
                                modifier = Modifier.testTag("agenda-date-${availableDate.date}"),
                            )
                        }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            date,
                            onDateChange,
                            label = { Text("规划日") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            singleLine = true,
                            isError = dateError,
                        )
                        AttentionTextButton(
                            onClick = { onOpenPlanningDay(); onDateMenuChange(false) },
                            modifier = Modifier
                                .align(Alignment.End)
                                .testTag("agenda-open-planning-day"),
                        ) { Text("打开") }
                    }
                }
            }
            AttentionTextButton(
                onClick = actions::openSettings,
                modifier = Modifier.testTag("agenda-summary-settings"),
            ) { Text("设置", style = tokens.typography.caption) }
        }
        // 摘要是一条轻量信息带：不再用大卡片和长说明堆叠，四个读数固定在同一行。
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = actions::openSettings)
                .testTag("agenda-open-settings")
                .semantics {
                    text = AnnotatedString(
                        listOfNotNull(summary.capacityUsageLabel, summary.capacityStatusLabel, statusLabel)
                            .joinToString(" "),
                    )
                    contentDescription = listOfNotNull(
                        summary.capacityMinutes?.let { "每日容量 $it 分钟" } ?: "每日容量未设置",
                        summary.plannedMinutes?.let { "已排时间 $it 分钟" } ?: "已排时间 0 分钟",
                        summary.actualMinutes?.let { "实际投入 $it 分钟" } ?: "实际投入 0 分钟",
                        summary.capacityStatusLabel,
                        "状态 $statusLabel",
                    ).joinToString("，")
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.colorScheme.outlineVariant))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag("agenda-capacity-summary"),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1.05f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(compactDateLabel, style = tokens.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$itemCount 项", style = tokens.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.width(1.dp).height(38.dp).background(tokens.colorScheme.outlineVariant))
                AgendaSummaryMetric(
                    label = "已排",
                    value = summary.plannedMinutes?.let(::compactDuration) ?: "0分",
                    semanticLabel = "已排时间",
                    modifier = Modifier.weight(1f).testTag("agenda-summary-planned"),
                )
                Box(Modifier.width(1.dp).height(38.dp).background(tokens.colorScheme.outlineVariant))
                AgendaSummaryMetric(
                    label = "实际",
                    value = summary.actualMinutes?.let(::compactDuration) ?: "0分",
                    semanticLabel = "实际投入",
                    modifier = Modifier.weight(1f).testTag("agenda-summary-actual"),
                )
                Box(Modifier.width(1.dp).height(38.dp).background(tokens.colorScheme.outlineVariant))
                Column(
                    Modifier
                        .weight(1.25f)
                        .testTag("agenda-summary-capacity")
                        .semantics {
                            text = AnnotatedString(
                                listOfNotNull(
                                    summary.capacityMinutes?.let { "每日容量 ${DurationFormat.ofMinutes(it)}" },
                                    compactCapacityStatus,
                                    "状态 $statusLabel",
                                ).joinToString(" "),
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.testTag("agenda-summary-capacity-status"),
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(statusColor, CircleShape)
                                .testTag("agenda-summary-status-dot"),
                        )
                        Text(statusLabel, style = tokens.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(tokens.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
                            .testTag("agenda-capacity-progress"),
                    ) {
                        if (progress > 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress)
                                    .height(4.dp)
                                    .background(statusColor, RoundedCornerShape(2.dp)),
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(tokens.colorScheme.outlineVariant))
        }
    }
}

/** 原型 A 的日期范围选中态使用中性纸色；完成绿色只表达已完成，不借给筛选状态。 */
@Composable
private fun AgendaSelectedRangeIndicator(
    label: String,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    Surface(
        shape = RoundedCornerShape(tokens.radius.small),
        color = tokens.colorScheme.surfaceVariant,
        contentColor = tokens.colorScheme.onSurface,
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = tokens.spacing.medium, vertical = tokens.spacing.small),
        ) {
            Icon(Icons.Filled.Check, contentDescription = "已选中", modifier = Modifier.size(16.dp))
            Text(label)
        }
    }
}

@Composable
private fun AgendaSummaryMetric(
    label: String,
    value: String,
    semanticLabel: String? = null,
    supporting: String? = null,
    progress: Float? = null,
    modifier: Modifier = Modifier,
) {
    val tokens = attentionTokens()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = tokens.typography.caption,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = semanticLabel?.let { Modifier.semantics { text = AnnotatedString(it) } } ?: Modifier,
        )
        Text(value, style = tokens.typography.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        supporting?.let { Text(it, style = tokens.typography.caption, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        progress?.let {
            LinearProgressIndicator(
                progress = { it },
                modifier = Modifier.fillMaxWidth().height(4.dp).testTag("agenda-capacity-progress"),
            )
        }
    }
}

/**
 * 宽屏时间轴右侧的辅助操作栈。它只消费投影里的下一步与未安排事实，
 * 不在页面里重新排序日程；添加与补记由常驻的「调整计划」承担。
 */
@Composable
private fun AgendaAuxiliaryStack(
    state: AgendaScreenState,
    actions: AgendaActions,
    onEditRow: (AgendaScheduleRow) -> Unit,
    onOpenFreeTimer: () -> Unit,
    compact: Boolean = false,
) {
    val unscheduledRows = state.unscheduledRows.ifEmpty {
        state.dateSections.flatMap { it.unscheduledRows }
    }
    val timelineVisible = state.timelineRows.isNotEmpty() ||
        state.dateSections.any { it.timelineRows.isNotEmpty() } ||
        state.activeTimer != null
    val layoutModifier = if (compact) Modifier.fillMaxWidth() else Modifier.widthIn(min = 240.dp, max = 300.dp)
    Column(
        layoutModifier.testTag("agenda-side-stack"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 空态不抢首屏焦点：没有时间轴内容时只保留说明和统一操作入口。
        if (timelineVisible) {
            AgendaNextStepCard(state)
        }
        AgendaOperationStack(onOpenFreeTimer = onOpenFreeTimer)
        if (unscheduledRows.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(attentionTokens().colorScheme.outlineVariant))
            Column(
                Modifier.fillMaxWidth().testTag("agenda-side-unscheduled"),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("未安排", style = attentionTokens().typography.body)
                unscheduledRows.forEach { row ->
                    AgendaScheduleRowCard(
                        row = row,
                        actions = actions,
                        onEdit = { onEditRow(row) },
                        secondary = true,
                    )
                }
            }
        }
    }
}

/** 下一步由当前时间轴投影驱动；没有可执行条目时也保留稳定空态。 */
@Composable
private fun AgendaNextStepCard(state: AgendaScreenState) {
    val tokens = attentionTokens()
    val timelineRows = state.timelineRows.ifEmpty { state.dateSections.flatMap { it.timelineRows } }
    val next = timelineRows.firstOrNull { !it.timing && !it.completed }
    Column(
        Modifier.fillMaxWidth().testTag("agenda-next-step"),
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        Text("下一步", style = tokens.typography.caption, color = tokens.colorScheme.onSurfaceVariant)
        if (next == null) {
            Text("暂无下一步", style = tokens.typography.body, modifier = Modifier.testTag("agenda-next-step-empty"))
        } else {
            Text(next.title, style = tokens.typography.body, modifier = Modifier.testTag("agenda-next-step-title"))
            Text(next.detailLabel, style = tokens.typography.caption)
        }
    }
}

/**
 * A 的次级操作入口：自由计时。
 *
 * 窄屏只渲染这一列；宽屏由辅助栈复用同一件组件。添加与补记已经上移到常驻的
 * 「调整计划」，这里保留自由计时，避免把它塞进菜单后多一层点击。
 */
@Composable
private fun AgendaOperationStack(
    onOpenFreeTimer: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("agenda-section-add"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .testTag("agenda-operation-stack"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AttentionTextButton(
                onClick = onOpenFreeTimer,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("agenda-operation-free-timer")
                    .semantics { contentDescription = "开始自由计时" },
            ) { Text("开始自由计时") }
        }
    }
}

/**
 * 首屏第二块：时间轴。有具体时间的日程按开始/结束时间排列，正在计时的固定在最前；
 * 没有具体时间的日程进入独立的「未安排时间」区域。当天没有日程时给出「安排今天第一件事」。
 *
 * 顺序、行上的计划投入与实际投入、以及「已超出容量」提示都来自投影，页面只按给定顺序渲染。
 */
@Composable
private fun AgendaTimelineSection(
    state: AgendaScreenState,
    actions: AgendaActions,
    onEditRow: (AgendaScheduleRow) -> Unit,
    includeUnscheduled: Boolean = true,
) {
    val tokens = attentionTokens()
    Column(Modifier.fillMaxWidth().testTag("agenda-section-timeline"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 规划日空态只保留一条说明和一个入口；所有日期视图仍保留原有说明 seam。
        state.emptyLabel
            ?.takeUnless { state.workbenchEmptyLabel != null && state.dateSections.isEmpty() }
            ?.let { emptyLabel ->
            Text(
                emptyLabel,
                style = tokens.typography.caption,
                color = tokens.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("agenda-empty"),
            )
            }
        if (state.dateSections.isEmpty()) {
            if (state.timelineRows.isEmpty() && state.unscheduledRows.isEmpty()) {
                    state.workbenchEmptyLabel?.let { emptyLabel ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.testTag("agenda-workbench-empty")) {
                        Text(emptyLabel, style = tokens.typography.caption, color = tokens.colorScheme.onSurfaceVariant)
                    }
                }
            }
            AgendaTimelineRows(
                rows = state.timelineRows,
                actions = actions,
                onEditRow = onEditRow,
                activeTimer = state.activeTimer,
                timerTargets = state.targets,
            )
            if (includeUnscheduled) AgendaUnscheduledGroup(state.unscheduledRows, actions, onEditRow)
        } else {
            val timerSectionIndex = state.dateSections.indexOfFirst { section ->
                section.timelineRows.any(AgendaScheduleRow::timing) || section.timelineRows.isNotEmpty()
            }
            state.dateSections.forEachIndexed { sectionIndex, section ->
                Text("规划日 ${section.planningDate}", style = tokens.typography.body)
                Text(
                    section.capacityLabel,
                    style = tokens.typography.caption,
                    color = tokens.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("agenda-capacity-summary-${section.planningDate}"),
                )
                AgendaTimelineRows(
                    rows = section.timelineRows,
                    actions = actions,
                    onEditRow = onEditRow,
                    activeTimer = state.activeTimer?.takeIf { sectionIndex == timerSectionIndex },
                    timerTargets = state.targets,
                )
                if (includeUnscheduled) AgendaUnscheduledGroup(section.unscheduledRows, actions, onEditRow)
            }
        }
    }
}

@Composable
private fun AgendaTimelineRows(
    rows: List<AgendaScheduleRow>,
    actions: AgendaActions,
    onEditRow: (AgendaScheduleRow) -> Unit,
    activeTimer: AgendaActiveTimer? = null,
    timerTargets: List<AgendaTargetOption> = emptyList(),
) {
    if (rows.isEmpty() && activeTimer == null) return
    val tokens = attentionTokens()
    val timelineGutterWidth = 48.dp
    val timelineNodeGap = 24.dp
    val timelineNodeSize = 10.dp
    // 时间轴是主画布：只用一条细轴和节点分隔，不再把每一行包成大卡片。
    Column(
        Modifier
            .fillMaxWidth()
            .testTag("agenda-timeline-tree")
            .padding(vertical = 2.dp)
            .drawBehind {
                val axisX = 12.dp.toPx() + timelineGutterWidth.toPx() + timelineNodeGap.toPx() + timelineNodeSize.toPx() / 2
                drawLine(
                    color = tokens.colorScheme.outlineVariant,
                    start = Offset(axisX, 0f),
                    end = Offset(axisX, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            },
        verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
    ) {
        if (rows.isEmpty()) {
            AgendaRunningTimerStandalone(timer = activeTimer!!, targets = timerTargets, actions = actions)
        }
        rows.forEachIndexed { index, row ->
            val nodeColor = when {
                row.timing -> tokens.brand.timingInProgress.accent
                row.completed -> tokens.brand.completed.accent
                else -> tokens.brand.scheduled.accent
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(timelineNodeGap),
            ) {
                Text(
                    text = if (row.timing) "现在" else row.startText.ifBlank { "—" },
                    style = tokens.typography.caption,
                    color = tokens.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(timelineGutterWidth).padding(top = 8.dp),
                )
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .size(timelineNodeSize)
                        .background(nodeColor, CircleShape)
                        .border(2.dp, tokens.colorScheme.background, CircleShape),
                )
                Box(Modifier.weight(1f)) {
                    AgendaScheduleRowCard(
                        row = row,
                        actions = actions,
                        onEdit = { onEditRow(row) },
                        activeTimer = activeTimer?.takeIf { row.timing || index == 0 },
                        timerTargets = timerTargets,
                    )
                }
            }
        }
    }
}

/** 没有具体时间的日程。它们仍然属于这一天，只是不假装有一个固定时段。 */
@Composable
private fun AgendaUnscheduledGroup(
    rows: List<AgendaScheduleRow>,
    actions: AgendaActions,
    onEditRow: (AgendaScheduleRow) -> Unit,
) {
    if (rows.isEmpty()) return
    val tokens = attentionTokens()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("agenda-unscheduled")) {
        Text("未安排时间", style = tokens.typography.body, modifier = Modifier.testTag("agenda-unscheduled-heading"))
        rows.forEach { row ->
            AgendaScheduleRowCard(row, actions = actions, onEdit = { onEditRow(row) }, secondary = true)
        }
    }
}

/** 没有对应日程条目的自由计时也固定在时间轴第一行，避免恢复旧的独立计时卡。 */
@Composable
private fun AgendaRunningTimerStandalone(
    timer: AgendaActiveTimer,
    targets: List<AgendaTargetOption>,
    actions: AgendaActions,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text("现在", style = attentionTokens().typography.caption, modifier = Modifier.width(48.dp).padding(top = 4.dp))
        Box(
            Modifier
                .padding(top = 8.dp)
                .size(10.dp)
                .background(attentionTokens().brand.timingInProgress.accent, CircleShape),
        )
        Card(
            Modifier.weight(1f).testTag("agenda-running-timer"),
            shape = RoundedCornerShape(attentionTokens().radius.medium),
            colors = CardDefaults.cardColors(containerColor = attentionTokens().brand.timingInProgress.container),
        ) {
            Column(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("schedule-timing-cue")) {
                    Text("正在计时：${timer.targetLabel}", style = attentionTokens().typography.caption)
                }
                Box(
                    Modifier
                        .background(attentionTokens().brand.timingInProgress.container, RoundedCornerShape(attentionTokens().radius.small))
                        .padding(4.dp),
                ) {
                    Icon(
                        if (timer.paused) Icons.Filled.PauseCircle else Icons.Filled.Timer,
                        contentDescription = timer.statusLabel,
                        tint = attentionTokens().brand.timingInProgress.onContainer,
                        modifier = Modifier.size(16.dp).testTag("agenda-running-timer-tint"),
                    )
                }
                Text(timer.statusLabel, modifier = Modifier.testTag("agenda-running-timer-status"))
                Text(timer.elapsedLabel, modifier = Modifier.testTag("agenda-running-timer-elapsed"))
                timer.pendingHintLabel?.let { hint ->
                    Text(hint, modifier = Modifier.testTag("agenda-running-timer-hint"))
                }
                AgendaRunningTimerControls(timer = timer, targets = targets, actions = actions)
            }
        }
    }
}

/**
 * 运行中日程的紧凑控制。它嵌在时间轴第一行的日程卡里，避免再渲染第二张计时卡。
 */
@Composable
private fun AgendaRunningTimerControls(
    timer: AgendaActiveTimer,
    targets: List<AgendaTargetOption>,
    actions: AgendaActions,
) {
    var timerMoreExpanded by remember(timer.targetId) { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (timer.paused) {
            AttentionButton(
                onClick = actions::resumeRunningTimer,
                modifier = Modifier.testTag("agenda-running-timer-resume"),
            ) { Text("恢复") }
        } else {
            AttentionOutlinedButton(
                onClick = actions::pauseRunningTimer,
                modifier = Modifier.testTag("agenda-running-timer-pause"),
            ) { Text("暂停") }
        }
        AttentionOutlinedButton(
            onClick = actions::stopRunningTimer,
            modifier = Modifier.testTag("agenda-running-timer-stop"),
        ) { Text("结束并保存") }
        Box {
            AttentionTextButton(
                onClick = { timerMoreExpanded = true },
                modifier = Modifier.testTag("agenda-running-timer-more"),
            ) { Text("更多") }
            DropdownMenu(
                expanded = timerMoreExpanded,
                onDismissRequest = { timerMoreExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("从此刻继续") },
                    onClick = { actions.recoverRunningTimerFromNow(); timerMoreExpanded = false },
                    modifier = Modifier.testTag("agenda-running-timer-recover"),
                )
                DropdownMenuItem(
                    text = { Text("切换到「未归属活动」") },
                    onClick = { actions.switchRunningTimerTarget(null); timerMoreExpanded = false },
                    modifier = Modifier.testTag("agenda-running-timer-switch-unowned"),
                )
                targets.forEach { target ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                "切换到「${target.title}」" +
                                    if (target.targetId == timer.targetId) "（当前）" else "",
                            )
                        },
                        onClick = {
                            actions.switchRunningTimerTarget(target.targetId)
                            timerMoreExpanded = false
                        },
                        modifier = Modifier.testTag("agenda-running-timer-switch-${target.targetId}"),
                    )
                }
            }
        }
    }
}

/**
 * 停止计时的结果面板。
 *
 * 四个要素（实际投入、计划时长、完成结果、记录归属）与「是否达标」都已经由投影给出，
 * 页面不比较任何分钟数，也不自己判断该不该完成。它只做两件事：把事实讲清楚，
 * 以及在「有计划且未达标」时把两个决定摆出来。
 *
 * 跨规划日的那次停止会产出多笔，面板逐笔如实呈现，不自行合成一笔。
 * 完成状态同时用文字与图标表达，不依赖颜色单独传达。
 */
@Composable
private fun AgendaStopResultSection(
    result: AgendaStopResult,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    Card(
        Modifier.fillMaxWidth().testTag("agenda-stop-result"),
        shape = RoundedCornerShape(tokens.radius.medium),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("本次计时结果", style = tokens.typography.title)
            Text(result.actualLabel, modifier = Modifier.testTag("agenda-stop-actual"))
            result.records.forEach { record ->
                Text(record.label, modifier = Modifier.testTag("agenda-stop-record-${record.entryId}"))
            }
            Text(result.plannedLabel, modifier = Modifier.testTag("agenda-stop-planned"))
            Text("记录归属：${result.attributionLabel}", modifier = Modifier.testTag("agenda-stop-attribution"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 完成状态不只靠颜色：图标与文字一起讲清楚是不是已经完成。
                Icon(
                    if (result.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (result.completed) "已完成" else "未完成",
                    tint = if (result.completed) tokens.colorScheme.primary else tokens.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    result.completionLabel,
                    modifier = Modifier.padding(start = tokens.spacing.small).testTag("agenda-stop-completion"),
                )
            }
            if (result.needsCompletionDecision) {
                Text(
                    "实际投入没有达到计划时长：完成与否由你决定。",
                    modifier = Modifier.testTag("agenda-stop-decision-hint"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AttentionOutlinedButton(
                        onClick = actions::dismissStopResult,
                        modifier = Modifier.testTag("agenda-stop-keep-incomplete"),
                    ) { Text("结束并保留未完成") }
                    AttentionButton(
                        onClick = {
                            result.scheduleEntryId?.let(actions::markStoppedScheduleCompleted)
                            actions.dismissStopResult()
                        },
                        modifier = Modifier.testTag("agenda-stop-mark-completed"),
                    ) { Text("标记为完成") }
                }
            } else {
                AttentionTextButton(
                    onClick = actions::dismissStopResult,
                    modifier = Modifier.testTag("agenda-stop-dismiss"),
                ) { Text("知道了") }
            }
        }
    }
}

/**
 * 添加日程的底部表单面板。高频的添加流程被压在这一层里：面板一打开，规划日已经填好，
 * 标题与时间段、预计投入摆在一屏，备注、提醒与关联计划收在「更多设置」后面。
 *
 * 表单状态仍是页面本地状态（[AgendaScheduleForm]），提交只发一个草稿 intent；
 * 提醒时刻的绝对换算由组合根按注入的时区完成，页面因此不读系统时钟与系统时区。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AgendaAddScheduleSheet(
    state: AgendaScreenState,
    form: AgendaScheduleForm,
    visible: Boolean,
    initialPlanningDay: String,
    moreExpanded: Boolean,
    backfillExpanded: Boolean,
    onToggleMore: () -> Unit,
    onToggleBackfill: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (AgendaScheduleDraft, Boolean, AgendaBackfillDraft?) -> Unit,
) {
    val tokens = attentionTokens()
    val backfillForm = rememberAgendaBackfillForm()
    LaunchedEffect(visible, backfillExpanded, form.estimate) {
        if (visible && backfillExpanded) backfillForm.initialize(form.estimate.trim().toIntOrNull() ?: 15)
        if (!visible) backfillForm.reset()
    }
    if (!visible) return
    // 规划日在面板打开时就填好：默认是正在看的那一天，用户仍然可以改成别的日子。
    var planningDay by remember(initialPlanningDay) { mutableStateOf(initialPlanningDay) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("agenda-add-sheet"),
        // 面板一次展开到可滚动高度：半展开状态下「更多设置」里的次要字段与就地校验提示
        // 落在面板可视高度以下，面板内滚动也到不了，用户同样看不到。
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.large)
                .padding(bottom = tokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.addScheduleHeadingLabel, style = tokens.typography.title)
            OutlinedTextField(
                planningDay,
                { planningDay = it },
                label = { Text("规划日 YYYY-MM-DD") },
                modifier = Modifier.fillMaxWidth().testTag("agenda-add-planning-day"),
                singleLine = true,
            )
            OutlinedTextField(
                form.title,
                form::changeTitle,
                label = { Text("日程标题") },
                modifier = Modifier.fillMaxWidth().testTag("agenda-title-input"),
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(form.startTime, form::changeStartTime, label = { Text("开始时间 HH:mm（可选）") }, modifier = Modifier.weight(1f).testTag("agenda-start-input"), singleLine = true)
                OutlinedTextField(form.endTime, form::changeEndTime, label = { Text("结束时间 HH:mm（可选）") }, modifier = Modifier.weight(1f).testTag("agenda-end-input"), singleLine = true)
            }
            OutlinedTextField(form.estimate, form::changeEstimate, label = { Text("预计分钟") }, modifier = Modifier.fillMaxWidth().testTag("agenda-estimate-input"), singleLine = true)
            AttentionOutlinedButton(
                onClick = onToggleBackfill,
                modifier = Modifier.testTag("agenda-backfill-toggle"),
            ) { Text(if (backfillExpanded) "收起实际投入" else "记录实际投入（可选）") }
            if (backfillExpanded) BackfillDurationEditor(form = backfillForm)
            // 低频字段收在「更多设置」后面：展开状态用文字说明，标签本身告诉读屏它是什么。
            AttentionTextButton(onClick = onToggleMore, modifier = Modifier.testTag("agenda-add-more-toggle")) {
                Text(if (moreExpanded) "收起更多设置" else "更多设置（备注、提醒与关联计划）")
            }
            if (moreExpanded) {
                OutlinedTextField(form.note, form::changeNote, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth().testTag("agenda-note-input"), singleLine = true)
                OutlinedTextField(form.reminderTime, form::changeReminderTime, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.fillMaxWidth().testTag("agenda-reminder-input"), singleLine = true)
                Box {
                    AttentionOutlinedButton(onClick = { form.toggleTargetMenu(true) }, modifier = Modifier.testTag("agenda-add-target")) {
                        Text(state.targets.firstOrNull { it.targetId == form.targetId }?.title ?: "关联计划（可选）")
                    }
                    DropdownMenu(
                        expanded = form.targetMenuExpanded,
                        onDismissRequest = { form.toggleTargetMenu(false) },
                    ) {
                        DropdownMenuItem(text = { Text("不关联计划") }, onClick = { form.selectTarget(null) })
                        state.targets.forEach { target ->
                            DropdownMenuItem(text = { Text(target.title) }, onClick = { form.selectTarget(target.targetId) })
                        }
                    }
                }
            }
            form.error?.let { AttentionInlineError(message = it) }
            AttentionButton(modifier = Modifier.testTag("agenda-add-submit"), onClick = {
                val parsedDate = runCatching { LocalDate.parse(planningDay) }.getOrNull()
                val parsedStart = parseScheduleMinute(form.startTime)
                val parsedEnd = parseScheduleMinute(form.endTime, allowEndOfDay = true)
                val estimated = form.estimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                val parsedReminder = parseScheduleMinute(form.reminderTime)
                val error = validateScheduleForm(
                    ScheduleFormInput(
                        title = form.title,
                        date = parsedDate,
                        startText = form.startTime,
                        endText = form.endTime,
                        estimateText = form.estimate,
                        startMinute = parsedStart,
                        endMinute = parsedEnd,
                        estimateMinutes = estimated,
                        reminderText = form.reminderTime,
                        reminderMinute = parsedReminder,
                    ),
                )
                // 校验没过就只显示错误：不发保存意图，也没有任何数据被写入。
                if (error != null) {
                    form.showError(error)
                } else if (backfillExpanded && !backfillForm.validateAndShowError()) {
                    // 补记校验失败时只保留表单错误，不发保存 intent。
                } else {
                    onSubmit(
                        form.toDraft(parsedDate!!),
                        parsedReminder != null,
                        if (backfillExpanded) backfillForm.toDraft(form.targetId, form.note) else null,
                    )
                    form.reset()
                    backfillForm.reset()
                }
            }) { Text("添加日程") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BackfillDurationEditor(form: AgendaBackfillForm) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag("agenda-backfill-editor"),
    ) {
        Text("实际投入")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 30, 60).forEach { minutes ->
                AttentionOutlinedButton(
                    onClick = { form.setPreset(minutes) },
                    modifier = Modifier.testTag("agenda-backfill-preset-$minutes"),
                ) { Text("${minutes} 分钟") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = form.hours,
                onValueChange = form::changeHours,
                label = { Text("小时") },
                modifier = Modifier.width(120.dp).testTag("agenda-backfill-hours"),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.minutes,
                onValueChange = form::changeMinutes,
                label = { Text("分钟（5 分钟步进）") },
                modifier = Modifier.width(180.dp).testTag("agenda-backfill-minutes"),
                singleLine = true,
            )
        }
        form.error?.let { AttentionInlineError(message = it, messageTag = "agenda-backfill-error") }
    }
}

/**
 * 「开始自由计时」的目标选择面板。就地挑一个未归属活动或未归档计划就开始，
 * 不需要先建一条日程——自由计时的时间记录因此没有日程来源。
 *
 * 当前正在计时的目标用文字标出，不靠高亮颜色单独表达。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgendaFreeTimerSheet(
    options: List<AgendaFreeTimerOption>,
    visible: Boolean,
    onStart: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = attentionTokens()
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("agenda-free-timer-sheet"),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = tokens.spacing.large)
                .padding(bottom = tokens.spacing.large),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("开始自由计时", style = tokens.typography.title)
            Text("选一个目标就开始计时：不需要先添加日程。")
            options.forEach { option ->
                val tag = option.targetId
                    ?.let { "agenda-free-timer-target-$it" }
                    ?: "agenda-free-timer-unowned"
                AttentionOutlinedButton(
                    onClick = { onStart(option.targetId) },
                    modifier = Modifier.fillMaxWidth().testTag(tag),
                ) { Text(option.label + if (option.selected) "（当前）" else "") }
            }
        }
    }
}

/**
 * 首屏最后一块：事后补记——所选规划日上已经记录的实际投入。
 *
 * 手动时间账本的编辑与删除跟着合并后的日程页走，因此这里既能补上遗漏的投入，也能更正与删除。
 */
@Composable
private fun AgendaBackfillSection(
    state: AgendaScreenState,
    editingRecord: AgendaTimeRecord?,
    onEditRecord: (AgendaTimeRecord) -> Unit,
    onStopEditingRecord: () -> Unit,
    onDeleteRecord: (String) -> Unit,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxWidth().testTag("agenda-section-backfill"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("事后补记", style = tokens.typography.title, modifier = Modifier.testTag("agenda-backfill"))
        if (state.timeRecords.isNotEmpty() && state.recordedMinutesLabel.isNotBlank()) {
            Text(state.recordedMinutesLabel)
        }
        state.timeRecords.forEach { record ->
            AgendaTimeRecordCard(
                record = record,
                targets = state.targets,
                isEditing = editingRecord?.entryId == record.entryId,
                onEdit = { onEditRecord(record) },
                onStopEdit = onStopEditingRecord,
                onDelete = { onDeleteRecord(record.entryId) },
                actions = actions,
            )
        }
    }
}

/**
 * 重复日程的次级入口。
 *
 * 能力没有删除：展开之后，创建重复规则与「停止未来生成」都仍然可用。默认折叠只是让它
 * 不再占据首屏主路径；折叠状态属于页面 module，不写入任何持久化。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaRecurrenceSection(
    state: AgendaScreenState,
    form: AgendaRecurrenceForm,
    expanded: Boolean,
    onToggle: () -> Unit,
    date: String,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    Column(
        Modifier.fillMaxWidth().testTag("agenda-section-recurrence"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AttentionOutlinedButton(onClick = onToggle, modifier = Modifier.testTag("agenda-recurrence-toggle")) {
            Text(if (expanded) "收起重复日程" else "重复日程（次级入口）")
        }
        if (expanded) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
                Column(Modifier.padding(tokens.spacing.medium), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("重复日程", style = tokens.typography.body)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(form.title, form::changeTitle, label = { Text("重复标题") }, modifier = Modifier.weight(1f).testTag("agenda-recurrence-title-input"), singleLine = true)
                        ScheduleFrequency.entries.forEach { value ->
                            if (form.frequency == value) AttentionButton(onClick = {}) { Text(value.agendaLabel()) }
                            else AttentionOutlinedButton(onClick = { form.selectFrequency(value) }) { Text(value.agendaLabel()) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(form.untilDate, form::changeUntilDate, label = { Text("结束规划日（可选）") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(form.estimatedMinutes, form::changeEstimatedMinutes, label = { Text("预计分钟（可选）") }, modifier = Modifier.width(140.dp), singleLine = true)
                        OutlinedTextField(form.reminderTime, form::changeReminderTime, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.width(150.dp).testTag("recurrence-reminder-input"), singleLine = true)
                    }
                    when (form.frequency) {
                        ScheduleFrequency.WEEKLY -> {
                            // 七个星期自动换行，不再横向滚动。横向滚动容器会成为复选框最近的滚动祖先：
                            // 重复日程是折叠的次级入口，整块被推到折叠线以下时，只滚动这一行的行为
                            // 滚不动页面，复选框也就既到不了视口里也点不到。换行后七个星期一直可达。
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (1..7).forEach { weekday ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = weekday in form.weekdays,
                                            onCheckedChange = { checked -> form.toggleWeekday(weekday, checked) },
                                            // 星期复选框带稳定 test tag：页面上的时间轴行也有完成勾选框，
                                            // 测试必须能指名这一个，而不是「页面上第一个可切换的控件」。
                                            modifier = Modifier.testTag("agenda-recurrence-weekday-$weekday"),
                                        )
                                        Text(agendaWeekdayLabel(weekday))
                                    }
                                }
                            }
                        }
                        ScheduleFrequency.MONTHLY -> {
                            OutlinedTextField(form.dayOfMonth, form::changeDayOfMonth, label = { Text("每月日期 1-31") }, modifier = Modifier.width(160.dp), singleLine = true)
                        }
                        ScheduleFrequency.DAILY -> Unit
                    }
                    Box {
                        AttentionOutlinedButton(onClick = { form.toggleTargetMenu(true) }) {
                            Text(state.targets.firstOrNull { it.targetId == form.targetId }?.title ?: "关联计划（可选）")
                        }
                        DropdownMenu(
                            expanded = form.targetMenuExpanded,
                            onDismissRequest = { form.toggleTargetMenu(false) },
                        ) {
                            DropdownMenuItem(text = { Text("不关联计划") }, onClick = { form.selectTarget(null) })
                            state.targets.forEach { target ->
                                DropdownMenuItem(text = { Text(target.title) }, onClick = { form.selectTarget(target.targetId) })
                            }
                        }
                    }
                    form.error?.let { AttentionInlineError(message = it) }
                    AttentionButton(modifier = Modifier.testTag("agenda-recurrence-submit"), onClick = {
                        val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                        val untilDateText = form.untilDate.trim()
                        val parsedUntilDate = untilDateText.takeIf(String::isNotEmpty)?.let { value ->
                            runCatching { LocalDate.parse(value) }.getOrNull()
                        }
                        val estimatedMinutesText = form.estimatedMinutes.trim()
                        val parsedEstimatedMinutes = estimatedMinutesText.takeIf(String::isNotEmpty)?.toIntOrNull()
                        val parsedReminderMinute = parseScheduleMinute(form.reminderTime)
                        val parsedDayOfMonth = form.dayOfMonth.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                        val error = validateRecurrenceForm(
                            form.title,
                            parsedDate,
                            untilDateText,
                            parsedUntilDate,
                            form.frequency,
                            form.weekdays,
                            parsedDayOfMonth,
                            estimatedMinutesText,
                            parsedEstimatedMinutes,
                            form.reminderTime,
                            parsedReminderMinute,
                        )
                        if (error != null) {
                            form.showError(error)
                        } else {
                            actions.addRecurrenceAndMaterialize(form.toDraft(parsedDate!!))
                            if (parsedReminderMinute != null) actions.requestReminderPermission()
                            form.reset()
                        }
                    }) { Text("创建并生成未来 30 天") }
                    state.recurrenceRules.forEach { rule ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(rule.label, Modifier.weight(1f))
                            if (rule.active) {
                                AttentionTextButton(
                                    onClick = { actions.stopRecurrence(rule.ruleId) },
                                    modifier = Modifier.testTag("agenda-recurrence-stop-${rule.ruleId}"),
                                ) { Text("停止未来生成") }
                            } else {
                                Text("已停止")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一条日程条目行。展示与可点击的动作都来自投影，页面只发 intent。
 *
 * 计划投入、实际投入、完成状态与「已超出容量」都由投影给出，页面不比较分钟数，
 * 因此预计时长永远不会被当成实际投入展示。正在计时的那条用文字与图标说明状态，
 * 锈橙色只是补充语义。
 *
 * 整卡是触摸用户的便捷入口：点卡片任意非控件区域和点行尾 `⋮` 打开同一个行菜单。
 * 整卡刻意用 `pointerInput` 而不是 `clickable`，避免把行内完成控件、计时控制
 * 与文字合并成一个点击语义；`⋮` 仍是读屏用户的等价入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AgendaScheduleRowCard(
    row: AgendaScheduleRow,
    actions: AgendaActions,
    onEdit: () -> Unit,
    secondary: Boolean = false,
    activeTimer: AgendaActiveTimer? = null,
    timerTargets: List<AgendaTargetOption> = emptyList(),
) {
    val tokens = attentionTokens()
    val entry: ScheduleEntry = row.entry
    val statusSemantic = when {
        row.timing -> tokens.brand.timingInProgress
        row.completed -> tokens.brand.completed
        else -> tokens.brand.scheduled
    }
    val cardColor = when {
        row.timing -> tokens.colorScheme.surfaceContainerLow
        secondary -> tokens.colorScheme.surfaceContainerLow
        else -> tokens.colorScheme.background
    }
    var rowMoreExpanded by remember(entry.id) { mutableStateOf(false) }
    val rowTapModifier = if (row.offersMoreMenu) {
        Modifier.pointerInput(entry.id) {
            detectTapGestures { rowMoreExpanded = true }
        }
    } else {
        Modifier
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(cardColor)
            .drawBehind {
                val accentWidth = 3.dp.toPx()
                drawLine(
                    color = statusSemantic.accent,
                    start = Offset(accentWidth / 2f, 0f),
                    end = Offset(accentWidth / 2f, size.height),
                    strokeWidth = accentWidth,
                )
                drawLine(
                    color = tokens.colorScheme.outlineVariant,
                    start = Offset(0f, size.height - 1.dp.toPx()),
                    end = Offset(size.width, size.height - 1.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .then(rowTapModifier)
            .testTag("agenda-schedule-row-${entry.id}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (row.timing || activeTimer != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("schedule-timing-cue")) {
                    Text(
                        activeTimer?.let { "正在计时：${it.targetLabel}" } ?: "正在计时",
                        style = tokens.typography.caption,
                        color = tokens.brand.timingInProgress.accent,
                        modifier = Modifier.padding(start = tokens.spacing.small),
                    )
                }
                activeTimer?.let { timer ->
                    Column(
                        Modifier.testTag("agenda-running-timer"),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            Modifier
                                .background(tokens.brand.timingInProgress.container, RoundedCornerShape(tokens.radius.small))
                                .padding(4.dp),
                        ) {
                            Icon(
                                if (timer.paused) Icons.Filled.PauseCircle else Icons.Filled.Timer,
                                contentDescription = timer.statusLabel,
                                tint = tokens.brand.timingInProgress.onContainer,
                                modifier = Modifier.size(16.dp).testTag("agenda-running-timer-tint"),
                            )
                        }
                        Text(timer.statusLabel, modifier = Modifier.testTag("agenda-running-timer-status"))
                        Text(timer.elapsedLabel, modifier = Modifier.testTag("agenda-running-timer-elapsed"))
                        timer.pendingHintLabel?.let { hint ->
                            Text(hint, modifier = Modifier.testTag("agenda-running-timer-hint"))
                        }
                        AgendaRunningTimerControls(timer = timer, targets = timerTargets, actions = actions)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.small),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(tokens.spacing.small)) {
                    Text(
                        row.title,
                        style = tokens.typography.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        row.detailLabel,
                        style = tokens.typography.caption,
                        color = tokens.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.investmentLabel.isNotBlank()) {
                        Text(
                            row.investmentLabel,
                            style = tokens.typography.caption,
                            color = tokens.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    row.capacityNotice?.let { notice ->
                        Text(
                            notice,
                            style = tokens.typography.caption,
                            color = tokens.brand.error.accent,
                            modifier = Modifier.testTag("schedule-capacity-notice-${entry.id}"),
                        )
                    }
                }
                Text(
                    text = when {
                        row.timing -> "计时中"
                        // The investment line already says「已完成」; keep this chip short so
                        // accessibility queries do not hear the same completion state twice.
                        row.completed -> "完成"
                        else -> "已安排"
                    },
                    style = tokens.typography.caption,
                    color = statusSemantic.onContainer,
                    modifier = Modifier
                        .background(statusSemantic.container, RoundedCornerShape(tokens.radius.small))
                        .padding(tokens.spacing.small)
                        .testTag("schedule-status-${entry.id}"),
                )
            }
            // 操作控件按可用宽度换行，窄屏上不会把长标题或按钮挤出卡片边界。
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.small),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.small),
            ) {
                if (row.offersCompletionCheck) {
                    Box(
                        Modifier
                            .testTag("schedule-completion-${entry.id}")
                            .semantics {
                                contentDescription = "完成「${row.title}」"
                                onClick {
                                    if (row.completed) actions.toggleScheduleCompleted(entry.id)
                                    else actions.confirmScheduleCompleted(entry.id)
                                    true
                                }
                            },
                    ) {
                        Checkbox(
                            checked = row.completed,
                            onCheckedChange = {
                                if (row.completed) actions.toggleScheduleCompleted(entry.id)
                                else actions.confirmScheduleCompleted(entry.id)
                            },
                            modifier = Modifier.testTag("schedule-completion-check-${entry.id}"),
                        )
                    }
                }
                if (row.offersMoreMenu) {
                    Box {
                        AttentionTextButton(
                            onClick = { rowMoreExpanded = true },
                            modifier = Modifier
                                .testTag("schedule-more-${entry.id}")
                                .semantics { contentDescription = "更多操作" },
                        ) { Icon(Icons.Filled.MoreVert, contentDescription = null) }
                        DropdownMenu(
                            expanded = rowMoreExpanded,
                            onDismissRequest = { rowMoreExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("修改日程") },
                                onClick = { rowMoreExpanded = false; onEdit() },
                                modifier = Modifier.testTag("schedule-more-edit-${entry.id}"),
                            )
                            DropdownMenuItem(
                                text = { Text("开始计时") },
                                enabled = row.canStartTimer,
                                onClick = { rowMoreExpanded = false; actions.startTimerFromEntry(entry) },
                                modifier = Modifier.testTag("schedule-more-start-timer-${entry.id}"),
                            )
                            DropdownMenuItem(
                                text = { Text(if (row.completed) "取消完成" else "完成并记录") },
                                onClick = {
                                    rowMoreExpanded = false
                                    if (row.completed) actions.toggleScheduleCompleted(entry.id)
                                    else actions.confirmScheduleCompleted(entry.id)
                                },
                                modifier = Modifier.testTag("schedule-more-completion-${entry.id}"),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一条实际投入：时长、归属与来源，以及编辑与删除的入口。
 *
 * 编辑表单是页面本地状态；保存时只把被改动的字段作为 intent 发出，
 * 记录的规划日与来源由领域层保持不变（ADR-0003 的手动时间账本边界）。
 */
@Composable
private fun AgendaTimeRecordCard(
    record: AgendaTimeRecord,
    targets: List<AgendaTargetOption>,
    isEditing: Boolean,
    onEdit: () -> Unit,
    onStopEdit: () -> Unit,
    onDelete: () -> Unit,
    actions: AgendaActions,
) {
    val tokens = attentionTokens()
    var minutes by remember(record.entryId) { mutableStateOf(record.durationMinutes.toString()) }
    var note by remember(record.entryId) { mutableStateOf(record.note) }
    var targetId by remember(record.entryId) { mutableStateOf(record.targetId) }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(tokens.radius.medium)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(DurationFormat.ofMinutes(record.durationMinutes), style = tokens.typography.title)
            Text(
                "${record.targetLabel} · ${TimeEntrySourceFormat.labelOf(record.source)}",
                style = tokens.typography.caption,
                color = tokens.colorScheme.onSurfaceVariant,
            )
            if (record.note.isNotBlank()) Text(record.note)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttentionTextButton(onClick = onEdit, modifier = Modifier.testTag("edit-time-${record.entryId}")) { Text("编辑") }
                AttentionTextButton(onClick = onDelete, modifier = Modifier.testTag("delete-time-${record.entryId}")) { Text("删除") }
            }
            if (isEditing) {
                // 目标选择器在颜色之外还要说清楚当前选中了谁：选中项独占一行，
                // 候选项另起一行横向滚动，因此选中态既不会被滚出屏幕也不依赖颜色。
                val selectedLabel = targets.firstOrNull { it.targetId == targetId }?.title ?: "未归属"
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag("edit-time-target-${record.entryId}")) {
                    AttentionSelectedIndicator(selected = true, label = selectedLabel)
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    AttentionTextButton(onClick = { targetId = null }) { Text("未归属") }
                    targets.forEach { target ->
                        AttentionTextButton(onClick = { targetId = target.targetId }) { Text(target.title) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        minutes,
                        { minutes = it },
                        label = { Text("分钟") },
                        modifier = Modifier.width(120.dp).testTag("edit-time-minutes-${record.entryId}"),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        note,
                        { note = it },
                        label = { Text("备注") },
                        modifier = Modifier.width(180.dp).testTag("edit-time-note-${record.entryId}"),
                        singleLine = true,
                    )
                    AttentionButton(
                        modifier = Modifier.testTag("save-time-${record.entryId}"),
                        onClick = {
                            minutes.toIntOrNull()?.takeIf { it > 0 }?.let { value ->
                                actions.editTime(record.entryId, value, targetId, note)
                                onStopEdit()
                            }
                        },
                    ) { Text("保存") }
                }
            }
        }
    }
}

/** 编辑日程对话框。表单状态是页面本地的；保存时把改好的条目作为 intent 发出。 */
@Composable
private fun AgendaScheduleEditDialog(
    row: AgendaScheduleRow,
    targets: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSave: (ScheduleEntry, Int?, Boolean, AgendaBackfillDraft?) -> Unit,
) {
    val entry = row.entry
    var date by remember(entry.id) { mutableStateOf(entry.planningDate) }
    var title by remember(entry.id) { mutableStateOf(row.title) }
    var startTime by remember(entry.id) { mutableStateOf(row.startText) }
    var endTime by remember(entry.id) { mutableStateOf(row.endText) }
    var estimate by remember(entry.id) { mutableStateOf(row.estimateText) }
    var note by remember(entry.id) { mutableStateOf(row.note) }
    var reminderTime by remember(entry.id) { mutableStateOf(row.reminderText) }
    var targetId by remember(entry.id) { mutableStateOf(row.targetId) }
    var targetMenuExpanded by remember { mutableStateOf(false) }
    var backfillExpanded by remember(entry.id) { mutableStateOf(false) }
    val backfillForm = rememberAgendaBackfillForm()
    LaunchedEffect(entry.id, backfillExpanded) {
        if (backfillExpanded) backfillForm.initialize(estimate.trim().toIntOrNull() ?: entry.estimatedMinutes ?: 15)
        else backfillForm.reset()
    }
    var error by remember(entry.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑日程") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(date, { date = it }, label = { Text("规划日 YYYY-MM-DD") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(title, { title = it }, label = { Text("日程标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startTime, { startTime = it }, label = { Text("开始 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(endTime, { endTime = it }, label = { Text("结束 HH:mm") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(estimate, { estimate = it }, label = { Text("预计分钟") }, modifier = Modifier.fillMaxWidth().testTag("schedule-edit-estimate-${entry.id}"), singleLine = true)
                AttentionOutlinedButton(
                    onClick = { backfillExpanded = !backfillExpanded },
                    modifier = Modifier.testTag("schedule-edit-backfill-toggle-${entry.id}"),
                ) { Text(if (backfillExpanded) "收起实际投入" else "记录实际投入（可选）") }
                if (backfillExpanded) BackfillDurationEditor(form = backfillForm)
                OutlinedTextField(reminderTime, { reminderTime = it }, label = { Text("提醒 HH:mm（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Box {
                    AttentionOutlinedButton(onClick = { targetMenuExpanded = true }) { Text(targets.firstOrNull { it.first == targetId }?.second ?: "关联计划（可选）") }
                    DropdownMenu(expanded = targetMenuExpanded, onDismissRequest = { targetMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text("不关联计划") }, onClick = { targetId = null; targetMenuExpanded = false })
                        targets.forEach { (id, titleText) -> DropdownMenuItem(text = { Text(titleText) }, onClick = { targetId = id; targetMenuExpanded = false }) }
                    }
                }
                error?.let { AttentionInlineError(message = it) }
            }
        },
        confirmButton = {
            AttentionTextButton(modifier = Modifier.testTag("schedule-edit-save-${entry.id}"), onClick = {
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                val parsedStart = parseScheduleMinute(startTime)
                val parsedEnd = parseScheduleMinute(endTime, allowEndOfDay = true)
                val estimated = estimate.trim().takeIf(String::isNotEmpty)?.toIntOrNull()
                val parsedReminder = parseScheduleMinute(reminderTime)
                val validationError = validateScheduleForm(
                    ScheduleFormInput(
                        title = title,
                        date = parsedDate,
                        startText = startTime,
                        endText = endTime,
                        estimateText = estimate,
                        startMinute = parsedStart,
                        endMinute = parsedEnd,
                        estimateMinutes = estimated,
                        reminderText = reminderTime,
                        reminderMinute = parsedReminder,
                    ),
                )
                if (validationError != null) {
                    error = validationError
                } else if (backfillExpanded && !backfillForm.validateAndShowError()) {
                    error = backfillForm.error
                } else {
                    onSave(
                        entry.copy(
                            planningDate = parsedDate.toString(),
                            title = title,
                            startMinute = parsedStart,
                            endMinute = parsedEnd,
                            estimatedMinutes = estimated,
                            note = note,
                            targetId = targetId,
                        ),
                        parsedReminder,
                        parsedReminder != null,
                        if (backfillExpanded) backfillForm.toDraft(targetId, note) else null,
                    )
                }
            }) { Text("保存") }
        },
        dismissButton = { AttentionTextButton(onClick = onDismiss) { Text("取消") } },
    )
}
