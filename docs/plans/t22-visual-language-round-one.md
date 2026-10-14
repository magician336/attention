# T22 第一轮开发计划：视觉语言、状态模型与记录来源

Issue：[确定 Attention 的 Material 3 视觉语言与状态模型](https://github.com/magician336/attention/issues/126)
上游地图：[UI/UX 优化路线图](https://github.com/magician336/attention/issues/125)

## 目标

把 #126 的决议落成可执行、可验收的三件套：

1. **领域数据层**——时间记录能区分日程确认、事后补记、计时产生三种来源，并保留与日程条目的关联。
2. **设计令牌**——暖纸色浅色主题与暖炭色深色主题，锈橙/蓝/森林绿/灰/红固定品牌语义色，不启用动态取色。
3. **共用状态与反馈组件**——加载、成功、错误、空数据、权限未开启、本地数据说明的统一表达，以及 Snackbar、内联错误、确认弹窗、禁用态和选中态的统一约定。

本轮只做最小接入：现有页面套用令牌与共用组件，**不重构信息架构**。#127 底部导航收敛、#128 工作台、#129 目标树、#130 记录流程的页面级重排留给后续轮次。

## 用户行为

1. 从某条日程完成入口提交时，创建一条来源为“日程确认”的实际投入，并携带该日程 ID；时间记录列表可反查它属于哪条日程。
2. 事后补记创建的实际投入来源为“事后补记”，可选择关联计划，但不强制绑定日程。
3. 从某条日程启动的计时器绑定该日程；停止后产生的实际投入来源为“计时产生”，同时携带日程 ID。从全局入口启动的计时器不绑定日程。
4. 时间记录编辑只调整时长、目标归属和备注，保留来源、关联日程和规划日。
5. 时间记录的关联日程被移除后，记录本身保留，读模型给出可解释的展示，而不是崩溃或静默丢失。日程删除入口本身属于 #130 轮次——当前代码只有 `addSchedule` / `updateSchedule` / `toggleScheduleCompleted`，没有删除——本轮只保证数据层与读模型对悬挂关联安全。
6. 只有计时产生的时间记录声称精确的实际开始/结束时间；日程确认的记录显示“按计划记录”，不伪装成精确实际时间。
7. 暖纸色/暖炭色主题在系统浅色、深色下均生效，品牌色不随壁纸变化；状态同时由颜色和图标或文字表达。
8. 计时和时长统一使用自然语言：`5 分钟` 显示为 `1 小时 15 分钟`。
9. 加载中、错误、空数据、权限未开启、本地数据说明在今天、目标树、所有日期、统计、设置之间使用同一套组件表达。

## 领域模型

```text
enum class TimeEntrySource { MANUAL, TIMER, SCHEDULE_CONFIRMED, IMPORT }
  ├─ MANUAL            = 事后补记
  ├─ TIMER             = 计时产生，唯一声称精确实际开始/结束时间
  ├─ SCHEDULE_CONFIRMED= 日程确认，occurredAtEpochMillis 为 null，标记“按计划记录”
  └─ IMPORT            = 备份导入，保持既有语义

TimeEntry(id, planningDate, durationMinutes, targetId?, source,
          occurredAtEpochMillis?, note, scheduleEntryId?)
  └─ scheduleEntryId != null -> 该记录由某条日程条目产生

ActiveTimer(..., scheduleEntryId?)
  └─ scheduleEntryId != null -> 计时绑定日程；null -> 仅绑定目标或未归属活动
```

约束：

- 时长必须大于 0；`source` 决定 `occurredAtEpochMillis` 的可空性与“按计划记录”标记，两者不得互相矛盾。
- `scheduleEntryId` 非空时必须指向存在的日程条目；日程条目被删除后允许悬挂，但读模型必须显式表达“所属日程已删除”，不得抛错。
- 编辑时间记录保留 `source`、`scheduleEntryId`、`planningDate`、`occurredAtEpochMillis`。
- 三种来源不重复计算投入经验：同一笔时间记录只产生一次经验，与来源无关。
- 统计口径不变：实际投入只来自时间记录，来源字段不改变聚合结果。

### 数据层变更

- `time_entries` 新增 `scheduleEntryId TEXT`（可空），`active_timer` 新增同名字段。
- 数据库版本 4 → 5，`MIGRATION_4_5` 为纯增量 `ALTER TABLE ... ADD COLUMN`，不重写既有行，不使用破坏性回退。
- 既有行的 `scheduleEntryId` 为 null、`source` 保持原值；不追溯推断历史记录属于哪条日程。
- `TimeEntry` 新字段带默认值，备份 JSON 向前向后兼容；导入校验增加“关联日程存在性”检查。
- `Exporters.kt` 的 CSV 现有列不变，`source` 列直接输出 `SCHEDULE_CONFIRMED` 新值；不新增列以免破坏既有导出消费方。

### 设计令牌

```text
浅色 暖纸色 surface / 深色 暖炭色 surface，固定品牌色，无动态取色
锈橙 = 正在计时        蓝 = 已安排
森林绿 = 已完成        灰 = 未归属        红 = 错误
```

- 状态不得只依赖颜色，必须同时有图标或文字。
- 令牌集中在一个主题文件，提供 spacing / radius / typography 三档标度；页面不得再写裸色值。
- 对比度、触控区域、动态字体和语义标签满足 Material 3 基本要求，作为 instrumentation 断言对象。

## 实现切片

| 切片 | 内容 | 归属 |
| --- | --- | --- |
| T22-A | `TimeEntrySource` 增加 `SCHEDULE_CONFIRMED`；`TimeEntry` 与 `ActiveTimer` 增加 `scheduleEntryId`；`MIGRATION_4_5`；mapper、备份校验、CSV 导出兼容；领域测试覆盖三种来源、悬挂日程与编辑保真 | `:domain` + `:app` 数据层 |
| T22-B | 新建主题文件：暖纸色/暖炭色配色、品牌语义色、spacing/radius/typography 标度 | `:app` `ui/theme` |
| T22-C | 共用状态组件（加载/成功/错误/空数据/权限未开启/本地数据说明）、Snackbar 宿主、内联错误、确认弹窗、禁用态与选中态 | `:app` `ui/components` |
| T22-D | 自然语言时长格式化与“按计划记录”标记；计划时间与实际时间分离展示的复用件 | `:app` `ui/format` |
| T22-E | 现有页面最小接入 T22-B/C/D；Compose UI 测试与 API 35 instrumentation 验收 | `:app` UI |

## 验收矩阵

| 场景 | 证据 | 结果 |
| --- | --- | --- |
| 日程确认记录 | 领域测试 | source 为 `SCHEDULE_CONFIRMED`，`scheduleEntryId` 指向该日程，`occurredAtEpochMillis` 为 null |
| 事后补记记录 | 领域测试 | source 为 `MANUAL`，可无关联日程 |
| 日程计时停止 | 领域测试 | source 为 `TIMER`，携带日程 ID，含精确时间戳 |
| 全局计时停止 | 领域测试 | source 为 `TIMER`，`scheduleEntryId` 为 null |
| 编辑时间记录 | 领域测试 | 来源、关联日程、规划日、精确时间戳均保持 |
| 关联日程被移除 | 领域测试 + Room 测试 | 时间记录保留，读模型显示“所属日程已删除”；本轮不实现日程删除入口 |
| v4 → v5 迁移 | Room instrumentation | 历史行完整，`scheduleEntryId` 为 null，统计与投入经验不变 |
| 备份导出导入往返 | 备份测试 | 三种来源与关联往返一致；旧备份可导入 |
| 投入经验 | 领域测试 | 三种来源各只计一次，父子汇总不重复 |
| 令牌落地 | Compose UI 测试 | 品牌语义色来自主题令牌，页面无裸色值 |
| 状态一致性 | Compose UI 测试 | 同一状态在五个页面使用同一组件与同一 testTag |
| 非仅颜色 | Compose UI 测试 | 每种状态都有可断言的图标或文字 |
| 自然语言时长 | 单元测试 | `75` → `1 小时 15 分钟`，`45` → `45 分钟` |
| 深浅主题 | Compose UI 测试 | 浅色/深色两套品牌色均生效且不随壁纸变化 |
| 无障碍 | instrumentation | 触控区域、对比度、语义标签达标 |

## 非目标

- 不重构主导航与信息架构（#127）、不实现工作台页面重排（#128）、不改目标树（#129）与所有日期流程（#130）。
- 不实现 `#128` 的自动完成规则（实际投入达到计划时长自动勾选完成），本轮只保证来源与关联正确。
- 不重建 `app/src/main/prototype/attention_ui_prototype.html`；第一轮证据是 Compose 组件与 instrumentation 用例。
- 不拆分 `Workspace.kt`，不改备份文件命名与导出格式，不新增云同步或账号能力。
- 不启用动态取色，不改变业务计算规则与统计口径。

## 后续

1. 本计划确认后进入 to-spec，产出可验收规格。
2. 按 T22-A…T22-E 开 `ready-for-agent` issue，逐片实现。
3. 全部切片合入后回到 #125 地图，继续 #131 统计/设置分层与 #132 全局反馈、可访问性验收基线。