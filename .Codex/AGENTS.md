# Attention 项目 AI 指南

## 项目概览
Attention 是 Android 原生时间投入与目标树应用。根项目使用 Gradle Kotlin DSL 和 Version Catalog，包含 `:app` Android 应用模块与 `:domain` 纯 Kotlin 领域模块。

## 技术栈
- Kotlin 2.4.20、JVM 17、Android Gradle Plugin 8.12.2、KSP 2.3.12
- Android API 36，minSdk 26；Jetpack Compose、Material 3、Navigation Compose
- Room 2.8.5 保存业务实体；Preferences DataStore 仅保存小型设置
- Kotlin Coroutines/Flow、kotlinx.serialization；JUnit、AndroidX instrumentation、Compose UI tests

## 架构边界
- 领域规则、状态变换和可确定性测试优先放在 `domain`，不得依赖 Android、Room 或 Compose。
- `app` 负责 UI、ViewModel、Room/DataStore 映射、通知、前台服务、AlarmManager 和文件选择器。
- Room 是运行时业务主存储；DataStore 只保存设置；JSON 只用于版本化备份与恢复。
- 父目标汇总、规划日、周期快照、目标迁移和奖励由领域层统一计算，适配层不得复制规则。

## 术语与文档
遵守根目录 `CONTEXT.md` 的中文领域术语和禁用词。架构决定见 `docs/adr/`，代理工作流见 `docs/agents/`。Issue 使用 `gh` CLI，标签和流程以 `docs/agents/issue-tracker.md` 为准。

## 验证
常规 JVM 检查：`./gradlew test`。Android 构建或 instrumentation 测试需使用已配置的 Android SDK/AVD；环境失败应与代码断言失败分开记录。

## CodeGraph
本项目已启用 CodeGraph，索引位于 `.codegraph/`。在理解代码、定位符号或编辑前，优先调用 `codegraph_explore`，并传入项目根路径；它返回当前源代码、调用路径和影响范围。索引数据库是本机生成物，由 `.codegraph/.gitignore` 排除，不提交到仓库。索引变化后运行 `codegraph update`（或重新 `codegraph init`）。

## 变更约束
先阅读相关 ADR、规格和现有测试；保持未提交工作；数据库 schema 变化要更新迁移与 schema fixture；新增领域规则必须有纯 Kotlin 测试。不要把历史快照语义或设置职责重新移回 DataStore。
