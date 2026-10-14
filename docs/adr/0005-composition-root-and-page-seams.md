---
status: accepted
date: 2026-10-14
---

# 组合根与页面 seam 的职责边界

## 背景

工作台的组合与页面实现在同一个文件里。`app/src/main/java/com/attention/app/Workspace.kt` 同时包含 `WorkspaceApp` 和五个页面：`WorkspaceTodayScreen`、`TargetsScreen`、`AgendaScreen`、`SettingsScreen`、`StatisticsScreen`，它们都是该文件内的 private Composable，签名统一为 `(state: AttentionState, viewModel: AttentionViewModel)`。页面直接调用 `AttentionViewModel` 上四十多个不同方法，包括 `startTimer`、`stopTimer`、`switchTimer`、`addTime`、`editTime`、`deleteTimeWithUndo`、`addTarget`、`moveTargetFrom`、`addSchedule`、`setSettings`、`exportJson` 等。

时间也在渲染过程中被直接读取。`TargetsScreen` 用 `state.planningDate(Instant.now())` 得到当前规划日，`AgendaScreen` 用它初始化选中日期，`StatisticsScreen` 用它初始化选中规划日和周期统计，提醒时间的格式化直接取 `ZoneId.systemDefault()`。规划日本来就是产品的核心语义（每日目标、每日容量和统计都按规划日计算），却无法在测试中固定。

平台能力的使用位置也不一致：SAF 的 `CreateDocument` 与 `OpenDocument` launcher 目前在 `WorkspaceApp` 内，但前台计时服务由页面直接启动（`ContextCompat.startForegroundService` 出现在今天工作台的开始与恢复路径上），通知权限也跨越了根与页面——今天工作台直接检查通知可用性（`notificationsEnabled`），日程页面直接申请提醒权限（`requestReminderPermission`）。

结果是：改动一个页面必须理解无关页面的状态；页面缺少可独立测试的 seam；时间相关渲染不确定；平台副作用在页面测试里无法避免。

## 决策

### 1. `WorkspaceApp` 是唯一的组合根

`WorkspaceApp` 保留以下职责：仓库与 ViewModel 组装、导航目的地与上次打开页面的持久化协调、全局错误展示、共享反馈宿主、导入预览状态、文件选择器状态，以及 Android adapter。

`WorkspaceApp` 不再承载任何页面内部的对话框状态、表单状态或选择状态，这些留在对应页面 module 内。

### 2. 每个页面只暴露 `*ScreenState` 与 `*Actions`

五个页面各自拥有一个小的 interface：不可变的、页面专属的 `*ScreenState`（例如 `SettingsScreenState`、`StatisticsScreenState`），以及页面专属的 `*Actions`。

- 页面不得接收完整的 `AttentionViewModel`。
- 页面不得直接调用仓储方法，包括 `AttentionStateRepository`、`RoomAttentionStateRepository` 与 `SettingsStore`。
- `AttentionViewModel` 与 Room、DataStore 只出现在组合根侧的 actions adapter 实现中。

现有稳定的 test tag、无障碍语义、共享状态组件与反馈组件仍然是页面 interface 的一部分，迁移不得删除它们。

### 3. 时间与时区显式注入

页面投影与选择器放在一个新增的纯 Kotlin Gradle module 中。它从 `AttentionState` 加上显式注入的当前时间与时区构建各页面的 `*ScreenState`，产出与规划日、周期和选中日期相关的状态。

- 该 module 依赖 `:domain`；`:domain` 不依赖它，也不把它当作规则来源。
- Composable 不再调用 `Instant.now()`，页面渲染不隐式读取系统时间或系统默认时区。

### 4. Android adapter 归属组合根

SAF 文件读写、权限检查与申请、前台计时服务启动、恢复提示取消和通知可用性查询都属于组合根 adapter。

页面 module 不触达任何 Android API：它发出 intent，由组合根 adapter 执行。今天工作台发出“开始计时”，实际的 `startForegroundService` 由组合根完成；日程页面发出“保存提醒”，权限申请由组合根完成。

## 迁移顺序

先迁移设置与统计，再迁移今天工作台、目标树和所有日期。每一步都必须保持对应的用户可见行为后才进入下一步：

