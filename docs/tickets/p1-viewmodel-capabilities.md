## Parent

[#216](https://github.com/magician336/attention/issues/216)

## What to build

把 `AttentionViewModel` 从跨能力的宽门面迁移成三个窄而有深度的能力接口：`InvestmentActions` 管理实际投入、时间记录和计时器，`PlanningActions` 管理目标树、目标阶段、日程、重复日程、迁移和规划设置，`BackupActions` 管理 JSON/CSV 导出、导入预览、提交和最近导入备份恢复。页面继续通过各自的 `*ScreenState` 与 `*Actions` 发 intent；组合根可以用同一个 ViewModel 组合实现这些接口，但页面和 adapter 不再依赖完整 ViewModel。

这是一次 expand–migrate–contract 迁移：先增加兼容接口并保留旧入口，再按页面和能力迁移调用者，最后删除没有调用者的旧跨能力入口。共享的协程生命周期、错误映射、状态订阅、提交锁和反馈通道只保留一份。

## Acceptance criteria

- [ ] 三个能力接口覆盖现有页面所需的实际投入、规划和备份动作；接口只暴露调用者需要的领域参数和用户可观察结果。
- [ ] 日程页通过 `InvestmentActions` 完成计时、时间记录编辑/删除/撤销等动作；计时入口继续使用 #217 的统一平台效果路径。
- [ ] 目标树、统计、引导和规划设置通过 `PlanningActions` 完成目标阶段、目标迁移、日程规则和规划设置动作，既有领域校验与反馈保持不变。
- [ ] 设置页的数据导入导出、预览、提交和最近导入备份恢复通过 `BackupActions` 完成，并使用 #218 的备份预览摘要。
- [ ] 页面和页面 actions adapter 的类型签名不暴露完整 `AttentionViewModel`、仓储、Room、DataStore、SAF、权限 API 或系统时钟。
- [ ] 能力接口共享现有命令执行、错误映射、状态订阅和反馈通道，不复制三套协程或提交逻辑。
- [ ] 兼容入口在迁移期间只转发到唯一的能力/workflow 路径；所有调用者迁移完成后，旧的跨能力门面被删除，源代码边界测试能阻止回归。
- [ ] 时间记录、投入经验、规划日、目标树、日程、备份协议、状态指纹和恢复行为与迁移前一致；Progress Quest 游戏会话仍与 Attention 计时器独立。

## Blocked by

- [#217](https://github.com/magician336/attention/issues/217) 统一计时平台效果 seam
- [#218](https://github.com/magician336/attention/issues/218) 收拢备份预览摘要与计数
