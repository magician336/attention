---
status: proposed
date: 2026-10-19
base: origin/main
base_commit: 4eb9f35a87aa16f184ed8c0343aad5a95de695e9
---

# 基于 origin/main 的 Ousterhout 设计审查

## 范围和判据

本审查把 John Ousterhout 的“软件设计的哲学”应用到当前代码，不以文件行数直接判断质量。重点是：

- **变化放大**：一个设计决策变化时，是否需要同步修改多个模块。
- **认知负荷**：调用者是否必须记住超出当前任务的存储、平台或领域规则。
- **模块深度**：接口成本是否被足够多的隐藏功能抵消。
- **信息隐藏**：格式、算法、策略和副作用是否只有一个归属点。
- **层次抽象**：页面、组合根、ViewModel、持久化和领域层是否使用各自的语言。

审查基于 `origin/main` 提交 `4eb9f35a87aa16f184ed8c0343aad5a95de695e9`。当前结论是设计审查和重构计划，不改变运行时代码。

## 现有的设计资产

### 领域规则集中在纯 Kotlin 层

`domain/src/main/kotlin/com/attention/domain/AttentionEngine.kt` 集中了规划日、目标进度、周期快照、投入经验和里程碑等规则。它文件较大，但这不是拆分它的充分理由：这些规则共享同一套目标树、时间记录和规划日知识，集中保存反而减少了信息泄漏。

### 页面已经有小型状态 seam

`workspace-state` 为日程、目标树和统计页提供页面状态与动作接口。`AgendaScreen`、`TargetsScreen` 和 `StatisticsScreen` 不直接接收完整 `AttentionState` 或 `AttentionViewModel`，这符合“不同层使用不同抽象”的方向。

### 备份 workflow 已经形成深模块

`AttentionBackupCodec` 负责协议编解码、校验、稳定 ID 合并和经验规范化；`BackupWorkflow` 负责预览、指纹和提交编排。调用者不需要知道 JSON 字段和 Room/DataStore 提交流程，这是当前最接近深模块的边界之一。

## 复杂性热点

### P1：`Workspace.kt` 同时承担组合根、平台适配器和页面编排

`app/src/main/java/com/attention/app/Workspace.kt` 的 `WorkspaceApp` 附近同时处理：

- 仓库、数据库和 ViewModel 组装；
- 启动入口、上次打开页面和导航状态；
- SAF 导入导出与备份预览；
- 通知权限、提醒权限和前台计时服务；
- 多个页面的 actions adapter；
- 反馈宿主与页面渲染。

这不是单纯的“大文件”问题。组合根读者必须同时掌握页面意图、Android 副作用、备份协议和导航持久化，导致变化放大：新增一个页面或一个平台动作时，组合根会成为必改中心。

**红旗**：同一类平台动作在组合根多个位置重复。计时启动路径在日程条目和自由计时路径中分别启动前台服务、取消恢复提示并检查通知权限（约 841–875 行）。这两处现在依靠注释保持一致，属于后门信息泄漏。

**建议结构**：保留 `WorkspaceApp` 作为组合根，但把“计时平台效果”集中到一个深的 `TimerEffects`/`WorkspaceTimerAdapter` 接口：

```text
AgendaActions.startTimer
  -> ViewModel 记录领域计时状态
  -> TimerEffects.onTimerStarted()
       取消恢复提示
       检查通知权限
       启动前台服务
```

页面仍然只发 intent；组合根只负责把 adapter 注入页面。这个接口隐藏 Android 服务、通知和恢复提示的共同知识，不能退化成三个同名转发方法。

### P1：`AttentionViewModel` 是跨能力的浅门面

`app/src/main/java/com/attention/app/AttentionViewModel.kt` 暴露目标、阶段、迁移、日程、时间记录、计时器、设置和备份等大量动作。`runCommand` 和 `runTypedCommand` 统一了一部分错误处理，但调用者仍要知道每个动作应走哪条路径：普通状态变换、typed domain command、settings command 或 backup workflow。

这层的收益主要是生命周期和错误 StateFlow；许多公共方法只是把参数转给 repository 或 domain extension，接口成本接近实现成本。新功能继续向这里堆方法会让所有页面都依赖一个不断膨胀的能力集合。

**建议结构**：不按每个方法机械拆类，而是按变化知识建立三个深接口：

1. `InvestmentActions`：时间记录、计时器和目标投入命令；
2. `PlanningActions`：日程、重复日程、阶段和迁移；
3. `BackupActions`：JSON/CSV 导出、导入预览、提交和恢复。

它们应共享一个底层提交与错误通道，避免复制 Coroutine、错误映射和状态订阅。页面只拿到自己需要的能力，组合根仍然可以用一个 ViewModel 组装这些接口。

### P1：备份预览的协议知识泄漏到组合根

`AttentionBackupCodec` 已拥有 `BackupPreview`，但 `Workspace.kt` 的 `backupPreviewText`（约 1293–1317 行）仍自行计算“总记录数”和合并前后的记录数。这样 JSON 协议新增一种记录时，codec、预览文案和组合根至少要同步修改三处。