- 设置：规划日界线、周起始日、每日容量、首屏与通知偏好在重启后仍然生效。
- 统计：周期范围、进度、每日容量、达成里程碑信息与筛选结果不变。
- 今天工作台：计时的开始、暂停、恢复、切换与停止保持一致；手动时间账本的编辑、删除与撤销保持相同约束；规划日与每日容量展示不变；计时完成仍保留通知权限与前台服务行为；从日程条目开始计时仍保留来源关联。
- 目标树：层级、排序、归档、恢复与永久移除行为不变；未来目标规则、目标迁移与目标移动保持既有校验。
- 所有日期：规划日导航与日程条目筛选不变；日程创建、编辑、完成确认与重复日程展开仍产生相同的时间记录。
- 每一步都必须保持导航与上次打开页面的行为，以及共享的成功、错误、权限与本地数据反馈的文案与可达性（不靠颜色单独传达状态）。

## seam 深度检查点

设置与统计是这次重构的检查点。判据是 seam 的深度：一个页面的调用者只学到它的 `*ScreenState` 与 `*Actions`，而渲染、表单协调与页面专属的展示规则被该页面 module 吸收。

如果先迁移的页面 seam 退化为透传层——即 `*ScreenState` 仍然携带整个 `AttentionState`，或 `*Actions` 仍然把 `AttentionViewModel` 整体暴露给页面——必须先修订 seam 形状，再继续迁移今天工作台、目标树和所有日期。删除测试同样适用：删除一个页面后，复杂度应集中在这个页面 module，而不是泄漏到导航或其他页面。

## 与既有 ADR 的关系

本 ADR 不取代、不修订 ADR-0001、ADR-0002、ADR-0004，只补足它们未覆盖的部分。需要显式说明的非冲突点有两处：

- ADR-0004 确立了「`:domain` 为唯一规则来源」。新增的页面投影 module 是展示与投影层，不拥有任何领域规则：它只从 `AttentionState` 派生页面状态，绝不重新计算或复制领域规则。规划日切分、周期快照、父目标汇总、目标迁移、投入经验与等级的算法仍然只存在于 `:domain`。因此投影 module 只是 ADR-0004 允许的「读取领域结果」的一侧，不是第二个规则来源。
- ADR-0002 的「`:domain` 继续只包含纯 Kotlin 的领域类型和规则」仍然成立：新增 module 装的是投影函数与面向页面的状态类型，不是领域类型与规则。该句约束的是 `:domain` 的内容，并未断言纯 Kotlin module 只此一个，因此无需解读为冲突，也不修改 ADR-0002。

模块之间的依赖方向是单向的：页面投影 module 依赖 `:domain`；`:domain` 不依赖投影 module，投影 module 也不依赖 Android、Room 或 DataStore。

ADR-0003（手动时间账本记录边界）同样不受影响：时间记录的编辑、删除、归档限制与未归属活动归入规则仍只在 `:domain`，今天工作台页面只提供入口与表单，seam 迁移不改变账本语义。

ADR-0001 保持不变。它锁定的是原生 Android 技术栈，并把纯 Kotlin `:domain` 作为主要测试 seam；收窄组合根与新增一个纯 Kotlin 投影 module 不改变任何技术选型，因此不在 ADR-0001 中产生需要修订的结论。

## 影响

- 页面 interface 成为页面行为的唯一测试面：每个投影补纯 Kotlin 测试，每个页面补直接 Compose 测试，用 fake screen state 与 fake actions 断言可见标签、选中状态和发出的 intent。
- 依赖方向 `页面投影 module → :domain` 必须持续维护；`:domain` 保持为唯一规则来源。
- 计时器的平台行为改为跨 seam 传递（页面 intent → 组合根 adapter → 前台服务），前台计时必须仍由可见 UI 触发这一约束见 ADR-0001。
- 迁移期间可能短暂存在旧的直通路径；每完成一个页面必须删除该页面的旧路径，不保留两套入口。
- ADR-0002、ADR-0003、ADR-0004 的既有 Room、备份、计时器与端到端测试继续作为领域与持久化行为的权威测试，不被页面测试取代。

## 关联

- 规格 Issue：[#141](https://github.com/magician336/attention/issues/141)
- 相关任务：[#142](https://github.com/magician336/attention/issues/142)
- [ADR-0001 Android 技术栈](0001-android-technology-stack.md)、[ADR-0002 Room 与 DataStore 边界](0002-room-datastore-boundary.md)、[ADR-0003 手动时间账本](0003-manual-time-ledger.md)、[ADR-0004 经验规范化边界](0004-experience-canonicalization-boundary.md)