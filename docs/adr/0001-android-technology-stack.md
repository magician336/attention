---
status: accepted
date: 2026-10-08
---

# Attention v0.1.0 Android 技术栈

Attention v0.1.0 采用原生 Android 技术栈：Kotlin、Jetpack Compose、Room、Preferences DataStore、Coroutines/Flow 和 Android 系统适配器。核心领域规则放在不依赖 Android 的纯 Kotlin `:domain` 模块中，Android UI、Room、通知、前台服务、AlarmManager 和文件选择器作为适配层；这样可以把目标树、周期快照、目标迁移、时间记录、规划日切分和经验里程碑放在一个可确定性测试的 seam 后面。

## 选择

- Kotlin 2.4.20、Android Gradle Plugin 8.12.2、Gradle 8.13 和 KSP 2.3.12，使用 Gradle Kotlin DSL 与 Version Catalog；Room compiler 通过 KSP 处理 Kotlin 源码。
- `compileSdk`/`targetSdk` 使用 Android 16 API 36，`minSdk` 使用 26。
- UI 使用 Jetpack Compose、Material 3、Navigation Compose；页面状态使用 ViewModel、Coroutines 和 StateFlow。
- 数据库使用 Room 稳定线（当前构建锁定 2.8.5），保存目标树、目标阶段、周期快照、目标迁移、时间记录、日程、重复规则、提醒、活动计时、经验和里程碑。
- Preferences DataStore 只保存规划日界线、周起始日、每日容量、首屏和通知偏好等小型设置。
- JSON 备份使用 kotlinx.serialization 和版本化备份 DTO；CSV 只作为分析导出格式。
- 全局计时器使用持久化活动计时状态和前台服务通知；日程提醒使用 AlarmManager，延迟维护工作使用 WorkManager。
- 测试分为纯 Kotlin 领域测试、Room 数据库迁移测试、AndroidX 系统适配测试和 Compose 用户路径测试。

## 为什么这样选

产品当前只面向 Android，且 v0.1.0 依赖锁屏计时、通知栏控制、进程恢复、开机后的恢复选择、本地提醒和 Storage Access Framework。原生 Kotlin 可以直接使用这些系统能力，也避免跨平台插件成为时间记录正确性的隐含依赖。

Room 适合目标树、历史周期和时间记录之间的引用关系；DataStore 只负责少量设置。JSON 是可迁移的备份协议，不作为运行时主数据库，从而避免用整份文档重写来处理单条时间记录或单个目标节点。

纯 Kotlin `:domain` 模块是主要测试 seam。它接收领域命令和时钟，输出新状态及领域结果；UI、数据库和系统服务不负责计算父目标汇总、规划日、周期快照、迁移或奖励。这样同一条规则可以由手动追加、计时器、导入和恢复路径共同使用。

## 考虑过的方案

- Flutter 或 React Native：可以减少未来多平台 UI 的重复工作，但计时器前台服务、通知动作、开机恢复和本地提醒仍需原生桥接；当前 Android-only 范围无法抵消这层复杂度。
- Kotlin Multiplatform：保留纯 Kotlin 领域模块为未来迁移点，但 v0.1.0 不提前引入跨平台构建和存储矩阵。
- XML Views：已有生态成熟，但新项目需要大量状态同步代码；Compose 更适合目标树、日期菜单、计时状态和统计筛选的声明式更新。
- JSON 文件作为主存储：结构简单，但难以安全处理历史快照、目标移动、单条记录编辑和批量删除；Room 更适合这些查询和事务。

## 需要先验证的边界

Android 14 及以上要求前台服务声明具体类型。持续计时没有合适的媒体或数据同步语义，初步采用 `specialUse`，并在 T13 中用目标 SDK 36、锁屏、进程回收、重启和通知操作做最小系统验证；清单中的用途说明还需要符合发行渠道的审核要求。计时器必须由可见 UI 启动，避免触发后台启动限制。

日程提醒默认使用非精确闹钟。只有用户明确要求精确时才申请精确闹钟访问，并在权限被拒绝或撤销时保留核心日程功能。

## 关联

- 产品规格：[Attention v0.1.0](../specs/attention-v0.1.0.md)
- GitHub 规格 Issue：[#1](https://github.com/magician336/attention/issues/1)
- 相关任务：T01 Android 应用与本地设置、T12 应用内计时器、T13 后台计时恢复、T19 JSON 备份与恢复

## 参考

- [Jetpack Compose](https://developer.android.com/compose)
- [Android 16 SDK setup](https://developer.android.com/about/versions/16/setup-sdk)
- [Room release notes](https://developer.android.com/jetpack/androidx/releases/room)
- [DataStore](https://developer.android.com/topic/libraries/architecture/datastore)
- [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)