**建议结构**：让备份模块提供 `BackupPreview.summary` 或纯 Kotlin 的 `BackupPreviewPresentation`，集中拥有记录类型清单与计数规则；组合根只负责把已经准备好的行交给反馈宿主或对话框渲染。该改动能直接消除一类变化放大，且不会改变备份协议。

### P2：Room 业务仓库的读模型装配知识出现两套

`RoomBusinessDataRepository` 的 `readInTransaction` 按 DAO 装配完整 `AttentionState`，`observe()` 又为同一批表分别建立 Flow、组合分组并重新装配状态。两条路径都必须知道业务表清单和派生经验的边界。

这不是立即拆成多个 DAO facade 的理由；那些 facade 会是浅模块。更深的方向是提取一个只属于 Room 边界的 `RoomBusinessSnapshot` 装配器，让一次性读取和响应式读取共享“实体行到领域快照”的映射知识，而不是共享一堆 getter。

**迁移顺序**：先为 `readInTransaction` 和 `observe()` 建立相同快照的回归测试，再重组装配器；不要在没有基线的情况下同时改变 Room 表、Flow 拓扑和领域投影。

### P2：`AgendaScreen` 的局部状态很多，但不能只按行数拆分

`AgendaScreen` 的页面状态包含日期、面板、表单、编辑行、删除确认和通知打开处理等多个局部状态。它们共同描述“日程页一次用户操作的生命周期”，机械拆成许多 `*State` 小类会增加接口和跨文件跳转，不一定减少复杂性。

**当前判断**：先保留页面内的状态所有权。只有在新增功能必须跨多个 overlay 共享状态时，才提取一个有明确不变量的 `AgendaOverlayState`；不要为了降低文件行数而拆出只转发字段的类。

## 不应立即重构的部分

### `AttentionEngine` 不应按函数数量机械切分

它虽然有约 1,400 行，但规划日、目标树、周期和投入经验共享大量知识。按“读取/计算/奖励”或按执行顺序切分，会把日期边界、目标祖先关系和里程碑实例键泄漏到多个模块。除非出现独立变化的规则族或明确的测试隔离收益，否则保持一个领域深模块更简单。

### `AttentionBackupCodec` 不应拆成读、校验、合并三个浅类

编解码、校验和合并共同拥有备份协议格式和稳定 ID 语义。按时间顺序拆分会复制协议知识。应只把面向 UI 的预览表示提取出来，并保持协议规则集中。

## 两个候选重构方案

### 方案 A：先收敛平台效果和备份预览（推荐）

1. 提取计时平台效果深接口，合并日程计时和自由计时的重复副作用。
2. 把备份计数与预览文案知识移回 backup 模块。
3. 保持 ViewModel、Room 和页面文件布局不变。
4. 用 seam 测试保护动作调用和备份预览行为。

**收益**：改动小，直接消除两个已确认的信息泄漏点，回归面集中在组合根和备份测试。

### 方案 B：重组能力型 ViewModel 和 Room 快照装配

1. 按投入、规划、备份建立能力型深接口。
2. 提取 Room 快照装配器，统一一次性读取与响应式读取。
3. 迁移页面 actions adapter，再删除跨能力的 ViewModel 入口。

**收益**：长期边界更清晰；代价是需要迁移大量测试和组合根代码，短期变化放大较大。

## 推荐实施顺序

1. **基线**：为计时启动副作用、备份预览计数和现有页面 actions 建立行为测试。
2. **消除重复**：先做方案 A 的计时 adapter 和备份预览归属迁移。
3. **再评估**：根据新增功能的变化路径，确认 `AttentionViewModel` 是否仍然成为跨能力热点。
4. **能力接口**：只有在第二个以上页面需要同一能力、且现有 ViewModel 继续变化放大时，才实施方案 B。
5. **Room 重组**：最后处理快照装配，避免把持久化拓扑、领域规则和 UI 迁移叠在同一个改动中。

## 验收标准

- 新增或修改计时启动入口时，只需要修改一个平台效果模块和对应 actions adapter。
- 新增备份记录类型时，记录计数与预览文案不需要在 `Workspace.kt` 重新维护一份类型清单。
- 页面仍然只依赖页面专属状态与动作接口，不接触 Room、DataStore、SAF、系统时钟或前台服务。
- `AttentionEngine` 和 `AttentionBackupCodec` 继续分别拥有领域规则和备份协议知识，不出现第二份规则实现。
- 每个重构切片都有行为测试，能够区分 UI seam、领域规则、持久化和平台副作用的回归。

## 结论

当前最值得做的不是把大文件平均切小，而是把已经重复出现的设计知识重新集中：计时平台效果归到一个深接口，备份预览归到备份模块。`AttentionEngine` 和 `AttentionBackupCodec` 虽然较大，但它们已经隐藏了高价值规则；在没有独立变化边界前拆分，反而会增加调用层和信息泄漏。
