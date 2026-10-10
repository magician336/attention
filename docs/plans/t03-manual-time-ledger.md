# T03 手动时间账本开发设计

Issue：[T03 手动时间账本](https://github.com/magician336/attention/issues/4)

## 目标

让用户在任意未归档目标或未归属活动入口记录实际投入，并能修正、删除和批量整理记录；目标树页面同时展示直接投入与子树汇总。

## 用户行为

1. 在选定规划日输入正整数分钟，可选一个未归档目标；不选目标时产生未归属活动。
2. 在该规划日的时间记录列表编辑分钟、目标和备注。规划日、来源、发生时间和记录 ID 保持不变。
3. 删除记录后，目标进度、周期统计和基础投入经验按剩余记录重算。
4. 未归属活动列表支持多选、全选和选择目标后一次归入；任何失效选择都使整批失败。
5. 目标树显示“直接投入 / 子树汇总”；父目标汇总不复制记录，也不重复产生基础经验。

## 领域模型

```text
TimeEntry(id, planningDate, durationMinutes, targetId?, source, occurredAt, note)
  ├─ targetId = null -> 未归属活动
  └─ targetId = active Target -> 目标投入

directMinutes(target) = sum(entry.duration where entry.targetId == target.id)
subtreeMinutes(target) = sum(entry.duration where entry.targetId in target + descendants)
```

约束：时长必须大于 0；目标必须存在且未归档；编辑不移动规划日；批量归入只接受存在且当前未归属的全部 ID。

## 实现切片

- `:domain`：收口新增、编辑、删除和批量归入校验；补编辑保留事实字段、归档目标拒绝和批量原子性的测试。
- `AttentionViewModel`：保持命令统一通过 Room 业务事务，新增/编辑/删除/归入后重算基础经验。
- Compose：当前规划日显示记录；未归属活动提供多选、全选和目标选择；归档目标不出现在选择器中。
- Room：复用 `TimeEntryEntity`、DAO 和 `RoomBusinessDataRepository` 的事务写入，不在 DAO 层复制领域汇总规则。

## 验收矩阵

| 场景 | 结果 |
| --- | --- |
| 新增到目标 | 记录绑定一个目标，直接投入和子树汇总更新 |
| 新增未归属 | 记录出现在未归属入口，不推进目标经验 |
| 编辑 | 只改分钟/目标/备注，规划日与来源保持 |
| 删除 | 记录移除，进度、统计、基础经验重算 |
| 多选归入 | 全量有效才提交；失败不改变任何记录 |
| 归档目标 | 历史保留，新记录和新归入被拒绝 |
| Room 重启 | 记录、归属和汇总读模型保持 |

## 非目标

- 不在 T03 中编辑规划日、来源或发生时间。
- 不在 T03 中实现里程碑撤销/重放、跨日期纠错迁移或新的计时器协议。
- 不把父目标汇总物化为重复时间记录。

